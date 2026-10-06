package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantVitality
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantVitalityHandler : Listenable {

    private val MODIFIER_ID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_vitality".toByteArray())

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val attr = p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH) ?: return
        val total = totalLevel(p)
        val hasModifier = attr.getModifier(MODIFIER_ID) != null

        if (total > 0 && hasModifier) {
            val existingAmount = attr.getModifier(MODIFIER_ID)?.amount ?: 0.0
            val expected = total * 4.0 + (total / 10) * 10.0
            if (existingAmount == expected) return
        }
        if (total <= 0 && !hasModifier) return

        attr.removeModifier(MODIFIER_ID)
        if (total > 0) {
            val bonus = total * 4.0 + (total / 10) * 10.0
            val percentage = bonus * 0.01 * total
            attr.applyModifier(AttributeModifier(MODIFIER_ID, "Vitality", bonus + percentage, 0))
        }
        p.syncAttributes()

        if (p.health > p.maxHealth) p.health = p.maxHealth
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        clearModifier(event.original)
        clearModifier(event.entityPlayer)
    }

    @SubscribeEvent
    fun onLogout(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        clearModifier(e.player)
    }

    private fun clearModifier(p: EntityPlayer) {
        p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(MODIFIER_ID)
    }

    private fun totalLevel(p: EntityPlayer): Int {
        var total = 0
        for (slot in EntityEquipmentSlot.entries) {
            if (slot.slotType != EntityEquipmentSlot.Type.ARMOR) continue
            total += getItemSpecificEnchantLevel(p.getItemStackFromSlot(slot), EnchantVitality)
        }
        return total
    }
}