package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.EnchantPresence;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the entity's {@code EnchantPresence} snapshot when equipment changes.
 *
 * <p>{@code equipStack} is abstract and both overrides funnel through {@code onEquipStack}, so this is the
 * shared leaf. {@code RETURN} (not {@code HEAD}) so a query inside the hook cannot rebuild from the
 * outgoing stack and cache that.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityOnEquipStackMixin {

    @Inject(method = "onEquipStack", at = @At("RETURN"))
    private void simpletweaks$dropEnchantPresence(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack,
                                                  CallbackInfo ci) {
        EnchantPresence.invalidate((LivingEntity) (Object) this);
    }
}
