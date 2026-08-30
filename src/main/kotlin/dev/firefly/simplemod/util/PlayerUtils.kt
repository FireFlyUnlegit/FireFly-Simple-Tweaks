package dev.firefly.simplemod.util
import net.minecraft.entity.player.EntityPlayer

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