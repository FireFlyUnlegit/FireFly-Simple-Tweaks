package dev.firefly.simpletweaks.util
import dev.firefly.simpletweaks.interfaces.AttackChargeAccessor
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.util.DamageSource
import net.minecraft.util.FoodStats
import net.minecraft.util.math.MathHelper.sqrt
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
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
    rawDamage: Float,
    forceHit: Boolean = true,
    source: DamageSource = DamageSource.causePlayerDamage(attacker),
    lastHitKeepDamagePlayerSource: Boolean = true,
    triggerEvent: Boolean = true,
): Float {
    if (!this.isEntityAlive) return 0f
    if (forceHit) this.hurtResistantTime = 0
    if (this.hurtResistantTime != 0) return -1f

    var damage = rawDamage

    if (triggerEvent) {
        val hurtEvent = LivingHurtEvent(this, source, rawDamage)
        MinecraftForge.EVENT_BUS.post(hurtEvent)
        if (hurtEvent.isCanceled || hurtEvent.amount <= 0f) return 0f

        val dmgEvent = LivingDamageEvent(this, source, hurtEvent.amount)
        MinecraftForge.EVENT_BUS.post(dmgEvent)
        if (dmgEvent.isCanceled || dmgEvent.amount <= 0f) return 0f

        damage = dmgEvent.amount
    }

    return applyDamage(attacker, damage, source, lastHitKeepDamagePlayerSource)
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
            this.hurtTime = 10
            this.maxHurtTime = 10
            this.attackedAtYaw = attacker.rotationYaw
            return dmg
        } else {
            dmg -= absorption
            this.absorptionAmount = 0f
        }
    }

    val newHealth = this.health - dmg
    this.hurtTime = 10
    this.maxHurtTime = 10
    this.attackedAtYaw = attacker.rotationYaw

    if (newHealth <= 0f) {
        this.health = 0f
        val deathSource = if (keepPlayerSource) DamageSource.causePlayerDamage(attacker) else source
        this.onDeath(deathSource)
        this.setDead()
        this.isDead = true
    } else {
        this.health = newHealth
    }

    return dmg
}

var FoodStats.saturation: Float
    get() = this.saturationLevel
    set(value) = this.setFoodSaturationLevel(value + this.saturationLevel)
