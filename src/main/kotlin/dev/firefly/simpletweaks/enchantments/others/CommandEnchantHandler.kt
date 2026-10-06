package dev.firefly.simpletweaks.enchantments.others

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.event.CommandEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object CommandEnchantHandler : Listenable {

    @SubscribeEvent
    fun onCommand(event: CommandEvent) {
        val cmd = event.command ?: return
        if (cmd.name != "enchant") return

        val params = event.parameters ?: return
        if (params.size < 3) return

        val level = params[2].toIntOrNull() ?: return
        if (level != 0) return

        event.isCanceled = true

        val sender = event.sender ?: return
        val server = sender.server ?: return

        val target: EntityPlayerMP = try {
            CommandBase.getPlayer(server, sender, params[0]) ?: return
        } catch (_: CommandException) {
            return
        }

        val enchantment: Enchantment? = params[1].toIntOrNull()
            ?.let { Enchantment.getEnchantmentByID(it) }
            ?: Enchantment.getEnchantmentByLocation(params[1])
        if (enchantment == null) return

        val item = target.heldItemMainhand
        if (item.isEmpty) return

        val enchants = EnchantmentHelper.getEnchantments(item)
        if (!enchants.containsKey(enchantment)) return

        enchants.remove(enchantment)
        EnchantmentHelper.setEnchantments(enchants, item)
        val lvl = getItemSpecificEnchantLevel(target.heldItemMainhand,enchantment)
        target.sendMessage(
            TextComponentString("§aRemoved Enchantment: §e${enchantment.getTranslatedName(lvl)}")
        )
    }
}