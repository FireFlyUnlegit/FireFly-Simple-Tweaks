package dev.firefly.simplemod.enchantments.handlers.rare

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.rare.EnchantImmortal
import dev.firefly.simplemod.util.attacker
import dev.firefly.simplemod.util.chance
import dev.firefly.simplemod.util.getArmorEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.SoundCategory
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import java.util.*

object EnchantImmortalHandler : Listenable {


    private val hitCounter = mutableMapOf<UUID, Int>()
    private val shieldCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val target = e.entityLiving
        val id = target.uniqueID
        val attacker = e.attacker?: return
        val lvl = target.getArmorEnchantLevel(EnchantImmortal)
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
        if (hits < 22 - lvl * 2) {
            hitCounter[id] = hits
            return
        }
        hitCounter[id] = 0

        if (chance(0.2 + 0.2 * lvl)) return

        shieldCounter[id] = lvl - 1
        e.isCanceled = true
        if (attacker is EntityPlayer) {
            attacker.world.playSound(
                attacker,
                attacker.posX,
                attacker.posY,
                attacker.posZ,
                SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
            )
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uniqueID
        hitCounter.remove(id)
        shieldCounter.remove(id)
    }
}