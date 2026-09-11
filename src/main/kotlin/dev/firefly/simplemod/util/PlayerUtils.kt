package dev.firefly.simplemod.util
import dev.firefly.simplemod.interfaces.AttackChargeAccessor
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
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


