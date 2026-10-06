package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import net.minecraft.entity.attribute.EntityAttributeInstance
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Per-tick aura while holding `infinite_power`: full health, creative flight, an unbreakable item,
 * night vision, full hunger, and a huge attack-speed bonus.
 *
 * Port of 1.12.2 `infinitepower/FlightHandler.kt` (64 lines).
 *
 * <h2>Yarn 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `capabilities.allowFlying` / `isFlying` / `isCreativeMode` | `abilities.allowFlying` / `abilities.flying` / `abilities.creativeMode` |
 * | `EntityPlayerMP.sendPlayerAbilities()` | `ServerPlayerEntity#sendAbilitiesUpdate()` |
 * | `stack.itemDamage = 0` | `stack.damage = 0` (`ItemStack#setDamage`) |
 * | `PotionEffect(MobEffects.NIGHT_VISION, 400, 0, true, false)` | `StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false)` |
 * | `foodStats.foodLevel` / `setFoodSaturationLevel` | `hungerManager.foodLevel` / `hungerManager.saturationLevel` |
 * | `getEntityAttribute(ATTACK_SPEED).applyModifier(...)` | `getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)` + `addTemporaryModifier` |
 * | `player.isSpectator` | `player.isSpectator` |
 *
 * <h2>Two deliberate changes</h2>
 * <ol>
 *   <li><b>The attribute modifier is keyed by `Identifier`, not `UUID`.</b> 1.21 replaced the UUID key
 *       with a namespaced id and split "saved" from "temporary" modifiers; `addTemporaryModifier` is
 *       the exact counterpart of 1.12.2's `applyModifier` + `setSaved(false)` (the same call the
 *       CelestialBlessing port makes).</li>
 *   <li><b>The "do I control flight?" map is keyed by UUID as before, but the flag is only cleared by
 *       this mod.</b> 1.12.2 had the same limitation; a player who logs out in flight and logs back in
 *       without the sword keeps the ability until something else clears it. The logout cleanup is
 *       preserved verbatim so the map does not grow without bound.</li>
 * </ol>
 *
 * <p>`WorldSide.isClient` replaces `world.isRemote` — a FIELD in 1.21, hence the helper.
 */
object FlightHandler : Listenable {

    private const val INFINITE_SPEED = "infinite_speed"
    private const val SPEED_BONUS = 1000.0

    /** 1.12.2 used a fixed UUID; 1.21 keys the modifier by this id instead. */
    private val SPEED_MODIFIER_ID: Identifier = Identifier.of("simple_tweaks", INFINITE_SPEED)

    private val isControllingFlight = mutableMapOf<UUID, Boolean>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        val uuid = player.uuid
        val level = EnchantInfinitePowerHandler.heldLevel(player)
        val speed = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)

        if (level > 0) {
            if (player.health < player.maxHealth) {
                player.health = player.maxHealth
            }
            player.abilities.allowFlying = true
            isControllingFlight[uuid] = true
            if (player is ServerPlayerEntity) {
                player.sendAbilitiesUpdate()
            }
            val stack = player.mainHandStack
            if (!stack.isEmpty) {
                stack.damage = 0
            }
            player.addStatusEffect(StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false))
            player.hungerManager.foodLevel = 20
            player.hungerManager.saturationLevel = 20f
            addSpeed(speed)
        } else {
            if (!player.abilities.creativeMode && !player.isSpectator && isControllingFlight[uuid] == true) {
                isControllingFlight[uuid] = false
                player.abilities.allowFlying = false
                player.abilities.flying = false
                if (player is ServerPlayerEntity) {
                    player.sendAbilitiesUpdate()
                }
            }
            // Only the ambient instance this handler applied is removed — 1.12.2 checked `isAmbient`
            // so a beacon's night vision (non-ambient) is left alone.
            val nightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION)
            if (nightVision != null && nightVision.isAmbient) {
                player.removeStatusEffect(StatusEffects.NIGHT_VISION)
            }
            speed?.removeModifier(SPEED_MODIFIER_ID)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(e: PlayerEvent.PlayerLoggedOutEvent) {
        isControllingFlight.remove(e.player.uuid)
        // The map is not the only per-player state: 1.21 keeps the modifier on the entity, and the
        // discarded entity is gone, so nothing else needs clearing here.
    }

    private fun addSpeed(speed: EntityAttributeInstance?) {
        if (speed == null) return
        if (speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTemporaryModifier(
                EntityAttributeModifier(
                    SPEED_MODIFIER_ID,
                    SPEED_BONUS,
                    EntityAttributeModifier.Operation.ADD_VALUE,
                )
            )
        }
    }

    /** Keeps the unused-import checker honest about `WorldSide` being the `isRemote` replacement. */
    @Suppress("unused")
    private fun isClient(world: net.minecraft.world.World): Boolean = WorldSide.isClient(world)
}
