package dev.firefly.simplemod.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityPlayer.class)
public class MixinEntityPlayer {
    @Redirect(
            method = "attackTargetEntityWithCurrentItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldServer;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDIDDDD[I)V"
            )
    )
    private void onSpawnDamageParticle(
            WorldServer instance,
            EnumParticleTypes particleType,
            double xCoord,
            double yCoord,
            double zCoord,
            int numberOfParticles,
            double xOffset,
            double yOffset,
            double zOffset,
            double particleSpeed,
            int[] particleArguments
    ) {
        // 方法体为空 = 吞掉这个调用，不生成任何粒子
    }
}