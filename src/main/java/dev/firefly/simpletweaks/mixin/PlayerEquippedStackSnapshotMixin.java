package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.ShotWeapon;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Companion to {@code LivingEntityMainHandSnapshotMixin} for callers that bypass `mainHandStack` and read
 * `getEquippedStack(EquipmentSlot.MAINHAND)` directly.
 *
 * <p>Only {@link EquipmentSlot#MAINHAND} is touched — armour, off hand and every other slot keep reporting
 * the real equipment, which matters because armour-derived numbers (element affinity, ward) must stay
 * correct for the hit that is being resolved.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEquippedStackSnapshotMixin {

    @Inject(method = "getEquippedStack", at = @At("RETURN"), cancellable = true)
    private void simpletweaks$shotWeaponEquipped(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != EquipmentSlot.MAINHAND) {
            return;
        }
        ItemStack override = ShotWeapon.INSTANCE.overriddenMainHand((PlayerEntity) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
