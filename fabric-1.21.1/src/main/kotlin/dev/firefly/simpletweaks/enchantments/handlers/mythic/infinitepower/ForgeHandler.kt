package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.event.LivingAttackEvent
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import net.minecraft.entity.player.PlayerEntity

/**
 * The holder is invulnerable while holding `infinite_power`.
 *
 * Port of 1.12.2 `infinitepower/ForgeHandler.kt` (51 lines) — three Forge damage events, all mapped
 * 1:1 onto this project's compat layer:
 *
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `LivingAttackEvent.entity` + `isCanceled` | `LivingAttackEvent.entityLiving` + `isCanceled` |
 * | `LivingDamageEvent.entity` + `amount` | `LivingDamageEvent.entityLiving` + `amount` |
 * | `LivingDeathEvent.entity` + `isCanceled` | `LivingDeathEvent.entityLiving` + `isCanceled` |
 * | `EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack)` | [EnchantInfinitePowerHandler.heldLevel] |
 *
 * <h2>⚠️ One field has no 1.21 equivalent: `isDead = false`</h2>
 * 1.12.2's death handler force-revived the player with `player.health = maxHealth;
 * player.deathTime = 0; player.isDead = false`. In 1.21 the death flag is the **protected**
 * `LivingEntity.dead` field with no public setter, and the supported way to stop a death is to cancel
 * `LivingDeathEvent` — which this port's seam implements (`LivingEntityDeathMixin`). The revive
 * therefore rests on the cancel plus restoring health, and not on clearing the flag. Recorded rather
 * than hidden because it is the one place this handler is not a literal translation.
 */
object ForgeHandler : Listenable {

    @SubscribeEvent
    fun onLivingAttack(e: LivingAttackEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (EnchantInfinitePowerHandler.heldLevel(player) <= 0) return
        e.isCanceled = true
        if (player.health < player.maxHealth) {
            player.health = player.maxHealth
        }
    }

    @SubscribeEvent
    fun onLivingDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (EnchantInfinitePowerHandler.heldLevel(player) <= 0) return
        e.isCanceled = true
        player.health = player.maxHealth
        player.deathTime = 0
    }

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (EnchantInfinitePowerHandler.heldLevel(player) <= 0) return
        e.isCanceled = true
        e.amount = 0.0f
    }
}
