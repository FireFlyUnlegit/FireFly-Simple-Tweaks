package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantVitality
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantVitalityHandler : Listenable {

    private val MODIFIER_ID: UUID = UUID.fromString("a4b3c2d1-e5f6-4a5b-9c8d-7e6f5a4b3c2d")

    private val appliedLevel = WeakHashMap<EntityPlayer, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val total = totalLevel(p)
        val last = appliedLevel[p] ?: -1
        if (total == last) return

        val attr = p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)

        val existing = attr.getModifier(MODIFIER_ID)
        if (existing != null) attr.removeModifier(existing)

        if (total > 0) {
            val bonus = total * 4.0 + (total / 10) * 10.0
            attr.applyModifier(
                AttributeModifier(MODIFIER_ID, "Vitality", bonus, 0)
            )
        }

        if (p.health > p.maxHealth) p.health = p.maxHealth

        appliedLevel[p] = total
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        appliedLevel.remove(e.player)
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