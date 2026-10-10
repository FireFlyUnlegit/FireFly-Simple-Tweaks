package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.cancel
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.LivingEvent
import dev.firefly.simpletweaks.compat.event.LivingHealEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageBypassingArmor
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import java.util.UUID
import java.util.WeakHashMap
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantEchoShieldHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                        | 1.21.1                                                                 |
 * |---------------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent` / `LivingDamageEvent` / `LivingHealEvent` / `LivingDeathEvent` | `compat.event.*` (same names)      |
 * | `net.minecraftforge...LivingEvent.LivingUpdateEvent`          | `compat.event.LivingEvent.LivingUpdateEvent`                            |
 * | `net.minecraftforge...entity.player.PlayerEvent.Clone`        | `compat.event.PlayerEvent.Clone`                                        |
 * | `net.minecraftforge...gameevent.PlayerEvent.PlayerLoggedOutEvent` | `compat.event.PlayerEvent.PlayerLoggedOutEvent`                     |
 * | `net.minecraftforge...EventPriority`                          | `compat.event.EventPriority`                                            |
 * | `e.attacker` / `e.target` / `e.invalid` / `e.cancel()`        | same extension names, now `compat.*`                                    |
 * | `EntityLivingBase` / `EntityPlayer`                           | `net.minecraft.entity.LivingEntity` / `net.minecraft.entity.player.PlayerEntity` |
 * | `player.getArmorEnchantLevel(EnchantEchoShield, 20)`          | `player.getArmorEnchantLevel(GeneratedEnchantments.ECHO_SHIELD, 20)`        |
 * | `player.uniqueID`                                             | `player.uuid` (`EntityLike.getUuid`, method_5667)                       |
 * | `attacker.maxHealth`                                          | unchanged (`method_6063`)                                              |
 * | `e.isWasDeath`                                                | `e.wasDeath`                                                            |
 * | `entity.world.isRemote`                                       | `WorldSide.isClient(entity.world)` (field_9236 is a FIELD)              |
 * | `world.totalWorldTime`                                        | `world.time` (`World.getTime`, method_8510)                             |
 * | `world as? WorldServer`                                       | `world as? ServerWorld`                                                 |
 * | `soundType` `SoundEvents.ENTITY_LIGHTNING_THUNDER`            | `SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER` (1.13 renamed the constant) |
 * | `EnumParticleTypes.EXPLOSION_LARGE`                           | `ParticleTypes.EXPLOSION` (1.13 renamed `largeexplode`)                  |
 * | `EnumParticleTypes.CRIT_MAGIC`                                | `ParticleTypes.ENCHANTED_HIT` (1.13 renamed `magicCrit`)                 |
 * | `EnumParticleTypes.ENCHANTMENT_TABLE`                         | `ParticleTypes.ENCHANT` (1.13 renamed `enchantmenttable`)                |
 * | `EnchantEchoShield` (Enchantment object)                      | `GeneratedEnchantments.ECHO_SHIELD` (RegistryKey)                          |
 *
 * ⚠️ Two forced mappings:
 *
 * 1. **The `st_echo_no_heal_until` per-entity NBT tag becomes a `WeakHashMap<LivingEntity, Long>`.**
 *    1.12.2 kept the "no heal until world tick N" deadline in Forge's per-entity persistent NBT
 *    (`Entity.getEntityData()`), which 1.21 has no equivalent for on an arbitrary entity. A
 *    weak-keyed map is the closest structural analogue: the value is attached to the entity's
 *    identity, disappears with it exactly like the NBT did, and needs no extra cleanup. The stored
 *    value is a `world.time`, so nothing is lost by it not surviving a reload (a persisted value
 *    would be stale anyway). The 1:1 control flow of `onHeal` / `onLivingUpdate` / `markNoHeal` is
 *    otherwise unchanged.
 *
 * 2. **Reflection bypasses armour — FIXED via a custom damage type.** Both reflection calls need
 *    `ignoreArmorAndPotion = true` (1.12.2 reflected the reduced damage "raw"). The shared
 *    `util/PlayerUtils.runPlayerAttack` cannot honour that flag in 1.21 (no
 *    `applyPotionDamageCalculations`), so these two call sites deliberately do **not** use it.
 *    They call `util/damagesource/ModDamageTypes.damageBypassingArmor` instead, which deals damage
 *    through the mod's own `simple_tweaks:reflection` damage type — registered in the vanilla
 *    `#minecraft:bypasses_armor`, `#minecraft:bypasses_resistance` and
 *    `#minecraft:bypasses_enchantments` tags, which together reproduce 1.12.2's armour + potion +
 *    protection skipping without touching the shared helper other batches depend on.
 *
 * Everything else — the NaN/Inf bail-outs (and their `println` diagnostics), the `lvl`-tick heal
 * lockout, the `0.03f * lvl` reduction, the buffer fill/burst state machine and the level-20 armour
 * cap — is carried over verbatim.
 *
 * 3. **Reflected damage is never reflected again, and never fully negated** — a deliberate departure
 *    from 1.12.2, needed to stop two shields from bouncing the same hit back and forth. Such damage
 *    still receives the plain `0.03 x level` mitigation; what is denied is bouncing it back, storing
 *    it, and cancelling it outright. See [isReflection] for the bytecode evidence and the two
 *    recursion paths this closes.
 */
@ModEnchantment(
    id = "echo_shield",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = 1,
    anvilCost = 12,
    minCostBase = 37,
    minCostPerLevel = 12,
    supportedItems = "#minecraft:enchantable/armor",
    slots = [EnchantSlot.ARMOR],
    order = 41,
)
object EnchantEchoShieldHandler : Listenable {

    private val echoBuffer = mutableMapOf<UUID, Float>()

    // 1.12.2 stored this deadline in Forge's per-entity persistent NBT under
    // "st_echo_no_heal_until"; see the class KDoc for why it is a weak-keyed map here.
    private val noHealUntil = WeakHashMap<LivingEntity, Long>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        // Reflection damage must never reach the buffer-burst branch below: a shield cannot counter its
        // own counter, and being *fully* negated is precisely what is denied. The plain `ratio`
        // mitigation still applies to it -- that happens in `onDamage`. See `isReflection`.
        if (isReflection(e.source)) return
        val player = e.target as? PlayerEntity ?: return
        val attacker = e.attacker ?: return
        if (attacker === player) return

        if (e.amount.isNaN() || e.amount.isInfinite()) {
            println("[FST-NaN] EchoShield.onHurt: incoming amount is NaN/Inf, skip. amount=${e.amount}")
            return
        }

        val lvl = player.getArmorEnchantLevel(GeneratedEnchantments.ECHO_SHIELD, 20)
        if (lvl <= 0) return

        markNoHeal(attacker, lvl)

        val id = player.uuid
        val buffer = echoBuffer[id] ?: 0f

        if (buffer.isNaN() || buffer.isInfinite()) {
            println("[FST-NaN] EchoShield.onHurt: buffer is NaN/Inf, clearing. buffer=$buffer")
            echoBuffer.remove(id)
            return
        }
        if (buffer <= 0f) return

        if (buffer >= e.amount) {
            e.cancel()

            // Spend the buffer BEFORE dealing the reflection. `damageBypassingArmor` is a synchronous
            // nested damage event that re-enters this handler for the attacker, so leaving the buffer
            // live here would let the same amount bounce straight back -- and because this branch
            // reflects the *whole* buffer (not a decaying fraction), two equal buffers would then
            // recurse forever. Clearing first makes the outgoing reflection the last word.
            echoBuffer.remove(id)

            attacker.damageBypassingArmor(player, buffer.coerceAtMost(attacker.maxHealth))

            playFeedback(player, big = true)
            STLog.log("EchoShield") {
                "player=${player.name.string}, attacker=${attacker.name.string}, lvl=$lvl, buffer=$buffer, " +
                    "incomingDamage=${e.amount}, reflected=${buffer.coerceAtMost(attacker.maxHealth)}, " +
                    "outcome=absorbed-and-reflected"
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        // Not skipped for reflection damage -- it is downgraded. See the branch below.
        val reflected = isReflection(e.source)
        val player = e.target as? PlayerEntity ?: return
        val attacker = e.attacker ?: return
        if (attacker === player) return

        if (e.amount.isNaN() || e.amount.isInfinite()) {
            println("[FST-NaN] EchoShield.onDamage: amount is NaN/Inf, skip. amount=${e.amount}")
            return
        }

        val lvl = player.getArmorEnchantLevel(GeneratedEnchantments.ECHO_SHIELD, 20)
        if (lvl <= 0) return

        val id = player.uuid
        val ratio = 0.03f * lvl
        val reduced = e.amount * ratio

        if (reduced.isNaN() || reduced.isInfinite() || reduced <= 0f) return

        if (reflected) {
            // A reflection still gets the shield's plain mitigation -- soaking is the point of wearing
            // it -- but the chain is cut at everything else:
            //  - nothing is bounced back, so two shields cannot trade the same hit;
            //  - nothing is stored, so no value derived from a reflection can be bounced later either;
            //  - `onHurt` returns early for this damage, so it can never be *fully* negated.
            // `markNoHeal` is skipped too: it is the shield's counter-attack, and a reflection is not
            // an attack worth countering.
            e.amount -= reduced
            STLog.log("EchoShield") {
                "player=${player.name.string}, attacker=${attacker.name.string}, lvl=$lvl, " +
                    "incomingDamage=${e.amount + reduced}, ratio=$ratio, reduced=$reduced, " +
                    "outcome=soaked-reflection"
            }
            return
        }

        markNoHeal(attacker, lvl)

        attacker.damageBypassingArmor(player, reduced)
        e.amount -= reduced

        val newBuffer = (echoBuffer[id] ?: 0f) + reduced
        if (!newBuffer.isNaN() && !newBuffer.isInfinite()) {
            echoBuffer[id] = newBuffer
        } else {
            println("[FST-NaN] EchoShield.onDamage: buffer overflow to NaN/Inf, reset to 0")
            echoBuffer.remove(id)
        }

        playFeedback(player, big = false)
        STLog.log("EchoShield") {
            "player=${player.name.string}, attacker=${attacker.name.string}, lvl=$lvl, incomingDamage=${e.amount + reduced}, " +
                "ratio=$ratio, reduced=$reduced, buffer=${echoBuffer[id]}, outcome=buffered"
        }
    }

    @SubscribeEvent
    fun onHeal(e: LivingHealEvent) {
        val entity = e.entityLiving ?: return
        if (WorldSide.isClient(entity.world)) return
        val until = noHealUntil[entity] ?: 0L
        if (until > 0L && entity.world.time < until) {
            e.isCanceled = true
            STLog.log("EchoShield") {
                "entity=${entity.name.string}, until=$until, now=${entity.world.time}, " +
                    "amount=${e.amount}, outcome=heal-blocked"
            }
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(e: LivingEvent.LivingUpdateEvent) {
        val entity = e.entityLiving ?: return
        if (WorldSide.isClient(entity.world)) return
        val until = noHealUntil[entity] ?: 0L
        if (until <= 0L) return
        if (entity.world.time > until) {
            noHealUntil.remove(entity)
        }
    }

    private fun markNoHeal(entity: LivingEntity, lvl: Int) {
        val now = entity.world.time
        val until = now + lvl
        val prev = noHealUntil[entity] ?: 0L
        if (until > prev) {
            noHealUntil[entity] = until
        }
    }

    @SubscribeEvent
    fun onDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        echoBuffer.remove(player.uuid)
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        if (!e.wasDeath) return
        echoBuffer.remove(e.original.uuid)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        echoBuffer.remove(e.player.uuid)
    }

    /**
     * True when [source] is this mod's own reflection damage.
     *
     * ## Why this guard exists
     * `damageBypassingArmor` builds its source with `DamageSources#create(REFLECTION, attacker)`, and
     * that two-argument overload passes the entity as **both** the source and the attacker — verified
     * from bytecode, where `DamageSource(RegistryEntry, Entity)` forwards `(type, entity, entity)` to
     * the three-argument constructor, which stores parameter 3 into the `attacker` field. A reflected
     * hit therefore arrives with a **non-null** `attacker`, which is exactly what this handler keys
     * on, so a second Echo Shield on the other side reflects it straight back.
     *
     * ## What that used to do
     * Two recursion paths, both only reachable when **both** sides wear the enchantment:
     * - `onDamage` reflects `0.03 x level` of every landed hit. The bounce re-enters the attacker's
     *   `onDamage`, which reflects that fraction of *it* back, and so on: the values decay by
     *   `ratio²` (0.36 at level 20) while the depth does not, giving roughly a hundred nested damage
     *   events inside one tick before float underflow stops it.
     * - `onHurt` reflects the **entire buffer**, and the buffer used to be cleared *after* that nested
     *   call — so the amount was identical at every level. Two equal buffers recursed without bound,
     *   i.e. a stack overflow rather than a spike.
     *
     * `damageBypassingArmor` also zeroes `timeUntilRegen`, so i-frames never broke the chain either.
     *
     * ## The rule
     * For damage that is itself a reflection:
     * - `onHurt` does nothing at all, so the `buffer >= hit` -> "cancel and bounce the whole buffer"
     *   branch is unreachable: such a hit can never be **fully negated**, and a shield can never
     *   counter its own counter.
     * - `onDamage` still applies the plain `0.03 x level` mitigation — soaking is the reason to wear
     *   the shield — but it does **not** bounce that part back and does **not** store it, so nothing
     *   derived from a reflection can be bounced later either. `markNoHeal` is skipped for the same
     *   reason: it is the shield's counter-attack.
     *
     * Deliberate consequence: in a duel between two shielded players, the attacker's shield soaks
     * `ratio` of the reflection it just received and that is all — at level 20 (`ratio` 0.6) it takes
     * the remaining 40%.
     */
    private fun isReflection(source: DamageSource): Boolean =
        source.typeRegistryEntry.matchesKey(ModDamageTypes.REFLECTION)

    private fun playFeedback(player: PlayerEntity, big: Boolean) {
        val world = player.world as? ServerWorld ?: return
        if (big) {
            world.spawnParticles(ParticleTypes.EXPLOSION, player.x, player.y + 1.0, player.z, 1, 0.0, 0.0, 0.0, 0.0)
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, player.x, player.y + 1.0, player.z, 30, 0.5, 0.5, 0.5, 0.1)
            world.playSound(null, player.x, player.y, player.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.4f)
        } else {
            world.spawnParticles(ParticleTypes.ENCHANT, player.x, player.y + 1.0, player.z, 6, 0.4, 0.4, 0.4, 0.02)
        }
    }
}
