package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.util.SwordBlocking;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererBlockingMixin {

    @Inject(method = "getArmPose", at = @At("RETURN"), cancellable = true)
    private static void simpletweaks$blockingArmPose(
        AbstractClientPlayerEntity player,
        Hand hand,
        CallbackInfoReturnable<BipedEntityModel.ArmPose> cir
    ) {
        if (SwordBlocking.isBlockingVisual(player)) {
            cir.setReturnValue(BipedEntityModel.ArmPose.BLOCK);
        }
    }
}
