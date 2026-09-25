package dev.firefly.simplemod.enchantments.handlers.legendary

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.legendary.EnchantDamageLimiter
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.max

object EnchantDamageLimiterHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val p = e.entityLiving ?: return

        var lvl = 0
        for (slot in EntityEquipmentSlot.entries) {
            if (slot.slotType != EntityEquipmentSlot.Type.ARMOR) continue
            lvl = max(lvl, getItemSpecificEnchantLevel(p.getItemStackFromSlot(slot), EnchantDamageLimiter))
        }
        if (lvl <= 0) return

        val maxHealth = p.maxHealth
        val limit = (0.8f - 0.2f * lvl) * maxHealth
        val maxLimit = lvl * maxHealth

        if (e.amount in limit..maxLimit) {
            e.amount = limit
        }
    }
}