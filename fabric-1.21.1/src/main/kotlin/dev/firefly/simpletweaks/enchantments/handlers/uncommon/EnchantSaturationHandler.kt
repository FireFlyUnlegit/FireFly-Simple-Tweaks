package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import java.util.*
import kotlin.random.Random.Default.nextFloat

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantSaturationHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                | 1.21.1                                                                |
 * |---------------------------------------|-----------------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`      | `compat.event.TickEvent`                                               |
 * | `net.minecraftforge...PlayerEvent`    | `compat.event.PlayerEvent` (Forge's two same-named classes are merged) |
 * | `p.world.isRemote`                    | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)                  |
 * | `p.chestplate`                        | `p.chestplate` (`util/PlayerUtils.kt` extension; **not ported yet**, see the batch report) |
 * | `p.uniqueID`                          | `p.uuid` (`EntityLike`, method_5667)                                   |
 * | `p.foodStats`                         | `p.hungerManager` (`PlayerEntity.getHungerManager()`, method_7344)     |
 * | `food.foodLevel`                      | `food.getFoodLevel()` / `food.setFoodLevel(int)` (method_7586 / method_7580) |
 * | `food.saturationLevel`                | `food.getSaturationLevel()` (method_7589)                              |
 * | `food.setFoodSaturationLevel(x)`      | `food.setSaturationLevel(x)` (method_7581 — **renamed**, there is no `setFoodSaturationLevel` in 1.21.1) |
 * | `EnchantSaturation` (Enchantment)     | `ModEnchantmentKeys.SATURATION` (RegistryKey)                          |
 *
 * The accessor calls above are written explicitly (`getFoodLevel()` rather than `food.foodLevel`)
 * because `HungerManager` exposes *both* public fields (`foodLevel`, `saturationLevel`) and
 * getters of the same name — the explicit form is the only unambiguous one in Kotlin.
 */
object EnchantSaturationHandler : Listenable {

    private val cooldown = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val lvl = getItemSpecificEnchantLevel(p.chestplate, ModEnchantmentKeys.SATURATION)
        val recoveryCount: Int = lvl / 8 + 1
        if (lvl <= 0) {
            cooldown.remove(p.uuid)
            return
        }

        val interval = (60 - 5 * lvl).coerceAtLeast(1)
        val tick = (cooldown[p.uuid] ?: 0) + 1
        if (tick < interval) {
            cooldown[p.uuid] = tick
            return
        }
        cooldown[p.uuid] = 0

        val rate = (0.6 + 0.05 * lvl).coerceAtMost(1.0)
        val roll = nextFloat()
        if (roll >= rate) return

        val food = p.hungerManager
        if (food.getFoodLevel() < 20) {
            val foodBefore = food.getFoodLevel()
            food.setFoodLevel((food.getFoodLevel() + recoveryCount).coerceAtMost(20))
            STLog.log("Saturation") {
                "player=${p.name.string}, lvl=$lvl, interval=$interval, roll=$roll, rate=$rate, " +
                    "food=$foodBefore->${food.getFoodLevel()}, recoveryCount=$recoveryCount"
            }
        } else {
            val saturationBefore = food.getSaturationLevel()
            food.setSaturationLevel(
                (food.getSaturationLevel() + recoveryCount).coerceAtMost(food.getFoodLevel().toFloat())
            )
            STLog.log("Saturation") {
                "player=${p.name.string}, lvl=$lvl, interval=$interval, roll=$roll, rate=$rate, " +
                    "saturation=$saturationBefore->${food.getSaturationLevel()}, recoveryCount=$recoveryCount"
            }
        }
    }
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        cooldown.remove(event.player.uuid)
    }

}
