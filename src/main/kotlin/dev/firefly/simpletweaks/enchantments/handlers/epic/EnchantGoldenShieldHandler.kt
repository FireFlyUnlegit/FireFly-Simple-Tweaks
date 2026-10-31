package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import dev.firefly.simpletweaks.util.removeAbsorptionHeadroom
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * **Golden Shield**（金盾） — slowly builds an absorption shield while the armour is worn.
 *
 * ## Behaviour
 * | quantity | value |
 * |---|---|
 * | charge rate | `totalArmorLevel / 4` points per second (one charge every 20 ticks) |
 * | cap on **this** enchantment's contribution | `totalArmorLevel` points (level summed over the four armour slots) |
 * | foreign absorption | preserved — the grant is additive and is only ever topped up to the cap |
 * | on removing the armour | the tracking state is dropped and this enchantment's own `max_absorption` modifier is removed, which lets the vanilla clamp trim whatever was left of *its* shield |
 *
 * ## 🔴 Why the first version of this handler did nothing (fixed here, `build=cleanup21`)
 *
 * The original body was:
 *
 * ```kotlin
 * player.absorptionAmount += toAdd
 * contribution[id] = mine + toAdd
 * ```
 *
 * In 1.12.2 that is the whole story, and it was ported as if 1.21 still worked that way. It does
 * not. Three bytecode-verified facts about 1.21.1:
 *
 * 1. `LivingEntity#setAbsorptionAmount(float)` is `public final` and its first act is
 *    `MathHelper.clamp(f, 0.0f, getMaxAbsorption())` before delegating to
 *    `setAbsorptionAmountUnclamped`.
 * 2. `getMaxAbsorption()` is `getAttributeValue(EntityAttributes.GENERIC_MAX_ABSORPTION)`, and that
 *    attribute is registered as `ClampedEntityAttribute("generic.max_absorption", 0.0, 0.0, 2048.0)` —
 *    **default 0**. `PlayerEntity` never touches it. The only thing in vanilla that raises it is the
 *    `absorption` status effect, which carries `addAttributeModifier(GENERIC_MAX_ABSORPTION, "effect.absorption", 4.0, ADD_VALUE)`.
 * 3. `PlayerEntity` overrides `getAbsorptionAmount` / `setAbsorptionAmountUnclamped` to go through the
 *    tracked value `PlayerEntity.ABSORPTION_AMOUNT`, so a player's absorption *is* synced to the
 *    client — but only whatever survives the clamp in step 1.
 *
 * Consequence: with no absorption effect and no modifier, `max_absorption` is `0`, so every write was
 * clamped to `0`. The enchantment granted nothing, on every attempt, forever — while `contribution`
 * still advanced, so after a few seconds the handler also believed it was at cap. Nothing was logged,
 * so "it does nothing" and "it is not equipped" were indistinguishable. That is the exact failure
 * shape `AGENTS.md` §4.4 exists to prevent.
 *
 * ## The fix
 * The handler now attaches its **own temporary `generic.max_absorption` modifier** (`ADD_VALUE`,
 * value = the cap) before writing, so the clamp admits the grant, and it records the *observed* delta
 * (`after - before`) rather than the requested one, so a clamp can never silently desync the
 * bookkeeping again. `ADD_VALUE` also means a vanilla absorption effect stacks on top instead of
 * being overwritten, which is what keeps the "foreign absorption is preserved" promise.
 *
 * The modifier is deliberately **temporary**, not persistent: its removal deadline lives in memory
 * (this file's maps), and a persistent modifier whose remover can be lost is the bug that once left
 * victims of `heavenly_punishment` permanently unable to attack. Temporary means a lost deadline
 * self-heals on reload.
 *
 * Both halves now live in [dev.firefly.simpletweaks.util.grantAbsorption] /
 * [dev.firefly.simpletweaks.util.removeAbsorptionHeadroom], which is the shared primitive the
 * `DEVLOG` prescribed when it fixed this file and flagged `healing_blade` / `death_protection` as the
 * same bug. This file's behaviour is unchanged by that extraction: same modifier id, same value
 * (our own cap — note this is the *weaker* headroom choice, see AbsorptionUtil's caveat), same
 * temporary flag, same early-return when the modifier already covers the cap.
 */
@ModEnchantment(
    id = "golden_shield",
    category = EnchantCategory.EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 4,
    minCostBase = 30,
    minCostPerLevel = 12,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object EnchantGoldenShieldHandler : Listenable {

    /** One charge every this many ticks. */
    private const val CHARGE_INTERVAL = 20

    /**
     * `generic.max_absorption` modifier id.
     *
     * One fixed id per player: the modifier is removed and re-added whenever the cap changes, so the
     * instance never accumulates duplicates. It is **per source** (`AbsorptionUtil`'s KDoc explains
     * why), and the id string is unchanged from the version that introduced this fix
     * (`simple_tweaks:golden_shield_max_absorption`).
     */
    private val MAX_ABSORPTION_ID: Identifier = absorptionHeadroomId("golden_shield")

    private val contribution = mutableMapOf<UUID, Float>()
    private val tickCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val player = e.player
        if (player.isRemoved) return

        val id = player.uuid

        // Absorption consumed by damage: our share shrinks with it. Without this the handler would
        // believe it still holds points that have already been spent, and would never refill.
        val current = player.absorptionAmount
        val tracked = contribution[id] ?: 0f
        if (current < tracked) {
            contribution[id] = current
        }

        val lvl = player.getArmorEnchantLevel(GeneratedEnchantments.GOLDEN_SHIELD)
        if (lvl <= 0) {
            clear(player)
            return
        }

        val counter = (tickCounter[id] ?: 0) + 1
        if (counter < CHARGE_INTERVAL) {
            tickCounter[id] = counter
            return
        }
        tickCounter[id] = 0

        val maxMine = lvl.toFloat() * 4f
        val perCharge = lvl / 4f
        val mine = contribution[id] ?: 0f
        if (mine >= maxMine) {
            FSTLog.log("GoldenShield") {
                "player=${player.name.string}, lvl=$lvl, mine=$mine, cap=$maxMine, " +
                    "absorption=${player.absorptionAmount}, outcome=at-cap"
            }
            return
        }

        val requested = perCharge.coerceAtMost(maxMine - mine)
        // Landing the grant needs the `max_absorption` ceiling raised first, and the bookkeeping needs
        // the *landed* delta rather than the requested one — both live in AbsorptionUtil now, so this
        // file no longer carries its own copy of §4.9. The headroom is our own cap (unchanged from the
        // fix that introduced it): a foreign share is not covered by this modifier, which is a known
        // limitation of this enchantment, see `healing_blade` for the stricter form.
        val applied = grantAbsorption(player, MAX_ABSORPTION_ID, requested, maxMine)

        contribution[id] = mine + applied
        FSTLog.log("GoldenShield") {
            "player=${player.name.string}, lvl=$lvl, requested=$requested, applied=$applied, " +
                "mine=${contribution[id]}/$maxMine, absorption=${player.absorptionAmount}, " +
                "maxAbsorption=${player.maxAbsorption}, " +
                "outcome=${if (applied < requested) "clamped" else "granted"}"
        }
    }

    /**
     * Drops all state for [player] and removes this enchantment's `max_absorption` modifier.
     *
     * Removing the modifier is what retires the shield: the attribute change runs
     * `LivingEntity#onAttributeChanged`, which clamps the absorption down to the new maximum — so the
     * points *this* enchantment granted are trimmed, while anything a vanilla absorption effect is
     * still entitled to is not.
     */
    private fun clear(player: PlayerEntity) {
        val id = player.uuid
        val hadState = contribution.remove(id) != null
        tickCounter.remove(id)
        val hadModifier = removeAbsorptionHeadroom(player, MAX_ABSORPTION_ID)
        if (hadState || hadModifier) {
            FSTLog.log("GoldenShield") {
                "player=${player.name.string}, absorption=${player.absorptionAmount}, " +
                    "modifierRemoved=$hadModifier, outcome=cleared"
            }
        }
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        clear(e.original)
        clear(e.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        clear(e.player)
    }
}
