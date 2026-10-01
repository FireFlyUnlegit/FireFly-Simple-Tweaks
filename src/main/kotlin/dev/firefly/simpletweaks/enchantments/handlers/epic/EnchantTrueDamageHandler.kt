package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantTrueDamage
import dev.firefly.simpletweaks.util.attacker
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import java.util.*

object EnchantTrueDamageHandler : Listenable {
    val damagePool = mutableMapOf<UUID, Float>()
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        val attacker = e.attacker?: return
        val aid = attacker.uniqueID
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantTrueDamage)
        if (lvl > 0) {
            val addition = e.amount * 0.025f * (lvl + 1).coerceAtMost(8)
            e.amount -= addition
            damagePool[aid] = (damagePool[aid]?: 0f) + addition
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.isCanceled && damagePool[e.attacker?.uniqueID] != null) damagePool[e.attacker?.uniqueID?: return] = 0f
        if (e.invalid) return
        val attacker = e.attacker?: return
        val aid = attacker.uniqueID
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantTrueDamage)
        if (lvl > 0 && damagePool[aid] != null) {
            e.amount += damagePool[aid]?: 0f
            damagePool.remove(aid)
        }
    }
    @SubscribeEvent
    fun onPlayerLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val eid = e.player.uniqueID
        damagePool.remove(eid)
    }
}