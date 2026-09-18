package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantAoeAttack
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.DamageSource
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantAoeAttackHandler : Listenable {

    private val inAoe = ThreadLocal.withInitial { false }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (inAoe.get()) return
        if (e.invalid) return
        val attacker = e.source.trueSource as? EntityPlayer ?: return
        val target = e.entityLiving ?: return

        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantAoeAttack)
        if (lvl <= 0) return

        val box = target.entityBoundingBox.grow(0.2 * lvl)
        val others = target.world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)
            .filter {
                it !== attacker && it !== target && it.isEntityAlive &&
                        it.canBeAttackedWithItem() && !it.isEntityInvulnerable(e.source)
            }
        if (others.isEmpty()) return

        val originalAmount = e.amount
        val perTarget = (originalAmount * lvl / (others.size + 1)).coerceAtMost(originalAmount)

        e.amount = perTarget

        inAoe.set(true)
        try {
            for (other in others) {
                other.hurtResistantTime = 0
                other.attackEntityFrom(DamageSource.causePlayerDamage(attacker), perTarget)
            }
        } finally {
            inAoe.set(false)
        }
    }
}