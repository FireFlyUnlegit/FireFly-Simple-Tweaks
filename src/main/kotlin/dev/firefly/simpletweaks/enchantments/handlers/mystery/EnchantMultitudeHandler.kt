package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * 众生 — a reaction you trigger is sometimes **replicated** onto every nearby enemy.
 *
 * ## How it differs from `elemental_spread` (RARE)
 * | | `elemental_spread` | `multitude` |
 * |---|---|---|
 * | what is copied | the **aura only** (`0.5 × level` element units) | the **whole reaction**: the trigger element **and** the reaction's own flat damage |
 * | who gets it | everything in `2 + 0.5 × level` blocks | everything in `6 + 2 × level` blocks |
 * | chance | always | `15 % + 10 % × level` |
 *
 * The distinction is what makes the two tiers different rather than one being a bigger number:
 * spreading an aura is setup for *your next* hit, while replicating the reaction resolves damage on the
 * whole pack **now**.
 *
 * ## Why the numbers come from `ReactionOutcome` instead of being re-derived
 * `ReactionSeam` carries the resolved outcome (`build=cleanup77` added that parameter). Re-deriving it
 * here would need a second copy of `ElementMarks.transformativeDamage`'s formula plus mastery /
 * resonance / sovereignty multipliers — i.e. exactly the drift this project keeps getting bitten by.
 * Copying the number means the replicated reaction is **the same reaction**, by construction.
 *
 * ## Safety
 * - Only the attacker's hand is read, and only on a server world.
 * - The copied damage is queued through `ElementCombat.enqueueElemental`, so it carries the auxiliary
 *   flag and cannot re-enter the handler list (a replicated reaction must not trigger `multitude` again
 *   on the next enemy, which would be a chain reaction of chain reactions).
 * - `others` excludes the original target and the attacker, so a player cannot self-nuke.
 */
@ModEnchantment(
    id = "multitude",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 2,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 18,
    minCostBase = 50,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantMultitudeHandler : Listenable {

    /** Reaction chance, before the per-level part. II = 35 %. */
    const val BASE_CHANCE = 0.15f
    const val CHANCE_PER_LEVEL = 0.10f

    /** Radius the replicated reaction reaches: `6 + 2 × level` blocks. */
    const val BASE_RADIUS = 6.0
    const val RADIUS_PER_LEVEL = 2.0

    /**
     * Element units attached to each replicated target.
     *
     * `ULTRA` (not the trigger's own units): a replicated reaction is a *rain of that element*, and a
     * weak copy would be eaten by any aura the other enemy happens to carry.
     */
    private const val REPLICATED_UNITS = ElementAuras.ULTRA

    override fun init() {
        ReactionSeam.register { attacker, target, element, outcome ->
            replicate(attacker, target, element, outcome.flatDamage)
        }
    }

    private fun replicate(attacker: LivingEntity?, target: LivingEntity, element: dev.firefly.simpletweaks.util.elements.ElementType, flatDamage: Float) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return

        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.MULTITUDE)
        if (level <= 0) return

        val chance = (BASE_CHANCE + CHANCE_PER_LEVEL * level).coerceAtMost(1f)
        if (target.world.random.nextFloat() >= chance) return

        val radius = BASE_RADIUS + RADIUS_PER_LEVEL * level
        val box = target.boundingBox.expand(radius)
        var copied = 0

        for (other in target.world.getEntitiesByClass(LivingEntity::class.java, box) {
            it !== target && it !== player && it.isAlive
        }) {
            ElementAuras.attach(other, element, REPLICATED_UNITS)
            if (flatDamage > 0f) {
                ElementCombat.enqueueElemental(player, other, element, flatDamage)
            }
            copied++
        }

        FSTLog.log("Multitude") {
            "attacker=${player.name.string}, source=${target.type.name.string}, element=${element.id}, " +
                "flat=$flatDamage, chance=$chance, radius=${"%.1f".format(radius)}, " +
                "targets=$copied, outcome=${if (copied > 0) "replicated" else "no-targets"}"
        }
    }
}
