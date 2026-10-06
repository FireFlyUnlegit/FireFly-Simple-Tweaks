package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantFlightHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                          | 1.21.1                                                                |
 * |-------------------------------------------------|-----------------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`                | `compat.event.TickEvent`                                               |
 * | `net.minecraftforge...gameevent.PlayerEvent`    | `compat.event.PlayerEvent` (this project merged both Forge PlayerEvents) |
 * | `p.uniqueID` / `event.player.uniqueID`          | `p.uuid` / `event.player.uuid` (`getUuid()`, declared on `EntityLike`) |
 * | `p.inventory.armorInventory[2]`                 | `p.inventory.getArmorStack(2)` — index 2 is the chest slot in both (1.21's `PlayerInventory.armor` is ordered feet/legs/chest/head, same as 1.12.2) |
 * | `p.foodStats.foodLevel`                         | `p.hungerManager.foodLevel` (`HungerManager#getFoodLevel`/`setFoodLevel`) |
 * | `p.capabilities.allowFlying`                    | `p.abilities.allowFlying` (`PlayerAbilities.allowFlying`, public field) |
 * | `p.capabilities.isFlying`                       | `p.abilities.flying` (1.21 renamed the field: `isFlying` -> `flying`)   |
 * | `p.sendPlayerAbilities()` (EntityPlayerMP)      | `p.sendAbilitiesUpdate()` (`PlayerEntity#sendAbilitiesUpdate`)          |
 * | `p.isCreative` / `p.isSpectator`                | unchanged (`isCreative()` / `isSpectator()`)                            |
 * | `stack.itemDamage += 2`                         | `stack.damage += 2` (`ItemStack#getDamage`/`setDamage`, both public and neither clamped — same as the raw 1.12.2 field write) |
 * | `EnchantFlight.maxLevel`                        | `MAX_LEVEL` constant — see below                                        |
 * | `EnchantFlight` (Enchantment object)            | `ModEnchantmentKeys.FLIGHT` (RegistryKey)                               |
 *
 * ⚠️ Forced mapping: 1.12.2 read `EnchantFlight.maxLevel` (3). Since 1.21 the max level lives in the
 * enchantment JSON (`data/simple_tweaks/enchantment/flight.json`, `"max_level": 3`), so the value is
 * inlined as `MAX_LEVEL`. If that JSON changes, this constant must be updated by hand.
 *
 * The `if (p is ServerPlayerEntity)` guards are kept even though 1.21's `sendAbilitiesUpdate()` exists
 * on `PlayerEntity`: 1.12.2 only sent the packet from `EntityPlayerMP`, and the packet is what makes
 * the client's ability flags follow the server.
 */
object EnchantFlightHandler : Listenable {
    private val isControllingFlight = mutableMapOf<UUID, Boolean>()
    private val flyingTick = mutableMapOf<UUID, Int>()

    /** 1.12.2 `EnchantFlight.maxLevel`; now `"max_level"` in the flight enchantment JSON. */
    private val maxLevel = 3

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        val uuid = p.uuid
        val stack = p.inventory.getArmorStack(2)
        val enchantment = ModEnchantmentKeys.FLIGHT
        val level = getItemSpecificEnchantLevel(stack, enchantment)
        if (level > 0 && p.hungerManager.foodLevel > 0) {
            val wasControlling = isControllingFlight[uuid] == true
            isControllingFlight[uuid] = true
            flyingTick[uuid] = (flyingTick[uuid] ?: 0) + 1
            p.abilities.allowFlying = true
            if (p is ServerPlayerEntity) {
                p.sendAbilitiesUpdate()
            }
            val tick = flyingTick[uuid] ?: 0
            if (!wasControlling) {
                STLog.log("Flight") {
                    "player=${p.name.string}, lvl=$level, food=${p.hungerManager.foodLevel}, outcome=flight-granted"
                }
            }
            if (p.abilities.flying && level < maxLevel && (!p.isCreative || !p.isSpectator)) {
                if (tick % (20 * level) == 0) {
                    val damageBefore = stack.damage
                    stack.damage += 2
                    STLog.log("Flight") {
                        "player=${p.name.string}, lvl=$level, tick=$tick, food=${p.hungerManager.foodLevel}, " +
                            "elytraDamage=$damageBefore->${stack.damage}, outcome=wing-cost"
                    }
                }
                if (tick % (50 * level) == 0) {
                    p.hungerManager.foodLevel--
                    STLog.log("Flight") {
                        "player=${p.name.string}, lvl=$level, tick=$tick, " +
                            "food=${p.hungerManager.foodLevel + 1}->${p.hungerManager.foodLevel}, outcome=hunger-cost"
                    }
                }
            }
        } else if (isControllingFlight[uuid] == true) {
            isControllingFlight[uuid] = false
            flyingTick[uuid] = 0
            p.abilities.allowFlying = false
            p.abilities.flying = false
            if (p is ServerPlayerEntity) {
                p.sendAbilitiesUpdate()
            }
            STLog.log("Flight") {
                "player=${p.name.string}, lvl=$level, food=${p.hungerManager.foodLevel}, outcome=flight-revoked"
            }
        }
    }
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        isControllingFlight.remove(event.player.uuid)
        flyingTick.remove(event.player.uuid)
    }
}
