package dev.firefly.simplemod.util

import net.minecraft.entity.Entity
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.WorldServer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

object ParticleUtil {
    fun spawnCritParticlesServer(world: WorldServer, target: Entity, count: Int = 15) {
        world.spawnParticle(
            EnumParticleTypes.CRIT_MAGIC,
            target.posX, target.posY + target.height / 2, target.posZ,
            count,
            0.5, 0.5, 0.5,
            0.0
        )
    }

    @SideOnly(Side.CLIENT)
    fun spawnCritParticlesClient(target: Entity) {
        net.minecraft.client.Minecraft.getMinecraft().effectRenderer.emitParticleAtEntity(
            target,
            EnumParticleTypes.CRIT_MAGIC
        )
    }
}