package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantVoidProtection
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.tp
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.event.entity.living.LivingFallEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantVoidProtectionHandler : Listenable {
    val waitForCancel = mutableMapOf<UUID, Boolean>()
    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        val stack = p.inventory.armorInventory[0]
        val level = getItemSpecificEnchantLevel(stack, EnchantVoidProtection)
        val uuid = p.uniqueID
        if (level > 0) {
            if (p.posY <= -64 && p.fallDistance > 32.0) {
                val enchants = EnchantmentHelper.getEnchantments(stack)
                if (level - 1 > 0) {
                    enchants[EnchantVoidProtection] = level - 1
                } else enchants.remove(EnchantVoidProtection)
                p.tp(y=256.0)
                p.sendMessage(TextComponentString("Triggered Void Protection!"))
                EnchantmentHelper.setEnchantments(enchants, stack)
                waitForCancel[uuid] = true
            }
        }
        if (p.onGround && waitForCancel[uuid] == true) {
            waitForCancel[uuid] = false
        }
    }
    @SubscribeEvent
    fun onLivingFall(event: LivingFallEvent) {
        val entity = event.entity
        if (entity !is EntityPlayer) return
        if (entity.world.isRemote) return

        val stack = entity.inventory.armorInventory[0]
        if (stack.isEmpty) return

        val level = getItemSpecificEnchantLevel(stack, EnchantVoidProtection)
        if (level <= 0) return

        if (waitForCancel[entity.uniqueID] == true) {
            event.isCanceled = true
            entity.fallDistance = 0f
            waitForCancel[entity.uniqueID] = false
        }
    }

}