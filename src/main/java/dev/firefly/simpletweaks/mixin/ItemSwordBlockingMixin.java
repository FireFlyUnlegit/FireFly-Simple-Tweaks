package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.SwordBlocking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes an enchanted sword behave like a shield for use action, use duration and right-click. */
@Mixin(Item.class)
public abstract class ItemSwordBlockingMixin {

    @Inject(method = "getUseAction(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/util/UseAction;",
            at = @At("HEAD"), cancellable = true)
    private void simpletweaks$swordBlockUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (SwordBlocking.isBlockingSword(stack)) {
            cir.setReturnValue(UseAction.BLOCK);
        }
    }

    @Inject(method = "getMaxUseTime(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/LivingEntity;)I",
            at = @At("HEAD"), cancellable = true)
    private void simpletweaks$swordBlockMaxUseTime(ItemStack stack, LivingEntity user,
                                                   CallbackInfoReturnable<Integer> cir) {
        if (SwordBlocking.isBlockingSword(stack)) {
            cir.setReturnValue(SwordBlocking.BLOCK_USE_TIME);
        }
    }

    @Inject(method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
            at = @At("HEAD"), cancellable = true)
    private void simpletweaks$swordBlockUse(World world, PlayerEntity user, Hand hand,
                                            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (!SwordBlocking.isBlockingSword(stack)) {
            return;
        }
        user.setCurrentHand(hand);
        cir.setReturnValue(TypedActionResult.consume(stack));
    }
}
