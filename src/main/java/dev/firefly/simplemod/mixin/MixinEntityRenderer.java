package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.modules.NoFov;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V"))
    private void onRender2D(float partialTicks, long nanoTime, CallbackInfo ci) {
        dev.firefly.simplemod.core.event.EventDispatcher.INSTANCE.onRender2D(partialTicks);
    }

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V"))
    private void onRender3D(float partialTicks, long nanoTime, CallbackInfo ci) {
        dev.firefly.simplemod.core.event.EventDispatcher.INSTANCE.onRender3D(partialTicks);
    }

    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void onGetFOVModifier(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (NoFov.INSTANCE.isEnabled()) {
            float customFov = NoFov.INSTANCE.getFov().get();
            cir.setReturnValue(customFov);
        }
    }

}