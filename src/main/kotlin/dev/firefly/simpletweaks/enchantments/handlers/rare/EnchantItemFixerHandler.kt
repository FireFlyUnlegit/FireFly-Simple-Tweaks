package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.rare.EnchantItemFixer
import dev.firefly.simpletweaks.util.displayedItem
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.random.Random.Default.nextFloat

object EnchantItemFixerHandler : Listenable {
    @SubscribeEvent
    fun onPlayerTick(e:TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        p.displayedItem.forEach {
            val level = getItemSpecificEnchantLevel(it, EnchantItemFixer)
            if (level > 0) {
                if (nextFloat() <= 0.02 * level && it.itemDamage > 0) it.itemDamage -= (1 + level / 2).coerceAtMost(it.itemDamage)
            }
        }
    }
}