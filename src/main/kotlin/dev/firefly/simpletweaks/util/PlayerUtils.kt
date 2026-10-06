package dev.firefly.simpletweaks.util
import dev.firefly.simpletweaks.interfaces.AttackChargeAccessor
import dev.firefly.simpletweaks.mixin.accessor.EntityLivingBaseAccessor
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.MobEffects
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.network.play.server.SPacketEntityProperties
import net.minecraft.util.DamageSource
import net.minecraft.util.EntityDamageSource
import net.minecraft.util.FoodStats
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.MathHelper.sqrt
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.event.entity.player.CriticalHitEvent
import kotlin.random.Random.Default.nextInt

fun EntityPlayer.teleport(
    x: Double = this.posX,
    y: Double = this.posY,
    z: Double = this.posZ,
    update: Boolean = true
) {
    if (update) {
        this.setPositionAndUpdate(x,y,z)
    } else this.setPosition(x,y,z)
}
fun EntityPlayer.tp(
    x: Double = this.posX,
    y: Double = this.posY,
    z: Double = this.posZ,
    update: Boolean = true
) {
    this.teleport(x,y,z,update)
}
fun EntityLivingBase.safeGetCooledAttackStrength(): Float {
    val entity = this as? EntityPlayer
    if (entity !is EntityPlayer) return 0.0f
    return entity.attackCharge
}
fun EntityLivingBase.safeSetCooledAttackStrength(value : Float) {
    val entity = this as? EntityPlayer
    if (entity !is EntityPlayer) return
    entity.attackChargeValue = value.coerceIn(0f,1f)
}

val EntityPlayer.attackCharge: Float
    get() = (this as AttackChargeAccessor).attackCharge

var EntityPlayer.attackChargeValue: Float
    get() = (this as AttackChargeAccessor).attackCharge
set(value) = (this as AttackChargeAccessor).setAttackCharge(value)

fun EntityPlayer.getRandomArmor(): ItemStack {
    val candidates = this.inventory.armorInventory.filter { !it.isEmpty }
    return if (candidates.isEmpty()) ItemStack.EMPTY else candidates[nextInt(candidates.size)]
}

val EntityLivingBase.boots: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.FEET)

val EntityLivingBase.leggings: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.LEGS)

val EntityLivingBase.chestplate: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.CHEST)

val EntityLivingBase.helmet: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.HEAD)
val EntityPlayer.boots: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.FEET)

val EntityPlayer.leggings: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.LEGS)

val EntityPlayer.chestplate: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.CHEST)

val EntityPlayer.helmet: ItemStack
    get() = this.getItemStackFromSlot(EntityEquipmentSlot.HEAD)

fun EntityPlayer.relativeSpeedTo(other: Entity, includeY: Boolean = false): Double {
    val dx = this.motionX - other.motionX
    val dy = this.motionY - other.motionY
    val dz = this.motionZ - other.motionZ
    return (if (includeY) sqrt(dx * dx + dy * dy + dz * dz)
    else sqrt(dx * dx + dz * dz)).toDouble()
}

fun EntityPlayer.speed(includeY: Boolean = false): Double {
    return (if (includeY) sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ)
    else sqrt(motionX * motionX + motionZ * motionZ)).toDouble()
}


val EntityLivingBase.healthRatio: Float
    get() = this.health / this.maxHealth

val EntityLivingBase.lostHealth: Float
    get() = this.maxHealth - this.health

val EntityLivingBase.lostHealthRatio: Float
    get() = (this.maxHealth - this.health) / this.maxHealth

val EntityLivingBase.effectiveHealthRatio: Float
    get() = if (maxHealth <= 0f) 0f else (health + absorptionAmount) / maxHealth

val EntityLivingBase.displayedItem: List<ItemStack>
    get() = listOf(
        this.helmet,
        this.chestplate,
        this.leggings,
        this.boots,
        this.heldItemMainhand,
        this.heldItemOffhand
    ).filter { !it.isEmpty }
fun EntityLivingBase.runPlayerAttack(
    attacker: EntityPlayer,
    rawDamage: Number,
    forceHit: Boolean = true,
    source: DamageSource = DamageSource.causePlayerDamage(attacker),
    lastHitKeepDamagePlayerSource: Boolean = true,
    triggerEvent: Boolean = true,
    ignoreArmorAndPotion: Boolean = false,
    allowCrit: Boolean = true,
    damageCreativePlayer: Boolean = false,
): Float {
    if (!this.isEntityAlive) return 0f
    if (forceHit) this.hurtResistantTime = 0
    if (this.hurtResistantTime != 0) return -1f
    if (this is EntityPlayer && !damageCreativePlayer) {
        if (this.capabilities.isCreativeMode) return -1f
    }
    var damage = rawDamage.toFloat()
    val acc = this as EntityLivingBaseAccessor

    val vanillaCrit = allowCrit && attacker.canCrit()
    val crit: CriticalHitEvent? = ForgeHooks.getCriticalHit(
        attacker,
        this,
        vanillaCrit,
        if (vanillaCrit) 1.5f else 1.0f,
    )
    val isCrit = crit != null
    if (isCrit) {
        damage *= crit.damageModifier
    }

    if (triggerEvent) {
        val attackEvent = net.minecraftforge.event.entity.player.AttackEntityEvent(attacker, this)
        MinecraftForge.EVENT_BUS.post(attackEvent)
        if (attackEvent.isCanceled) return 0f

        val attackLiving = net.minecraftforge.event.entity.living.LivingAttackEvent(this, source, damage)
        MinecraftForge.EVENT_BUS.post(attackLiving)
        if (attackLiving.isCanceled) return 0f

        val hurtEvent = LivingHurtEvent(this, source, damage)
        MinecraftForge.EVENT_BUS.post(hurtEvent)
        if (hurtEvent.isCanceled || hurtEvent.amount <= 0f) return 0f
        damage = hurtEvent.amount
    }

    if (!ignoreArmorAndPotion) {
        damage = acc.invokeApplyArmorCalculations(source, damage)
        damage = acc.invokeApplyPotionDamageCalculations(source, damage)
        if (damage <= 0f) return 0f
    }

    if (triggerEvent) {
        val dmgEvent = LivingDamageEvent(this, source, damage)
        MinecraftForge.EVENT_BUS.post(dmgEvent)
        if (dmgEvent.isCanceled || dmgEvent.amount <= 0f) return 0f
        damage = dmgEvent.amount
    }

    val result = applyDamage(attacker, damage, source, lastHitKeepDamagePlayerSource)

    if (isCrit && result > 0f) {
        attacker.onCriticalHit(this)
        attacker.world.playSound(
            null,
            attacker.posX, attacker.posY, attacker.posZ,
            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
            attacker.soundCategory,
            1.0f, 1.0f,
        )
    }

    return result
}

fun EntityLivingBase.runVanillaAttack(
    attacker: EntityPlayer,
    rawDamage: Float,
    source: DamageSource = DamageSource.causePlayerDamage(attacker),
): Float {
    if (!this.isEntityAlive) return 0f
    return if (this.attackEntityFrom(source, rawDamage)) rawDamage else 0f
}

private fun EntityLivingBase.applyDamage(
    attacker: EntityPlayer,
    finalDamage: Float,
    source: DamageSource,
    keepPlayerSource: Boolean,
): Float {
    var dmg = finalDamage

    val absorption = this.absorptionAmount
    if (absorption > 0f) {
        if (absorption >= dmg) {
            this.absorptionAmount = absorption - dmg
            hurtFeedback(attacker, source, killed = false)
            return dmg
        } else {
            dmg -= absorption
            this.absorptionAmount = 0f
        }
    }

    val before = this.health
    val newHealth = before - dmg
    this.health = newHealth.coerceAtLeast(0f)
    this.hurtResistantTime = this.maxHurtResistantTime
    hurtFeedback(attacker, source, killed = newHealth <= 0f)

    if (newHealth <= 0f) {
        val deathSource = if (keepPlayerSource) DamageSource.causePlayerDamage(attacker) else source
        this.combatTracker.trackDamage(deathSource, before, dmg)
        this.onDeath(deathSource)
        this.setDead()
    }
    return dmg
}

private fun EntityLivingBase.hurtFeedback(
    attacker: EntityPlayer,
    source: DamageSource,
    killed: Boolean,
) {
    val acc = this as EntityLivingBaseAccessor
    val server = !this.world.isRemote

    if (server) {
        val state: Byte = when {
            source is EntityDamageSource && source.isThornsDamage -> 33
            source == DamageSource.DROWN -> 36
            source.isFireDamage -> 37
            else -> 2
        }
        this.world.setEntityState(this, state)
    }

    this.hurtTime = 10
    this.maxHurtTime = 10

    this.setRevengeTarget(attacker)
    this.setLastAttackedEntity(attacker)
    acc.recentlyHit = 100
    acc.attackingPlayer = attacker

    var dx = attacker.posX - this.posX
    var dz = attacker.posZ - this.posZ
    while (dx * dx + dz * dz < 1.0E-4) {
        dx = (Math.random() - Math.random()) * 0.01
        dz = (Math.random() - Math.random()) * 0.01
    }
    this.attackedAtYaw =
        (MathHelper.atan2(dz, dx) * (180.0 / Math.PI) - this.rotationYaw).toFloat()

    if (!killed) this.knockBack(attacker, 0.4f, dx, dz)
    this.velocityChanged = true

    if (server && !killed) acc.invokePlayHurtSound(source)
}

var FoodStats.saturation: Float
    get() = this.saturationLevel
    set(value) = this.setFoodSaturationLevel(value + this.saturationLevel)
fun EntityPlayer.syncAttributes() {
    if (this is EntityPlayerMP) {
        this.connection.sendPacket(
            SPacketEntityProperties(this.entityId, this.attributeMap.allAttributes)
        )
    }
}
fun EntityPlayer.canCrit(): Boolean {
    return this.fallDistance > 0.0F &&
            !this.onGround &&
            !this.isOnLadder &&
            !this.isInWater &&
            !this.isPotionActive(MobEffects.BLINDNESS) &&
            !this.isRiding &&
            !this.isSprinting
}