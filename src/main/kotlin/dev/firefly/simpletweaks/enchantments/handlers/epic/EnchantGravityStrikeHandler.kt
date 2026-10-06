package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.spawnRingParticles
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantGravityStrikeHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                                          |
 * |-----------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`        | `compat.event.LivingHurtEvent`                                  |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)                          |
 * | `attacker.fallDistance`                       | unchanged (public field `field_6012`)                           |
 * | `e.target.posX/posY/posZ`                     | `e.target.x/y/z`                                                |
 * | `WorldServer`                                 | `net.minecraft.server.world.ServerWorld`                        |
 * | `world.spawnRingParticles(EnumParticleTypes, ...)` | `util/WorldExtensions.kt` extension, now typed on `ParticleEffect` |
 * | `EnumParticleTypes.CRIT` / `.EXPLOSION_NORMAL` / `.CLOUD` | `ParticleTypes.CRIT` / `ParticleTypes.POOF` (1.13 renamed `explosion_normal` to `poof`) / `ParticleTypes.CLOUD` |
 * | `EnchantGravityStrike` (Enchantment)          | `ModEnchantmentKeys.GRAVITY_STRIKE` (RegistryKey)               |
 *
 * The 1.5-block fall gate, the `min(fall × 2.5 % × lvl, 50 % × lvl)` multiplier and every particle
 * count/offset are copied verbatim.
 */
object EnchantGravityStrikeHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, ModEnchantmentKeys.GRAVITY_STRIKE)
        if (lvl <= 0) return
        val fall = attacker.fallDistance
        if (fall < 1.5f) return
        val bonus = (fall * 0.025f * lvl).coerceAtMost(lvl * 0.5f)
        e.amount *= 1f + bonus
        STLog.log("GravityStrike") {
            "attacker=${attacker.name.string}, target=${e.target.name.string}, lvl=$lvl, fall=$fall, " +
                "bonus=$bonus, damage=${e.amount}"
        }
        val world = e.target.world as? ServerWorld ?: return
        val radius = 0.8 + fall * 0.08
        world.spawnRingParticles(
            ParticleTypes.CRIT,
            e.target.x, e.target.y + 0.15, e.target.z,
            radius, (12 + lvl * 4).coerceAtMost(40), 0.0, 0.2
        )
        world.spawnParticles(
            ParticleTypes.POOF,
            e.target.x, e.target.y + 0.3, e.target.z,
            4 + lvl, 0.3, 0.15, 0.3, 0.05
        )
        world.spawnParticles(
            ParticleTypes.CLOUD,
            e.target.x, e.target.y + 0.05, e.target.z,
            3 + lvl, radius * 0.6, 0.05, radius * 0.6, 0.02
        )
    }
}
