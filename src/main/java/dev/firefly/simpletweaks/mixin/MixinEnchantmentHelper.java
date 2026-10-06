package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelper {

    @ModifyConstant(
            method = "calcItemStackEnchantability",
            constant = @Constant(intValue = 15)
    )
    private static int modifyMaxPower(int original) {
        return GeneralConfig.disableEnchantmentTableLimit ? GeneralConfig.maxEnchantmentPower : 15;
    }
    @Redirect(
            method = "buildEnchantmentList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/Item;getItemEnchantability(Lnet/minecraft/item/ItemStack;)I"
            )
    )
    private static int firefly$bookEnchantability(Item item, ItemStack stack) {
        int result = item.getItemEnchantability(stack);
        if (result <= 0 && stack.getItem() == Items.ENCHANTED_BOOK) {
            return 15;
        }
        return result;
    }
    @Redirect(
            method = "buildEnchantmentList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/WeightedRandom;getRandomItem(Ljava/util/Random;Ljava/util/List;)Lnet/minecraft/util/WeightedRandom$Item;"
            )
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
    @Inject(method = "getEnchantmentDatas", at = @At("HEAD"), cancellable = true)
    private static void firefly$getEnchantmentDatas(
            int level, ItemStack stack, boolean allowTreasure,
            CallbackInfoReturnable<List<EnchantmentData>> cir
    ) {
        if (stack.getItem() != Items.ENCHANTED_BOOK) return;

        List<EnchantmentData> list = new ArrayList<>();
        for (Enchantment ench : Enchantment.REGISTRY) {
            if (ench.isTreasureEnchantment() && !allowTreasure) continue;
            if (!ench.isAllowedOnBooks()) continue;

            for (int i = ench.getMaxLevel(); i >= ench.getMinLevel(); i--) {
                if (level >= ench.getMinEnchantability(i) && level <= ench.getMaxEnchantability(i)) {
                    list.add(new EnchantmentData(ench, i));
                    break;
                }
            }
        }

        cir.setReturnValue(list);
    }
    @Inject(method = "buildEnchantmentList", at = @At("RETURN"), cancellable = true)
    private static void simpletweaks$ensureMinEnchants(
            Random random, ItemStack stack, int level, boolean allowTreasure,
            CallbackInfoReturnable<List<EnchantmentData>> cir
    ) {
        if (stack.isEmpty()) return;

        List<EnchantmentData> listBefore = cir.getReturnValue();
        int sizeBefore = listBefore == null ? -1 : listBefore.size();
        int minEnchants = level / 15;
        if (minEnchants <= 0) return;

        List<EnchantmentData> list = cir.getReturnValue();
        if (list == null) {
            list = new ArrayList<>();
            cir.setReturnValue(list);
        }
        if (list.size() >= minEnchants) return;

        boolean isBook = stack.getItem() == Items.BOOK || stack.getItem() == Items.ENCHANTED_BOOK;

        Set<Enchantment> used = new HashSet<>();
        for (EnchantmentData d : list) used.add(d.enchantment);

        int attempts = 0;
        while (list.size() < minEnchants && attempts < 30) {
            attempts++;

            List<Enchantment> candidates = new ArrayList<>();
            for (Enchantment ench : Enchantment.REGISTRY) {
                if (used.contains(ench)) continue;
                if (ench.isTreasureEnchantment() && !allowTreasure) continue;
                if (level < ench.getMinEnchantability(1)) continue;

                if (isBook) {
                    if (!ench.isAllowedOnBooks()) continue;
                } else {
                    if (!ench.canApply(stack)) continue;
                }

                boolean conflict = false;
                for (Enchantment u : used) {
                    if (!ench.isCompatibleWith(u)) { conflict = true; break; }
                }
                if (conflict) continue;

                candidates.add(ench);
            }
            if (candidates.isEmpty()) {
                break;
            }

            Enchantment picked = simpletweaks$weightedEnchantPick(random, candidates);
            if (picked == null) break;

            int lvl = 1 + random.nextInt(picked.getMaxLevel());
            list.add(new EnchantmentData(picked, lvl));
            used.add(picked);
        }

    }

    private static Enchantment simpletweaks$weightedEnchantPick(Random random, List<Enchantment> candidates) {
        int total = 0;
        for (Enchantment e : candidates) {
            total += simpletweaks$weightOfEnchant(e);
        }
        if (total <= 0) return candidates.get(random.nextInt(candidates.size()));

        int pick = random.nextInt(total);
        for (Enchantment e : candidates) {
            pick -= simpletweaks$weightOfEnchant(e);
            if (pick < 0) return e;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static int simpletweaks$weightOfEnchant(Enchantment ench) {
        if (ench instanceof ModEnchantments) {
            return ((ModEnchantments) ench).getCategory().getWeight();
        }
        return ench.getRarity().getWeight();
    }
}