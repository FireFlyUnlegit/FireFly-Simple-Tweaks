package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.ShotWeapon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code mainHandStack} report the **firing** weapon while a hit is being resolved.
 *
 * <p>This is the single seam that makes every damage handler correct without touching them: the arrow
 * snapshot lives in {@link ShotWeapon}, the damage window is opened by {@code LivingEntityDamageWindowMixin},
 * and here the getter answers with the snapshot. Outside that window — HUD, inventory, animations,
 * tooltips, item damage — nothing changes, because {@link ShotWeapon#getOverriddenMainHand} returns
 * {@code null} unless a window is open and the attacker is this entity.
 *
 * <p>`LivingEntity#getMainHandStack()` is concrete in 1.21.1 and not overridden by `PlayerEntity` or
 * `MobEntity` (`javap`), so one injection covers all of them.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMainHandSnapshotMixin {

    @Inject(method = "getMainHandStack", at = @At("RETURN"), cancellable = true)
    private void simpletweaks$shotWeaponMainHand(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack override = ShotWeapon.INSTANCE.overriddenMainHand((LivingEntity) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
