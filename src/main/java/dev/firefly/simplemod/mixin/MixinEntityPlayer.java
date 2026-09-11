package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.config.GeneralConfig;
import dev.firefly.simplemod.interfaces.AttackChargeAccessor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityPlayer.class)
public class MixinEntityPlayer implements AttackChargeAccessor {

    @Unique
    private float simplemod$attackCharge = 1.0F;

    @Override
    public float getAttackCharge() {
        return this.simplemod$attackCharge;
    }

    @Override
    public void setAttackCharge(float value) {
        this.simplemod$attackCharge = value;
    }

    @Redirect(
            method = "attackTargetEntityWithCurrentItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;getCooledAttackStrength(F)F"
            )
    )
    private float captureAttackCharge(EntityPlayer self, float adjustTicks) {
        float value = self.getCooledAttackStrength(adjustTicks);
        ((AttackChargeAccessor) self).setAttackCharge(value);
        return value;
    }

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
        if (GeneralConfig.cancelVanillaDamageIndicator) {
            return;
        }
        instance.spawnParticle(
                particleType,
                xCoord,
                yCoord,
                zCoord,
                numberOfParticles,
                xOffset,
                yOffset,
                zOffset,
                particleSpeed,
                particleArguments
        );
    }
}