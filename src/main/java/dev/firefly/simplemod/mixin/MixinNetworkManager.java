package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.event.EventDispatcher;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!EventDispatcher.INSTANCE.onPacketSend(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (!EventDispatcher.INSTANCE.onPacketReceive(packet)) {
            ci.cancel();
        }
    }
}