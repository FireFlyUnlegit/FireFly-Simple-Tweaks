package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantChargedStrikeHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                     | 1.21.1                                                          |
 * |--------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`     | `compat.event.LivingHurtEvent`                                  |
 * | `net.minecraftforge...LivingDeathEvent`    | `compat.event.LivingDeathEvent`                                 |
 * | `net.minecraftforge...EventPriority`       | `compat.event.EventPriority` (annotation keeps `priority = LOWEST`) |
 * | `EntityPlayer`                             | `net.minecraft.entity.player.PlayerEntity`                      |
 * | `e.source.trueSource`                      | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529) |
 * | `attacker.heldItemMainhand`                | `attacker.mainHandStack` (method_6047)                          |
 * | `EnchantChargedStrike` (Enchantment)       | `GeneratedEnchantments.CHARGED_STRIKE` (RegistryKey)               |
 *
 * The per-player "stored damage" map, the 15 %×level carry-over and the death cleanup are unchanged.
 */
@ModEnchantment(
    id = "charged_strike",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 4,
    anvilCost = 8,
    minCostBase = 30,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.35,
    order = 24,
)
object EnchantChargedStrikeHandler : Listenable {

    private val stored = WeakHashMap<PlayerEntity, Float>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.source.attacker as? PlayerEntity ?: return

        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.CHARGED_STRIKE)
        if (lvl <= 0) {
            stored.remove(attacker)
            return
        }

        val currentDamage = e.amount
        val bonus = stored[attacker] ?: 0f

        if (bonus > 0f) {
            e.amount = currentDamage + bonus
            STLog.log("ChargedStrike") {
                "attacker=${attacker.name.string}, lvl=$lvl, stored=$bonus, " +
                    "damage=$currentDamage->${e.amount}, nextStored=${currentDamage * (0.15f * lvl)}"
            }
        }

        stored[attacker] = currentDamage * (0.15f * lvl)
    }

    @SubscribeEvent
    fun onPlayerDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        stored.remove(player)
    }
}
