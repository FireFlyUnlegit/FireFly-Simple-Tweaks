package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import dev.firefly.simpletweaks.disenchanter.DisenchanterLogic;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerRepair.class)
public abstract class MixinContainerRepair {

    @Shadow @Final private IInventory outputSlot;
    @Shadow @Final private IInventory inputSlots;
    @Shadow public int maximumCost;

    @ModifyConstant(
            method = "updateRepairOutput",
            constant = @Constant(intValue = 40)
    )
    private int modifyMaxAnvilCost(int original) {
        if (GeneralConfig.disableAnvilCostLimit) {
            return GeneralConfig.maxAnvilCost;
        }
        return original;
    }

    @Redirect(
            method = "updateRepairOutput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/Enchantment;isCompatibleWith(Lnet/minecraft/enchantment/Enchantment;)Z"
            )
    )
    private boolean safeIsCompatibleWith(Enchantment self, Enchantment other) {
        if (self == null || other == null) return false;
        return self.isCompatibleWith(other);
    }

    @ModifyVariable(
            method = "updateRepairOutput",
            name = "i",
            at = @At(value = "STORE", ordinal = 0)
    )
    private int modifyVariableI(int original) {
        if (GeneralConfig.disableAnvilCostLimit && original == 40) {
            return 0;
        }
        return original;
    }

    @Inject(method = "updateRepairOutput", at = @At("HEAD"), cancellable = true)
    private void firefly$disenchant(CallbackInfo ci) {
        if (!GeneralConfig.anvilDisenchant) return;

        ItemStack left = this.inputSlots.getStackInSlot(0);
        ItemStack right = this.inputSlots.getStackInSlot(1);

        if (left.isEmpty()) return;
        if (!right.isEmpty()) return;

        boolean isBook = left.getItem() == net.minecraft.init.Items.ENCHANTED_BOOK;
        boolean hasEnchants = isBook
                ? net.minecraft.item.ItemEnchantedBook.getEnchantments(left).tagCount() > 0
                : left.isItemEnchanted();
        if (!hasEnchants) return;

        ItemStack output = DisenchanterLogic.createDisenchanted(left);
        if (output.isEmpty()) return;

        DisenchanterLogic.writeExpTag(output, DisenchanterLogic.calcExp(left));

        this.outputSlot.setInventorySlotContents(0, output);
        this.maximumCost = 1;
        ((Container) (Object) this).detectAndSendChanges();

        ci.cancel();
    }
}