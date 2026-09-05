package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.config.AnvilConfig;
import net.minecraft.inventory.ContainerRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ContainerRepair.class)
public class MixinContainerRepair {

    /**
     * 替换所有常量 40
     * 包括：if (this.maximumCost >= 40)、if (k == i && k > 0 && this.maximumCost >= 40)
     */
    @ModifyConstant(
            method = "updateRepairOutput",
            constant = @Constant(intValue = 40)
    )
    private int modifyMaxAnvilCost(int original) {
        if (AnvilConfig.disableAnvilCostLimit) {
            return AnvilConfig.maxAnvilCost;
        }
        return original;
    }

    /**
     * 处理 itemstack.getCount() > 1 时 i = 40 的赋值
     */
    @ModifyVariable(
            method = "updateRepairOutput",
            name = "i",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            )
    )
    private int modifyVariableI(int original) {
        if (AnvilConfig.disableAnvilCostLimit && original == 40) {
            return 0;
        }
        return original;
    }
}