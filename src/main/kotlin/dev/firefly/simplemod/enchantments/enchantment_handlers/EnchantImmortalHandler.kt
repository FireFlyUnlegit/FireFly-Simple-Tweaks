package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantImmortal
import dev.firefly.simplemod.util.getArmorEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*
import kotlin.random.Random.Default.nextFloat

object EnchantImmortalHandler : Listenable {

    private const val HITS_PER_ROLL = 20

    private val hitCounter = mutableMapOf<UUID, Int>()
    private val shieldCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val target = e.entityLiving
        val id = target.uniqueID

        val lvl = target.getArmorEnchantLevel(EnchantImmortal.INSTANCE)
        if (lvl <= 0) {
            hitCounter.remove(id)
            shieldCounter.remove(id)
            return
        }

        val shield = shieldCounter[id] ?: 0
        if (shield > 0) {
            shieldCounter[id] = shield - 1
            e.isCanceled = true
            return
        }

        val hits = (hitCounter[id] ?: 0) + 1
        if (hits < HITS_PER_ROLL) {
            hitCounter[id] = hits
            return
        }
        hitCounter[id] = 0

        val rate = (0.2f + 0.2f * lvl).coerceAtMost(1.0f)
        if (nextFloat() > rate) return

        shieldCounter[id] = lvl - 1
        e.isCanceled = true
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uniqueID
        hitCounter.remove(id)
        shieldCounter.remove(id)
    }
}