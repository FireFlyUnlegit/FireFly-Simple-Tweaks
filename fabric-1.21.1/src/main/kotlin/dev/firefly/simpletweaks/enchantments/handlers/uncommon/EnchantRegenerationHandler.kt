package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.leggings
import java.util.UUID
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantRegenerationHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                       | 1.21.1                                                       |
 * |--------------------------------------------------------------|--------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`                              | `compat.event.TickEvent`                                      |
 * | `net.minecraftforge.event.entity.player.PlayerEvent` **and** `...fml.common.gameevent.PlayerEvent` (`as ev`) | both collapse into the single `compat.event.PlayerEvent` — the `as ev` alias is deleted as the spec requires |
 * | `p.leggings`                                                  | `p.leggings` (`util/PlayerUtils.kt` extension; **not ported yet**, see the batch report) |
 * | `p.uniqueID`                                                  | `p.uuid` (`EntityLike`, method_5667)                          |
 * | `p.health` / `p.maxHealth`                                    | unchanged (`method_6032` / `method_6063`)                     |
 * | `p.heal(heal)`                                                | unchanged (`LivingEntity.heal(float)`, method_6025)           |
 * | `e.isWasDeath`                                                | `e.wasDeath` (the port's `PlayerEvent.Clone` field)            |
 * | `e.original.uniqueID`                                         | `e.original.uuid`                                            |
 * | `EnchantRegeneration` (Enchantment object)                    | `GeneratedEnchantments.REGENERATION` (RegistryKey)               |
 */
@ModEnchantment(
    id = "regeneration",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.LEGGINGS,
    maxLevel = 8,
    weight = 8,
    anvilCost = 4,
    minCostBase = 28,
    minCostPerLevel = 4,
    supportedItems = "#minecraft:enchantable/leg_armor",
    slots = [EnchantSlot.LEGS],
    order = 11,
)
object EnchantRegenerationHandler : Listenable {

    private val regenCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player

        val lvl = getItemSpecificEnchantLevel(p.leggings, GeneratedEnchantments.REGENERATION)
        if (lvl <= 0 || p.health >= p.maxHealth) {
            regenCounter.remove(p.uuid)
            return
        }

        val uuid = p.uuid
        val interval = 25.0 - (2.5 * lvl).coerceAtLeast(5.0)
        val counter = (regenCounter[uuid] ?: 0) + 1
        if (counter >= interval) {
            val heal = maxOf(lvl / 2f, p.maxHealth * 0.00125f * lvl)
            val healthBefore = p.health
            p.heal(heal)
            regenCounter[uuid] = 0
            STLog.log("Regeneration") {
                "player=${p.name.string}, lvl=$lvl, interval=$interval, heal=$heal, " +
                    "health=$healthBefore->${p.health}"
            }
        } else {
            regenCounter[uuid] = counter
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(e: PlayerEvent.PlayerLoggedOutEvent) {
        regenCounter.remove(e.player.uuid)
    }

    @SubscribeEvent
    fun onPlayerClone(e: PlayerEvent.Clone) {
        if (!e.wasDeath) return
        regenCounter.remove(e.original.uuid)
    }
}
