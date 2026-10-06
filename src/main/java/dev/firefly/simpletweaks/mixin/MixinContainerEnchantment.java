package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Int;

import java.util.Random;

@Mixin(ContainerEnchantment.class)
public abstract class MixinContainerEnchantment {

    @Shadow @Final public IInventory tableInventory;

    @Redirect(
            method = "onCraftMatrixChanged",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isItemEnchantable()Z")
    )
    private boolean firefly$allowReenchant(ItemStack stack) {
        boolean result = false;
        if (stack.isEmpty()) result = false;
        else if (stack.getItem() == Items.ENCHANTED_BOOK) result = true;
        else if (stack.isItemEnchanted()) result = true;
        else result = stack.isItemEnchantable();
        return result;
    }

    @Redirect(
            method = "onCraftMatrixChanged",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentHelper;calcItemStackEnchantability(Ljava/util/Random;IILnet/minecraft/item/ItemStack;)I")
    )
    private int firefly$forceEnchantability(Random rand, int enchantNum, int power, ItemStack stack) {
        int result = EnchantmentHelper.calcItemStackEnchantability(rand, enchantNum, power, stack);
        if (result > 0) {
            return result;
        }

        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            int maxPower = GeneralConfig.disableEnchantmentTableLimit
                    ? Math.min(GeneralConfig.maxEnchantmentPower, Int.MaxValue())
                    : 15;            int j = rand.nextInt(8) + 1 + (power >> 1) + rand.nextInt(power + 1);
            if (power > maxPower) power = maxPower;

            if (enchantNum == 0) result = Math.max(j / 3, 1);
            else if (enchantNum == 1) result = j * 2 / 3 + 1;
            else result = Math.max(j, power * 2);
        }
        return result;
    }

    @Redirect(
            method = "enchantItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;", ordinal = 0)
    )
    private Item firefly$treatEnchantedBookAsBook(ItemStack stack) {
        Item item = stack.getItem();
        Item result = item;
        if (item == Items.ENCHANTED_BOOK) result = Items.BOOK;
        return result;
    }

    @Inject(
            method = "enchantItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;onEnchant(Lnet/minecraft/item/ItemStack;I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void firefly$clearEnchants(EntityPlayer player, int id, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = this.tableInventory.getStackInSlot(0);
        if (stack.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        tag.removeTag("ench");
    }
}