package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantGravityStrike
import dev.firefly.simpletweaks.util.*
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantGravityStrikeHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantGravityStrike)
        if (lvl <= 0) return
        val fall = attacker.fallDistance
        if (fall < 1.5f) return
        e.amount *= 1f + (fall * 0.025f * lvl).coerceAtMost(lvl * 0.5f)
        val world = e.target.world as? WorldServer ?: return
        val radius = 0.8 + fall * 0.08
        world.spawnRingParticles(
            EnumParticleTypes.CRIT,
            e.target.posX, e.target.posY + 0.15, e.target.posZ,
            radius, (12 + lvl * 4).coerceAtMost(40), 0.0, 0.2
        )
        world.spawnParticle(
            EnumParticleTypes.EXPLOSION_NORMAL,
            e.target.posX, e.target.posY + 0.3, e.target.posZ,
            4 + lvl, 0.3, 0.15, 0.3, 0.05
        )
        world.spawnParticle(
            EnumParticleTypes.CLOUD,
            e.target.posX, e.target.posY + 0.05, e.target.posZ,
            3 + lvl, radius * 0.6, 0.05, radius * 0.6, 0.02
        )
    }
}