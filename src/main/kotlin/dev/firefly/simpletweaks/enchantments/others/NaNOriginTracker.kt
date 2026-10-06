package dev.firefly.simpletweaks.enchantments.others

import dev.firefly.simpletweaks.core.Listenable
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingHealEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object NaNOriginTracker : Listenable {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onHurtHighest(e: LivingHurtEvent) {
        check("LivingHurtEvent.HIGHEST", e.entityLiving, e.amount, e.source.damageType)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHurtLowest(e: LivingHurtEvent) {
        check("LivingHurtEvent.LOWEST", e.entityLiving, e.amount, e.source.damageType)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onDamageHighest(e: LivingDamageEvent) {
        check("LivingDamageEvent.HIGHEST", e.entityLiving, e.amount, e.source.damageType)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onDamageLowest(e: LivingDamageEvent) {
        check("LivingDamageEvent.LOWEST", e.entityLiving, e.amount, e.source.damageType)
    }

    @SubscribeEvent
    fun onHeal(e: LivingHealEvent) {
        check("LivingHealEvent", e.entityLiving, e.amount, "heal")
    }

    private fun check(stage: String, entity: net.minecraft.entity.EntityLivingBase, amount: Float, source: String) {
        if (entity !is EntityPlayer) return

        val h = entity.health
        val maxH = entity.maxHealth
        val abs = entity.absorptionAmount

        val badAmount = amount.isNaN() || amount.isInfinite()
        val badHealth = h.isNaN() || h.isInfinite()
        val badMax = maxH.isNaN() || maxH.isInfinite()
        val badAbs = abs.isNaN() || abs.isInfinite()

        if (badAmount || badHealth || badMax || badAbs) {
            System.out.println(
                "[ST-NaN-ORIGIN] stage=$stage " +
                        "entity=${entity.name} " +
                        "amount=$amount " +
                        "health=$h " +
                        "maxHealth=$maxH " +
                        "absorption=$abs " +
                        "source=$source"
            )
            // 打印堆栈——看是谁触发的
            Thread.dumpStack()
        }
    }
}