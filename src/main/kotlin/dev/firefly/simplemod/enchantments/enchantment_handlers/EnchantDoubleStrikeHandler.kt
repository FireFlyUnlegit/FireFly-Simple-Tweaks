package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantDoubleStrike
import dev.firefly.simplemod.util.attackCharge
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.DamageSource
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.WeakHashMap
import kotlin.random.Random.Default.nextFloat

object EnchantDoubleStrikeHandler : Listenable {

    private val inExtraStrike = ThreadLocal.withInitial { false }
    private val armed = WeakHashMap<EntityPlayer, Int>()

    @SubscribeEvent
    fun onAttack(e: AttackEntityEvent) {
        if (inExtraStrike.get()) return
        val player = e.entityPlayer
        if (player.world.isRemote) return
        armed.remove(player)
        if (player.attackCharge < 0.848) return
        val lvl = getItemSpecificEnchantLevel(player.heldItemMainhand, EnchantDoubleStrike.INSTANCE)
        if (lvl <= 0) return
        if (nextFloat() < 0.25f + 0.05f * lvl) armed[player] = lvl
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onHurt(e: LivingHurtEvent) {
        if (inExtraStrike.get()) return
        val attacker = e.source.trueSource as? EntityPlayer ?: return
        if (attacker.world.isRemote) return
        val lvl = armed.remove(attacker) ?: return
        val target = e.entityLiving
        val mx = target.motionX
        val my = target.motionY
        val mz = target.motionZ
        inExtraStrike.set(true)
        try {
            target.hurtResistantTime = 0
            target.attackEntityFrom(
                DamageSource.causePlayerDamage(attacker),
                e.amount * (0.4f + 0.10f * lvl)
            )
            target.motionX = mx
            target.motionY = my
            target.motionZ = mz
        } finally {
            inExtraStrike.set(false)
        }
    }
}