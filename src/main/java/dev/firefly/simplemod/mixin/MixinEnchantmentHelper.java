package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.config.GeneralConfig;
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.WeightedRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Random;

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
    @Redirect(
            method = "buildEnchantmentList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/WeightedRandom;getRandomItem(Ljava/util/Random;Ljava/util/List;)Lnet/minecraft/util/WeightedRandom$Item;"
            ),
            require = 2
    )
    private static WeightedRandom.Item simplemod$weightedPick(
            Random rand,
            List<? extends WeightedRandom.Item> items
    ) {
        int total = 0;
        for (WeightedRandom.Item item : items) {
            total += simplemod$weightOf(item);
        }
        if (total <= 0) return items.get(0);

        int pick = rand.nextInt(total);
        for (WeightedRandom.Item item : items) {
            pick -= simplemod$weightOf(item);
            if (pick < 0) return item;
        }
        return items.get(items.size() - 1);
    }

    private static int simplemod$weightOf(WeightedRandom.Item item) {
        if (item instanceof EnchantmentData) {
            Enchantment ench = ((EnchantmentData) item).enchantment;
            if (ench instanceof ModEnchantments) {
                return ((ModEnchantments) ench).getCategory().getWeight();
            }
        }
        return item.itemWeight;
    }
}