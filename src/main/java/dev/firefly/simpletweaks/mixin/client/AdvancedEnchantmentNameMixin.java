package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.SimpleTweaks;
import dev.firefly.simpletweaks.client.AdvancedNames;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Advanced enchantments read `进阶·Xxx`; the base half keeps its own rarity colour. */
@Mixin(Enchantment.class)
public abstract class AdvancedEnchantmentNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private static void simpletweaks$advancedName(RegistryEntry<Enchantment> enchantment, int level,
            CallbackInfoReturnable<Text> cir) {
        RegistryKey<Enchantment> key = enchantment.getKey().orElse(null);
        if (key == null || !key.getValue().getNamespace().equals(SimpleTweaks.MOD_ID)) return;
        String base = AdvancedNames.basePath(key.getValue().getPath());
        if (base == null) return;
        cir.setReturnValue(AdvancedNames.name(base, level, enchantment.value().getMaxLevel()));
    }
}