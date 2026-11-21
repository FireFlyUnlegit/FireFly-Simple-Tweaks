package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.LootEnchantmentGuard;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.EnchantWithLevelsLootFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code enchant_with_levels} without an {@code options} tag rolls from the whole enchantment registry, so a
 * creative-only enchantment can be rolled onto chest loot. Strip it on the way out.
 */
@Mixin(EnchantWithLevelsLootFunction.class)
public abstract class EnchantWithLevelsLootFunctionMixin {

    @Inject(method = "process", at = @At("RETURN"))
    private void simpletweaks$stripCreativeOnly(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(LootEnchantmentGuard.strip(cir.getReturnValue()));
    }
}
