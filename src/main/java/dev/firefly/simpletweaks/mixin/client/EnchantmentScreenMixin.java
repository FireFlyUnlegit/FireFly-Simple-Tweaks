package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.core.AdvancedEnchantMode;
import dev.firefly.simpletweaks.network.packets.PacketAdvancedEnchantMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-clicking one of the three offer rows flips advanced mode, and the mode is drawn in the
 * screen corner. No widget: the vanilla offer rows are the only thing the player has to aim at.
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {

    protected EnchantmentScreenMixin(EnchantmentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$shiftClickTogglesAdvanced(double mouseX, double mouseY, int button,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !Screen.hasShiftDown()) return;
        for (int row = 0; row < 3; row++) {
            double left = this.x + 60;
            double top = this.y + 14 + 19 * row;
            if (mouseX < left || mouseX > left + 108 || mouseY < top || mouseY > top + 19) continue;
            boolean on = !AdvancedEnchantMode.INSTANCE.getClientOn();
            AdvancedEnchantMode.INSTANCE.setClientOn(on);
            ClientPlayNetworking.send(new PacketAdvancedEnchantMode(on));
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void simpletweaks$drawAdvancedLabel(DrawContext context, int mouseX, int mouseY, float delta,
                                                CallbackInfo ci) {
        boolean on = AdvancedEnchantMode.INSTANCE.getClientOn();
        if (on) {
            // Vanilla only knows the 1/2/3 level price; advanced mode charges the row's power in
            // levels and power/3 lapis, so the affordability has to be drawn here.
            var player = this.client != null ? this.client.player : null;
            if (player != null) {
                int lapis = this.handler.getLapisCount();
                boolean creative = player.getAbilities().creativeMode;
                for (int row = 0; row < 3; row++) {
                    int power = this.handler.enchantmentPower[row];
                    if (power <= 0) continue;
                    int xp = AdvancedEnchantMode.INSTANCE.xpCost(power);
                    int gems = AdvancedEnchantMode.INSTANCE.lapisCost(power);
                    boolean ok = creative || (player.experienceLevel >= xp && lapis >= gems);
                    Text cost = Text.translatable("gui.simple_tweaks.enchant_table.advanced_cost", xp, gems);
                    context.drawTextWithShadow(this.textRenderer, cost, this.x + 63,
                            this.y + 14 + 19 * row + 10, ok ? 0x80FF20 : 0xFF5555);
                }
            }
        }
        Text label = Text.translatable(on
                ? "gui.simple_tweaks.enchant_table.advanced_on"
                : "gui.simple_tweaks.enchant_table.advanced_off");
        int width = this.textRenderer.getWidth(label);
        context.drawTextWithShadow(this.textRenderer, label, this.x + 176 - width, this.y - 11,
                on ? 0xFFD24A : 0x808080);
        if (MinecraftClient.getInstance().currentScreen == this) {
            // Cheap discoverability: the hint only shows while the cursor is over an offer row.
            if (mouseY >= this.y + 14 && mouseY <= this.y + 14 + 19 * 3 && mouseX >= this.x + 60
                    && mouseX <= this.x + 168) {
                Text hint = Text.translatable("gui.simple_tweaks.enchant_table.advanced_hint");
                context.drawTextWithShadow(this.textRenderer, hint, this.x + 176 - this.textRenderer.getWidth(hint),
                        this.y + 166 + 2, 0x808080);
            }
        }
    }
}
