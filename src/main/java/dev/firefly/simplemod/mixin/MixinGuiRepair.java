package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.core.config.GeneralConfig;
import net.minecraft.client.gui.GuiRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GuiRepair.class)
public class MixinGuiRepair {

    /**
     * 替换渲染时判断“过于昂贵”的常量 40
     */
    @ModifyConstant(
            method = "drawGuiContainerForegroundLayer",
            constant = @Constant(intValue = 40)
    )
    private int modifyRenderMaxCost(int original) {
        if (GeneralConfig.disableAnvilCostLimit) {
            return GeneralConfig.maxAnvilCost;
        }
        return original;
    }
}