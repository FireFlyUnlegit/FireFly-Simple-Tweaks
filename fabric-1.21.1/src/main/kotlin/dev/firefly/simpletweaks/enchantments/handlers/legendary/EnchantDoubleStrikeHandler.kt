package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.AttackEntityEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.attackCharge
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.*
import kotlin.random.Random.Default.nextFloat

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantDoubleStrikeHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                    | 1.21.1                                                                    |
 * |-----------------------------------------------------------|---------------------------------------------------------------------------|
 * | `net.minecraftforge...AttackEntityEvent`                  | `compat.event.AttackEntityEvent` (Forge's `e.entityPlayer` -> `e.player`)  |
 * | `net.minecraftforge...LivingHurtEvent`                    | `compat.event.LivingHurtEvent`                                             |
 * | `net.minecraftforge...EventPriority`                      | `compat.event.EventPriority` (the annotation keeps `priority`)             |
 * | `e.source.trueSource as? EntityPlayer`                    | `e.source.attacker as? PlayerEntity` (`DamageSource.getAttacker()`, `method_5529`; no import needed — this is the vanilla accessor, not the event extension) |
 * | `attacker.world.isRemote`                                 | `WorldSide.isClient(attacker.world)` (field_9236 is a FIELD)               |
 * | `player.heldItemMainhand`                                 | `player.mainHandStack` (`method_6047`)                                    |
 * | `player.attackCharge`                                     | `player.attackCharge` — `util/PlayerUtils.kt` extension; see below          |
 * | `target.hurtResistantTime = 0`                            | `target.timeUntilRegen = 0` (`Entity`'s public field `field_6019`)          |
 * | `target.attackEntityFrom(DamageSource.causePlayerDamage(attacker), x)` | `target.damage(attacker.damageSources.playerAttack(attacker), x)` (`DamageSources.playerAttack` is the 1.21 `causePlayerDamage`; it sets BOTH the source and attacker entity, verified in the constructor bytecode) |
 * | `target.motionX/motionY/motionZ`                          | `target.velocity` (single `Vec3d`, `getVelocity`/`setVelocity`)             |
 * | `inExtraStrike` (`ThreadLocal<Boolean>`)                  | unchanged — still the re-entrancy guard for the nested damage event        |
 * | `EnchantDoubleStrike` (Enchantment object)                | `ModEnchantmentKeys.DOUBLE_STRIKE` (RegistryKey)                           |
 *
 * ⚠️ `player.attackCharge`: the 1.12.2 value came from a mixin (`interfaces/AttackChargeAccessor` +
 * `MixinEntityPlayer`, which `@Redirect`-ed `getCooledAttackStrength(0.5F)` inside
 * `attackTargetEntityWithCurrentItem`). 1.21 exposes the same number as the public
 * `PlayerEntity#getAttackCooldownProgress(0.5F)`, so the accessor and the redirect are deleted
 * outright — see the KDoc on the extension for the timing argument (the read happens at the HEAD of
 * `ServerPlayerEntity#attack`, before vanilla resets `lastAttackedTicks`).
 *
 * ⚠️ The re-entrant call is `target.damage(...)`, i.e. 1.21's `Entity#damage` — the counterpart of
 * 1.12.2's `attackEntityFrom`. It fires this project's `LivingAttackEvent` + `LivingHurtEvent` seam
 * on the same thread, which is exactly why `inExtraStrike` exists; the guard is kept verbatim, so the
 * nested event returns immediately and the strike stays a single extra hit.
 */
object EnchantDoubleStrikeHandler : Listenable {

    private val inExtraStrike = ThreadLocal.withInitial { false }
    private val armed = WeakHashMap<PlayerEntity, Int>()

    @SubscribeEvent
    fun onAttack(e: AttackEntityEvent) {
        if (inExtraStrike.get()) return
        val player = e.player
        if (e.invalid) return

        armed.remove(player)
        if (player.attackCharge < 0.848) return
        val lvl = getItemSpecificEnchantLevel(player.mainHandStack, ModEnchantmentKeys.DOUBLE_STRIKE)
        if (lvl <= 0) return
        val roll = nextFloat()
        if (roll < 0.25f + 0.05f * lvl) {
            armed[player] = lvl
            STLog.log("DoubleStrike") {
                "player=${player.name.string}, lvl=$lvl, attackCharge=${player.attackCharge}, " +
                    "roll=$roll, chance=${0.25f + 0.05f * lvl}, outcome=armed"
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onHurt(e: LivingHurtEvent) {
        if (inExtraStrike.get()) return
        val attacker = e.source.attacker as? PlayerEntity ?: return
        if (e.invalid) return

        if (WorldSide.isClient(attacker.world)) return
        val lvl = armed.remove(attacker) ?: return
        val target = e.entityLiving
        val velocity = target.velocity
        inExtraStrike.set(true)
        try {
            target.timeUntilRegen = 0
            STLog.log("DoubleStrike") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "strikeDamage=${e.amount * (0.4f + 0.10f * lvl)}, multiplier=${0.4f + 0.10f * lvl}, " +
                    "outcome=extra-hit"
            }
            target.damage(
                attacker.damageSources.playerAttack(attacker),
                e.amount * (0.4f + 0.10f * lvl)
            )
            // 1.12.2 restored the three motion fields after the extra hit so the second strike does
            // not double up the knockback; 1.21 keeps the velocity in one Vec3d, so it is saved and
            // restored as a unit.
            target.velocity = velocity
        } finally {
            inExtraStrike.set(false)
        }
    }
}
