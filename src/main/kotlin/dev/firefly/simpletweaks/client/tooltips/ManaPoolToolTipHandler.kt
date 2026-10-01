package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.enchantments.mystery.EnchantCelestialBlessing
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.roundToInt

@SideOnly(Side.CLIENT)
object ManaPoolToolTipHandler {

    @SubscribeEvent
    fun onItemTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        if (stack.isEmpty) return
        val player = event.entityPlayer ?: return
        if (!player.world.isRemote) return

        val lvl = getItemSpecificEnchantLevel(stack, EnchantCelestialBlessing)
        if (lvl <= 0) return

        val pool = ClientManaPoolCache.get(player.uniqueID)
        val display = if (pool % 1f == 0f) {
            pool.roundToInt().toString()
        } else {
            "%.2f".format(pool)
        }
        event.toolTip.add("§bManaPool: §f$display")
    }
}