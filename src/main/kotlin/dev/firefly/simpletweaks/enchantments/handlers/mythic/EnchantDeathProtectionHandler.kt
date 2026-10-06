package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantDeathProtectionHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                      | 1.21.1                                                               |
 * |-------------------------------------------------------------|----------------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`                    | `compat.event.LivingDamageEvent`                                     |
 * | `net.minecraftforge...EventPriority`                        | `compat.event.EventPriority` (the annotation keeps `priority`)        |
 * | `e.invalid`                                                 | same extension name, now `compat.invalid`                            |
 * | `e.entityLiving as? EntityPlayer`                           | `e.entityLiving as? PlayerEntity`                                    |
 * | `player.chestplate`                                         | same extension (`util/PlayerUtils.kt`, `EquipmentSlot.CHEST`)         |
 * | `player.absorptionAmount = x`                               | same synthetic property (`method_6073` / `method_6067`)               |
 * | `player.world.playSound(player, x, y, z, SoundEvent, ...)`  | identical overload `World.playSound(PlayerEntity, double, double, double, SoundEvent, SoundCategory, float, float)` |
 * | `player.posX/posY/posZ`                                     | `player.x/y/z`                                                       |
 * | `player.sendMessage(TextComponentString("..."))`            | `player.sendMessage(Text.literal("..."))`                            |
 * | `EnchantDeathProtection` (Enchantment object)               | `ModEnchantmentKeys.DEATH_PROTECTION` (RegistryKey)                  |
 *
 * ⚠️ One forced mapping — the enchantment-level decrement.
 * 1.12.2 read the chest's `Map<Enchantment, Integer>` via `EnchantmentHelper.getEnchantments`,
 * edited it in place (level - 1, or `remove` at 0) and wrote it back with `setEnchantments`.
 * 1.21 stores enchantments in an `ItemEnchantmentsComponent` data component, so the same edit is an
 * `ItemEnchantmentsComponent.Builder` rebuild handed to `EnchantmentHelper.set`. The stored levels
 * are identical; only the container type differs.
 */
object EnchantDeathProtectionHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.entityLiving as? PlayerEntity ?: return

        if (player.health - e.amount >= 0f) return

        val chest = player.chestplate
        val lvl = getItemSpecificEnchantLevel(chest, ModEnchantmentKeys.DEATH_PROTECTION)
        if (lvl <= 0) return

        val absorption = lvl * 20f

        // 1.12.2: `val enchants = EnchantmentHelper.getEnchantments(chest)` then mutate the map
        // (`enchants[EnchantDeathProtection] = lvl - 1` / `enchants.remove(...)`) and
        // `EnchantmentHelper.setEnchantments(enchants, chest)`.
        // 1.21: rebuild the enchantment component with the same two outcomes.
        val enchants = EnchantmentHelper.getEnchantments(chest)
        val entry = enchants.enchantments.firstOrNull {
            it.matchesKey(ModEnchantmentKeys.DEATH_PROTECTION)
        }
        if (entry != null) {
            val builder = ItemEnchantmentsComponent.Builder(enchants)
            if (lvl - 1 > 0) {
                builder.set(entry, lvl - 1)
            } else {
                builder.remove { it.matchesKey(ModEnchantmentKeys.DEATH_PROTECTION) }
            }
            EnchantmentHelper.set(chest, builder.build())
        }

        player.absorptionAmount = absorption
        e.amount = 0f
        e.isCanceled = true
        player.world.playSound(
            player,
            player.x,
            player.y,
            player.z,
            SoundEvents.ITEM_TOTEM_USE,
            SoundCategory.PLAYERS,
            1.0f,
            1.0f
        )
        player.sendMessage(Text.literal("Death Protection triggered!"))
        STLog.log("DeathProtection") {
            "player=${player.name.string}, lvl=$lvl, healthBefore=${player.health}, preventedDamage=${e.amount}, " +
                "absorption=$absorption, chestLvlAfter=${lvl - 1}, outcome=death-prevented"
        }
    }
}
