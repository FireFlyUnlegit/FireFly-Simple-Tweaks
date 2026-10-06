package dev.firefly.simpletweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantFastBowHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client half of `fast_bow`: makes the **drawn-back animation** keep up with the faster shot.
 *
 * <h2>Why the first implementation was invisible</h2>
 * The first version only scaled the argument of `BowItem#getPullProgress` inside `onStoppedUsing`,
 * i.e. the shot's power. That is real, but it is not what "draws faster" looks like: the bow in hand
 * kept taking vanilla's 20 ticks to reach the fully-drawn pose, because `HeldItemRenderer` never
 * calls `getPullProgress` at all — it reads `getItemUseTimeLeft()`.
 *
 * <h2>Yarn 1.21.1 ground truth (bytecode, per MIGRATION.md 铁律二)</h2>
 * <pre>
 *   LivingEntity#getItemUseTimeLeft()I          aload_0; getfield itemUseTimeLeft:I; ireturn
 *                                               -- a plain field getter, and an override audit shows
 *                                                  NO subclass redeclares it (PlayerEntity,
 *                                                  AbstractClientPlayerEntity, ClientPlayerEntity,
 *                                                  ServerPlayerEntity all inherit it)
 *   HeldItemRenderer                            reads getItemUseTimeLeft() at 7 call sites
 *   LivingEntity#stopUsingItem                  calls onStoppedUsing(world, this, getItemUseTimeLeft())
 *   LivingEntity#getItemUseTime()               getMaxUseTime(activeItem) - getItemUseTimeLeft()
 * </pre>
 *
 * One number feeds the animation and the shot, so scaling the getter would fix both at once — but it
 * is deliberately **not** done that way.
 *
 * <h2>Why this hook bails out on the server side</h2>
 * The class is shared: in singleplayer the client and the integrated server are the same JVM, so a
 * mixin listed under `client` still fires for the server's calls. The shot's power is boosted once by
 * {@link BowItemArrowLooseMixin}; if this hook also scaled the server's `remainingUseTicks`, the two
 * would compound to `boost²` on the server only, and singleplayer would hit harder than multiplayer
 * and harder than the animation implies. Hence the `world.isClient()` guard: **client owns the
 * animation, server owns the power, each applies the multiplier exactly once.**
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDrawSpeedMixin {

    @ModifyReturnValue(method = "getItemUseTimeLeft", at = @At("RETURN"))
    private int simpletweaks$fastBowDraw(int remainingTicks) {
        LivingEntity self = (LivingEntity) (Object) this;

        // See the class KDoc: the server path is BowItemArrowLooseMixin's job.
        if (!self.getWorld().isClient()) {
            return remainingTicks;
        }

        ItemStack active = self.getActiveItem();
        if (active.isEmpty()) {
            return remainingTicks;
        }

        float boost = EnchantFastBowHandler.chargeBoost(active);
        if (boost == 1.0F) {
            return remainingTicks;
        }

        // Recompose the drawn-tick count as if the bow had been held for `boost` times as long, then
        // convert back to "remaining". Clamped at 0 so a very long hold cannot produce a negative
        // remaining time (vanilla's own stop condition reads the field, not this getter, so the
        // clamp is only about not handing nonsense to the consumers).
        int max = active.getMaxUseTime(self);
        int drawn = max - remainingTicks;
        return Math.max(0, max - Math.round(drawn * boost));
    }
}
