package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.util.SwordBlocking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererBlockingMixin {

    @Inject(
        method = "renderFirstPersonItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            shift = At.Shift.BEFORE,
            ordinal = 1
        )
    )
    private void simpletweaks$applySwordBlockPose(
        AbstractClientPlayerEntity player,
        float tickDelta,
        float pitch,
        Hand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
        if (hand != player.getActiveHand()) return;
        if (!SwordBlocking.isBlockingSword(stack)) return;
        if (!SwordBlocking.isBlocking(player)) return;

        Arm usingArm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = usingArm == Arm.RIGHT ? 1.0F : -1.0F;
        if (swingProgress > 0.0F) {
            float swingY = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + swingY * -20.0F)));
            float swingXZ = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * swingXZ * -20.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingXZ * -80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
        }
        matrices.translate(side * -0.14F, 0.15F, 0.1F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 13.37F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 78.05F));
    }

    @Inject(method = "resetEquipProgress", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$keepBlockPoseEquipProgress(Hand hand, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!client.player.isUsingItem()) return;
        if (client.player.getActiveHand() != hand) return;
        if (!SwordBlocking.isBlockingSword(client.player.getStackInHand(hand))) return;
        ci.cancel();
    }
}
