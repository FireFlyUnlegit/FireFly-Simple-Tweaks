package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mythic.EnchantUnbreakable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object EnchantUnbreakableHandler : Listenable {

    private const val TAG_UNBREAKABLE = "Unbreakable"
    private const val TAG_MARK = "st_unbreakable"

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        updateStack(p.heldItemMainhand)
        updateStack(p.heldItemOffhand)
        for (stack in p.inventory.armorInventory) {
            updateStack(stack)
        }
    }

    private fun updateStack(stack: ItemStack) {
        if (stack.isEmpty) return

        val tag = stack.tagCompound
        val hasEnchant = getItemSpecificEnchantLevel(stack, EnchantUnbreakable) > 0
        val hasMark = tag?.getBoolean(TAG_MARK) == true

        if (hasEnchant && !hasMark) {
            val t = tag ?: NBTTagCompound().also { stack.tagCompound = it }
            t.setBoolean(TAG_UNBREAKABLE, true)
            t.setBoolean(TAG_MARK, true)
        } else if (!hasEnchant && hasMark) {
            tag.removeTag(TAG_UNBREAKABLE)
            tag.removeTag(TAG_MARK)
        }
    }
}