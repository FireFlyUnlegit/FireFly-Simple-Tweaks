package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantVitality
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
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

    private val appliedLevel = WeakHashMap<EntityPlayer, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val attr = p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH) ?: return

        val total = totalLevel(p)
        val hasModifier = attr.getModifier(MODIFIER_ID) != null

        if (total <= 0 && !hasModifier) {
            appliedLevel.remove(p)
            return
        }
        if (total > 0 && hasModifier && appliedLevel[p] == total) return

        attr.removeModifier(MODIFIER_ID)

        if (total > 0) {
            val bonus = total * 4.0 + (total / 10) * 10.0
            attr.applyModifier(
                AttributeModifier(MODIFIER_ID, "Vitality", bonus, 0)
            )
            appliedLevel[p] = total
        } else {
            appliedLevel.remove(p)
        }

        if (p.health > p.maxHealth) p.health = p.maxHealth
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        clearModifier(event.original)
        clearModifier(event.entityPlayer)
        appliedLevel.remove(event.original)
        appliedLevel.remove(event.entityPlayer)
    }

    @SubscribeEvent
    fun onLogout(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        clearModifier(e.player)
        appliedLevel.remove(e.player)
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