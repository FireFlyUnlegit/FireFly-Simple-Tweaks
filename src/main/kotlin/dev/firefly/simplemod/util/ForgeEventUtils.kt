package dev.firefly.simplemod.util

import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.gameevent.TickEvent

fun CriticalHitEvent.setCrit(state: Boolean) {
    this.result = if (state) Event.Result.ALLOW else Event.Result.DENY
    this.damageModifier += 0.5f
}
val CriticalHitEvent.isCrit: Boolean
    get() = this.isVanillaCritical || this.result == Event.Result.ALLOW
val LivingHurtEvent.isClientSide: Boolean
    get() = this.entity.world.isRemote
val LivingHurtEvent.attacker: EntityLivingBase?
    get() = (this.source.trueSource as? EntityLivingBase)
val LivingDamageEvent.isClientSide: Boolean
    get() = this.entity.world.isRemote
val CriticalHitEvent.isClientSide: Boolean
    get() = this.entity.world.isRemote
val AttackEntityEvent.isClientSide: Boolean
    get() = this.entity.world.isRemote
val LivingHurtEvent.invalid: Boolean
    get() = this.isClientSide || this.isCanceled
val LivingDamageEvent.invalid: Boolean
    get() = this.isClientSide || this.isCanceled
val CriticalHitEvent.invalid: Boolean
    get() = this.isClientSide || this.isCanceled
val AttackEntityEvent.invalid: Boolean
    get() = this.isClientSide || this.isCanceled
val TickEvent.PlayerTickEvent.invalid: Boolean
    get() = this.phase != TickEvent.Phase.END || this.player == null || this.isCanceled