package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.config.GeneralConfig;
import net.minecraft.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelper {

    @ModifyConstant(
            method = "calcItemStackEnchantability",
            constant = @Constant(intValue = 15),
            require = 1
    )
    private static int modifyMaxPower(int original) {
        if (!GeneralConfig.disableEnchantmentTableLimit) return 15;
        return GeneralConfig.maxEnchantmentPower;
    }
}