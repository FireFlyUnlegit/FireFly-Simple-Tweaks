package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.event.EventDispatcher;
import dev.firefly.simplemod.extraforgeapi.extraevents.SlowDownEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInput;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"UnresolvedMixinReference"})
@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onUpdate(CallbackInfo ci) {
        EventDispatcher.INSTANCE.onPlayerUpdate();
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void onMotionPre(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        EventDispatcher.INSTANCE.onMotionUpdate(player.posX, player.posY, player.posZ, player.onGround, true);
    }

    @Inject(method = "onLivingUpdate", at = @At("RETURN"))
    private void onMotionPost(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        EventDispatcher.INSTANCE.onMotionUpdate(player.posX, player.posY, player.posZ, player.onGround, false);
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        if (!EventDispatcher.INSTANCE.onJump()) {
            ci.cancel();
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        EventDispatcher.INSTANCE.onAttack(target);
    }

    @Inject(method = "setSprinting", at = @At("HEAD"))
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        EventDispatcher.INSTANCE.onSprint(sprinting);
    }

    @Inject(method = "getStepHeight", at = @At("RETURN"), cancellable = true)
    private void onGetStepHeight(CallbackInfoReturnable<Float> cir) {
        float result = EventDispatcher.INSTANCE.onStep(cir.getReturnValue());
        cir.setReturnValue(result);
    }
    @Inject(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V",
                    shift = At.Shift.AFTER
            )
    )
    private void slowDown$apply(CallbackInfo ci) {
        EntityPlayerSP self = (EntityPlayerSP)(Object)this;
        if (self.isRiding()) return;
        if (self.capabilities.isFlying) return;

        MovementInput input = self.movementInput;

        SlowDownEvent.Type type;
        if (self.isHandActive()) {
            ItemStack active = self.getActiveItemStack();
            EnumAction action = active.isEmpty() ? null : active.getItemUseAction();
            if (action == EnumAction.BLOCK) type = SlowDownEvent.Type.BLOCKING;
            else if (action == EnumAction.BOW) type = SlowDownEvent.Type.BOW;
            else type = SlowDownEvent.Type.EATING;
        } else if (self.isSneaking()) {
            type = SlowDownEvent.Type.SNEAK;
        } else {
            return;
        }

        SlowDownEvent event = new SlowDownEvent(self, type, input.moveForward, input.moveStrafe, 0.3F);
        MinecraftForge.EVENT_BUS.post(event);

        if (event.isModified()) {
            input.moveForward = event.getForward();
            input.moveStrafe  = event.getStrafe();
        }
    }
}