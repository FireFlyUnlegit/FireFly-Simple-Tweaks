package dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower

import net.minecraft.nbt.NBTTagList
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object SaveHandler {

    @SubscribeEvent
    fun onPlayerSave(event: PlayerEvent.SaveToFile) {
        val player = event.entityPlayer
        val inv = InfiniteBagInventory(player)
        inv.markDirty()
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {

        if (!event.isWasDeath) return

        val oldPlayer = event.original
        val newPlayer = event.entityPlayer

        val oldNbt = oldPlayer.getEntityData()
        val newNbt = newPlayer.getEntityData()

        if (oldNbt.hasKey("InfiniteBagItems")) {
            val list = oldNbt.getTagList("InfiniteBagItems", 10)
            val copyList = NBTTagList()
            for (i in 0 until list.tagCount()) {
                copyList.appendTag(list.getCompoundTagAt(i).copy())
            }

            newNbt.setTag("InfiniteBagItems", copyList)
            newNbt.setInteger("InfiniteBagSize", oldNbt.getInteger("InfiniteBagSize"))
            newNbt.setBoolean("__infinite_dirty", true)
        }
    }
}