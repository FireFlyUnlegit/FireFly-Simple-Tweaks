package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.LootEnchantmentGuard;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.EnchantRandomlyLootFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code enchant_randomly} without an {@code options} tag rolls from the whole enchantment registry, so a
 * creative-only enchantment can be rolled onto chest loot. Strip it on the way out.
 *
 * <p>`cancellable = true` is mandatory: without it `setReturnValue` throws
 * `CancellationException: The call method_522 is not cancellable`, which the packet handler swallows as
 * "Failed to handle packet" and the container simply opens with nothing in it.
 */
@Mixin(EnchantRandomlyLootFunction.class)
public abstract class EnchantRandomlyLootFunctionMixin {

    @Inject(method = "process", at = @At("RETURN"), cancellable = true)
    private void simpletweaks$stripCreativeOnly(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(LootEnchantmentGuard.strip(cir.getReturnValue()));
    }
}
