package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.core.AdvancedEnchantMode;
import dev.firefly.simpletweaks.network.packets.PacketAdvancedEnchantMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shift-clicking one of the three offer rows flips advanced mode, and the advanced price is appended
 * to the offer tooltip instead of being painted over the rows.
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
        if (simpletweaks$hoveredRow(mouseX, mouseY) < 0) return;
        boolean on = !AdvancedEnchantMode.INSTANCE.getClientOn();
        AdvancedEnchantMode.INSTANCE.setClientOn(on);
        ClientPlayNetworking.send(new PacketAdvancedEnchantMode(on));
        cir.setReturnValue(true);
    }

    private int simpletweaks$hoveredRow(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            double left = this.x + 60;
            double top = this.y + 14 + 19 * row;
            if (mouseX >= left && mouseX <= left + 108 && mouseY >= top && mouseY <= top + 19) return row;
        }
        return -1;
    }

    /** Vanilla builds the offer tooltip here; append the advanced price so both live in one box. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V"))
    private void simpletweaks$appendAdvancedPrice(DrawContext context, TextRenderer renderer, List<Text> lines,
                                                  int mouseX, int mouseY) {
        int row = simpletweaks$hoveredRow(mouseX, mouseY);
        int power = row >= 0 ? this.handler.enchantmentPower[row] : 0;
        boolean on = AdvancedEnchantMode.INSTANCE.getClientOn();
        if (power > 0 && on) {
            int xp = AdvancedEnchantMode.INSTANCE.xpCost(power);
            int gems = AdvancedEnchantMode.INSTANCE.lapisCost(power);
            boolean affordable = this.client != null && this.client.player != null
                    && (this.client.player.getAbilities().creativeMode
                        || (this.client.player.experienceLevel >= xp && this.handler.getLapisCount() >= gems));
            List<Text> merged = new ArrayList<>(lines);
            merged.add(Text.translatable("gui.simple_tweaks.enchant_table.advanced_cost", xp, gems)
                    .formatted(affordable ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.RED));
            context.drawTooltip(renderer, merged, mouseX, mouseY);
            return;
        }
        context.drawTooltip(renderer, lines, mouseX, mouseY);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void simpletweaks$drawModeLabel(DrawContext context, int mouseX, int mouseY, float delta,
                                            CallbackInfo ci) {
        boolean on = AdvancedEnchantMode.INSTANCE.getClientOn();
        Text label = Text.translatable(on
                ? "gui.simple_tweaks.enchant_table.advanced_on"
                : "gui.simple_tweaks.enchant_table.advanced_off");
        int width = this.textRenderer.getWidth(label);
        context.drawTextWithShadow(this.textRenderer, label, this.x + 176 - width, this.y - 11,
                on ? 0xFFD24A : 0x808080);
        if (on && this.client != null && this.client.player != null) {
            var player = this.client.player;
            int lapis = this.handler.getLapisCount();
            boolean creative = player.getAbilities().creativeMode;
            for (int row = 0; row < 3; row++) {
                int power = this.handler.enchantmentPower[row];
                if (power <= 0) continue;
                boolean affordable = creative || (player.experienceLevel >= AdvancedEnchantMode.INSTANCE.xpCost(power)
                        && lapis >= AdvancedEnchantMode.INSTANCE.lapisCost(power));
                if (!affordable) {
                    context.fill(this.x + 60, this.y + 14 + 19 * row, this.x + 168, this.y + 14 + 19 * row + 19,
                            0x99000000);
                }
            }
        }
        if (simpletweaks$hoveredRow(mouseX, mouseY) >= 0) {
            Text hint = Text.translatable("gui.simple_tweaks.enchant_table.advanced_hint");
            context.drawTextWithShadow(this.textRenderer, hint, this.x + 176 - this.textRenderer.getWidth(hint),
                    this.y + 166 + 2, 0x808080);
        }
    }
}
