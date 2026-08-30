package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.event.EventDispatcher;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"UnresolvedMixinReference", "InvalidInjectorMethodSignature"})
@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    // Entity.onUpdate() - 客户端玩家每 tick 更新
    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onUpdate(CallbackInfo ci) {
        EventDispatcher.INSTANCE.onPlayerUpdate();
    }

    // EntityLivingBase.onLivingUpdate() - 在 HEAD 处注入（运动前）
    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void onMotionPre(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        EventDispatcher.INSTANCE.onMotionUpdate(player.posX, player.posY, player.posZ, player.onGround, true);
    }

    // EntityLivingBase.onLivingUpdate() - 在 RETURN 处注入（运动后）
    @Inject(method = "onLivingUpdate", at = @At("RETURN"))
    private void onMotionPost(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        EventDispatcher.INSTANCE.onMotionUpdate(player.posX, player.posY, player.posZ, player.onGround, false);
    }

    // jump()
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        if (!EventDispatcher.INSTANCE.onJump()) {
            ci.cancel();
        }
    }

    // attackEntity(Entity)
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        EventDispatcher.INSTANCE.onAttack(target);
    }

    // setSprinting(boolean)
    @Inject(method = "setSprinting", at = @At("HEAD"))
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        EventDispatcher.INSTANCE.onSprint(sprinting);
    }

    // getStepHeight()
    @Inject(method = "getStepHeight", at = @At("RETURN"), cancellable = true)
    private void onGetStepHeight(CallbackInfoReturnable<Float> cir) {
        float result = EventDispatcher.INSTANCE.onStep(cir.getReturnValue());
        cir.setReturnValue(result);
    }
}