package dev.firefly.simpletweaks.mixin;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mixin(ContainerEnchantment.class)
public abstract class MixinContainerEnchantmentPreview {

    @Shadow public IInventory tableInventory;
    @Shadow public int xpSeed;
    @Shadow public int[] enchantLevels;

    @Unique private List<EnchantmentData> simpletweaks$p0;
    @Unique private List<EnchantmentData> simpletweaks$p1;
    @Unique private List<EnchantmentData> simpletweaks$p2;

    @Unique
    private List<EnchantmentData> simpletweaks$get(int i) {
        if (i == 0) return simpletweaks$p0;
        if (i == 1) return simpletweaks$p1;
        return simpletweaks$p2;
    }

    @Unique
    private void simpletweaks$set(int i, List<EnchantmentData> list) {
        if (i == 0) simpletweaks$p0 = list;
        else if (i == 1) simpletweaks$p1 = list;
        else simpletweaks$p2 = list;
    }

    @Inject(method = "onCraftMatrixChanged", at = @At("TAIL"))
    private void simpletweaks$cachePreview(IInventory inv, CallbackInfo ci) {
        ItemStack stack = this.tableInventory.getStackInSlot(0);

        if (stack.isEmpty()) {
            simpletweaks$p0 = null;
            simpletweaks$p1 = null;
            simpletweaks$p2 = null;
            return;
        }

        for (int i = 0; i < 3; i++) {
            if (this.enchantLevels[i] <= 0) {
                simpletweaks$set(i, null);
                continue;
            }

            Random localRand = new Random(this.xpSeed + i);
            List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(
                    localRand, stack, this.enchantLevels[i], false);

            if (stack.getItem() == Items.BOOK && list.size() > 1) {
                list.remove(localRand.nextInt(list.size()));
            }

            simpletweaks$set(i, list);
        }
    }

    @Redirect(
            method = "enchantItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/inventory/ContainerEnchantment;getEnchantmentList(Lnet/minecraft/item/ItemStack;II)Ljava/util/List;"
            )
    )
    private List<EnchantmentData> simpletweaks$useCached(
            ContainerEnchantment self, ItemStack stack, int slot, int level
    ) {
        List<EnchantmentData> cached = simpletweaks$get(slot);
        if (cached != null) return new ArrayList<>(cached);
        return new ArrayList<>();
    }
}