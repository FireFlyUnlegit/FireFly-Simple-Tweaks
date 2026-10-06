package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ChargeBoost;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's `ArrowLooseEvent` seam, and adds the draw-power hook that `fast_bow` needs.
 *
 * <h2>1.12.2 original (the event)</h2>
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
 *
 * <h2>The second hook: making a shot count as fully drawn sooner</h2>
 * `fast_bow` has no declarative route in 1.21 (there is no "draw faster" component), and the event
 * cannot carry it either: `ArrowLooseEvent.charge` is a snapshot vanilla never reads back, so
 * modifying it would change nothing. Vanilla recomputes the shot's power itself as
 *
 * <pre>
 *   offset 34:  invokevirtual  getMaxUseTime(ItemStack, LivingEntity)I
 *   offset 44:  invokestatic   getPullProgress(I)F          &lt;-- hooked here
 * </pre>
 *
 * so {@code simpletweaks$drawSpeed} scales the argument of that one call. Verified with `javap -c`
 * that `getPullProgress` is **static** and appears **exactly once** in `onStoppedUsing`, which is why
 * this `@ModifyArg` needs no `slice` or `ordinal` — a deliberate contrast with
 * `AnvilScreenHandlerMixin`, where the same literal occurs three times and an ordinal was mandatory.
 *
 * <p>Ordering is guaranteed by the bytecode, not by injection priority: the `@Inject` at HEAD runs
 * first and posts the event (a handler sets {@link ChargeBoost#value}), and the `@ModifyArg` runs
 * later, at offset 44, in the same execution of the method.
 */
@Mixin(BowItem.class)
public abstract class BowItemArrowLooseMixin {

    @Shadow
    public abstract int getMaxUseTime(ItemStack stack, LivingEntity user);

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$arrowLoose(ItemStack stack, World world, LivingEntity user,
                                         int remainingUseTicks, CallbackInfo ci) {
        // Reset BEFORE posting: only a handler is allowed to set this, so a release from a bow
        // without the enchantment must not inherit the previous shot's multiplier. The early return
        // for non-players is below this on purpose.
        ChargeBoost.value = 1.0f;
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

    /**
     * Scales the drawn-tick count handed to vanilla's `getPullProgress`.
     *
     * <p>`getPullProgress` clamps its result to `1.0`, so a boost can only ever bring a partial draw
     * up to ordinary full-draw power; it cannot overcharge a shot. Consumes the value on the way out
     * for the same reason the `@Inject` resets it on the way in.
     */
    @ModifyArg(
            method = "onStoppedUsing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BowItem;getPullProgress(I)F")
    )
    private int simpletweaks$drawSpeed(int useTicks) {
        float boost = ChargeBoost.value;
        ChargeBoost.value = 1.0f;
        return boost == 1.0f ? useTicks : Math.round(useTicks * boost);
    }
}
