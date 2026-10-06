package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.rare.EnchantExperienceStealer
import dev.firefly.simpletweaks.util.attacker
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.target
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import java.util.*

object EnchantExperienceStealerHandler : Listenable {

    private val pendingXp = WeakHashMap<EntityPlayer, Float>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val atk = e.attacker ?: return
        val lvl = getItemSpecificEnchantLevel(atk.heldItemMainhand, EnchantExperienceStealer)
        if (lvl <= 0) return

        val raw = e.amount * 0.1f * lvl
        if (atk is EntityPlayer) {
            val acc = (pendingXp[atk] ?: 0f) + raw
            val whole = acc.toInt()
            if (whole > 0) {
                atk.addExperience(whole)
                pendingXp[atk] = acc - whole
            } else {
                pendingXp[atk] = acc
            }
        }

        val tgt = e.target
        if (tgt is EntityPlayer) {
            val drain = raw.coerceAtMost(tgt.experience)
            tgt.experience = (tgt.experience - drain).coerceAtLeast(0f)
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        pendingXp.remove(e.player)
    }
}