package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.event.EventDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        EventDispatcher.INSTANCE.onTick();
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void onDisplayGuiScreen(net.minecraft.client.gui.GuiScreen screen, CallbackInfo ci) {
        if (!EventDispatcher.INSTANCE.onGuiOpen(screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void onLoadWorld(WorldClient world, CallbackInfo ci) {
        EventDispatcher.INSTANCE.onWorldChange();
    }
}