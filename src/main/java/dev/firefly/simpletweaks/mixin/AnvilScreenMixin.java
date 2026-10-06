package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Client half of the "Disable Anvil Cost Limit" / "Max Anvil Cost" option — port of 1.12.2
 * {@code MixinGuiRepair#modifyRenderMaxCost}.
 *
 * <h2>Why the server half alone is not enough</h2>
 * {@code AnvilScreen#drawForeground} has its own {@code 40} and prints
 * {@code container.repair.expensive} ("Too Expensive!") in red once the cost reaches it. If only
 * {@link AnvilScreenHandlerMixin} is installed, the server happily produces the result while the
 * client still paints the item as unusable — the exact "config value does nothing" symptom this pair
 * of mixins exists to fix.
 *
 * <h2>Yarn 1.21.1 ground truth (bytecode, per MIGRATION.md 铁律二)</h2>
 * {@code AnvilScreen.drawForeground(DrawContext,int,int)} contains exactly <b>one</b>
 * {@code bipush 40}, guarding the {@code client.player.getAbilities().creativeMode} read — the
 * "expensive" label branch:
 *
 * <pre>
 *   iload 4; bipush 40; if_icmplt &lt;skip&gt;;        // if (levelCost &gt;= 40 &amp;&amp; !creative) -&gt; expensive
 * </pre>
 *
 * Being the only occurrence in the method, this needs <b>no {@code ordinal}</b> — a deliberate
 * contrast with {@link AnvilScreenHandlerMixin}, where the same literal appears three times.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    /** The cost at which the GUI switches to the red "Too Expensive!" label — see the class KDoc. */
    @ModifyConstant(
            method = "drawForeground",
            constant = @Constant(intValue = 40)
    )
    private int simpletweaks$maxAnvilCostForDisplay(int original) {
        return GeneralConfig.getDisableAnvilCostLimit() ? GeneralConfig.getMaxAnvilCost() : original;
    }
}
