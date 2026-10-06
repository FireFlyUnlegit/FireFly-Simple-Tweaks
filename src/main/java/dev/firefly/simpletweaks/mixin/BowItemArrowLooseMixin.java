package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's `ArrowLooseEvent` seam.
 *
 * <h2>1.12.2 original</h2>
 * Forge fired `ArrowLooseEvent` from `ItemBow#onPlayerStoppedUsing` after the ammo lookup and before
 * the arrows were created, with `charge = getMaxItemUseDuration(stack) - timeLeft`. Handlers could
 * cancel it to replace the shot.
 *
 * <h2>Yarn 1.21.1 ground truth</h2>
 * `BowItem#onStoppedUsing(ItemStack, World, LivingEntity, int)` is the direct analogue — it is where
 * the ammo is resolved (`player.getProjectileType(stack)`), the charge is computed
 * (`getMaxUseTime(stack, user) - remainingUseTicks`), and the shot is fired
 * (`shootAll(...)`). Injecting at **HEAD** therefore reproduces Forge's position: the bow has been
 * released, nothing has been resolved or fired yet, and cancelling prevents the vanilla shot
 * entirely — which is what the multishot handler needs, since it builds its own arrows.
 *
 * <p>`BowItem` is the only class declaring `onStoppedUsing` in this chain (verified: it is declared by
 * `BowItem` and does not exist on `Item` for ranged weapons), so there is no subclass override that
 * could route around this injection point.
 *
 * <p>`getMaxUseTime` is shadowed rather than reimplemented, so a modded bow with a custom draw time
 * yields a correct charge — 1.12.2 read the same value through `getMaxItemUseDuration`.
 */
@Mixin(BowItem.class)
public abstract class BowItemArrowLooseMixin {

    @Shadow
    public abstract int getMaxUseTime(ItemStack stack, LivingEntity user);

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$arrowLoose(ItemStack stack, World world, LivingEntity user,
                                         int remainingUseTicks, CallbackInfo ci) {
        if (!(user instanceof PlayerEntity player)) {
            return;
        }
        int charge = this.getMaxUseTime(stack, user) - remainingUseTicks;
        ArrowLooseEvent event = new ArrowLooseEvent(player, stack, charge);
        ForgeEventBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
