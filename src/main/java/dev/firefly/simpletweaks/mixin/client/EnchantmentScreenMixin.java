package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.core.AdvancedEnchantMode;
import dev.firefly.simpletweaks.network.packets.PacketAdvancedEnchantMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the advanced-mode toggle to the enchanting table screen. */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {

    protected EnchantmentScreenMixin(EnchantmentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void simpletweaks$advancedToggle(CallbackInfo ci) {
        int width = 96;
        addDrawableChild(ButtonWidget.builder(label(), button -> {
            boolean on = !AdvancedEnchantMode.INSTANCE.getClientOn();
            AdvancedEnchantMode.INSTANCE.setClientOn(on);
            ClientPlayNetworking.send(new PacketAdvancedEnchantMode(on));
            button.setMessage(label());
        }).dimensions(this.x + 176 - width - 4, this.y + 166 - 22, width, 18).build());
    }

    private Text label() {
        return Text.translatable(AdvancedEnchantMode.INSTANCE.getClientOn()
                ? "gui.simple_tweaks.enchant_table.advanced_on"
                : "gui.simple_tweaks.enchant_table.advanced_off");
    }
}
