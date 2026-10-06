package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.SimpleTweaks;
import dev.firefly.simpletweaks.core.config.GeneralConfig;
import dev.firefly.simpletweaks.enchantments.EnchantmentNameColors;
import dev.firefly.simpletweaks.enchantments.InfinitePowerRainbow;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase-6 batch 2: the 1.12.2 `Enabled Enchantments' Color` option
 * ({@code GeneralConfig.enabledEnchantmentColor}).
 *
 * <h2>1.12.2 original</h2>
 * The colour was applied in {@code ModEnchantments}, the base class every mod enchantment extended:
 * <pre>
 *   override fun getTranslatedName(level: Int): String = decorateName(rawName(level))
 *   protected open fun decorateName(raw: String): String =
 *       if (!GeneralConfig.enabledEnchantmentColor) raw else "$textColor$raw"
 *   // textColor defaults to category.color, e.g. LEGENDARY -&gt; GOLD
 * </pre>
 * i.e. it prefixed the tier's legacy {@code §} code onto the whole string, including the level suffix.
 *
 * <h2>Why the target is {@code Enchantment.getName} and not a tooltip hook</h2>
 * In 1.12.2 the seam was "wherever the enchantment name is produced", which covered the item tooltip,
 * the enchanting table and the anvil alike, because all of them called {@code getTranslatedName}.
 * 1.21's direct equivalent is {@code Enchantment.getName(RegistryEntry, int) : Text} — verified from
 * the Yarn-named jar to be {@code public static}, so the injected handler must be static too. Doing it
 * here rather than in an {@code ItemTooltipCallback} keeps that same coverage and avoids matching
 * tooltip lines by their rendered string, which is exactly the kind of fragile seam this project has
 * already been bitten by.
 *
 * <h2>Why the colour is NOT baked into the enchantment JSON {@code description}</h2>
 * 1.21 enchantments are data-driven, so a styled {@code description} component would colour the name
 * with **zero code**. That was rejected: the feature is gated by a runtime config switch, and a static
 * component cannot be turned off. Baking it in would have made {@code enabledEnchantmentColor} inert —
 * the same class of mistake as the deleted particle mixins (a hook that looks right but means the
 * wrong thing).
 *
 * <h2>{@code infinite_power} is a real exception, not an oversight</h2>
 * 1.12.2's {@code EnchantInfinitePower} **overrode {@code decorateName}** to produce an animated
 * rainbow (see {@link InfinitePowerRainbow}) instead of the MYTHIC tier colour. That override is
 * reproduced here — without it the port would silently lose the effect. Its generated tier colour
 * ({@code DARK_RED}) is therefore present in {@link EnchantmentNameColors} but unused, deliberately,
 * so that the table stays a faithful copy of {@code EnchantmentCategories.color}.
 *
 * <p>{@code EnchantHealer} also passes an explicit {@code textColor = GREEN}, but its category is
 * UNCOMMON, which is already GREEN — so that override needs no special case. (Checked, not assumed.)
 *
 * <h2>Side effects</h2>
 * None on non-rendering paths: {@code Text.getString()} returns the plain text and drops styles, so
 * server-side logging and the {@code /sttest} diagnostics are unaffected by this mixin.
 *
 * <h2>Why the `client` list</h2>
 * The effect is purely visual, so it is applied on a physical client only. {@code Enchantment} is a
 * common class and this mixin references no client-only type, but listing it under {@code "client"}
 * keeps dedicated servers byte-for-byte on vanilla behaviour.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentNameColorMixin {

    @Inject(
            method = "getName(Lnet/minecraft/registry/entry/RegistryEntry;I)Lnet/minecraft/text/Text;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void simpletweaks$colorName(RegistryEntry<Enchantment> enchantment, int level,
                                               CallbackInfoReturnable<Text> cir) {
        if (!GeneralConfig.getEnabledEnchantmentColor()) {
            return;
        }

        RegistryKey<Enchantment> key = enchantment.getKey().orElse(null);
        if (key == null) {
            return;
        }
        Identifier id = key.getValue();
        // 1.12.2 decorated inside `ModEnchantments`, i.e. only this mod's own enchantments; vanilla
        // ones were never touched.
        if (!SimpleTweaks.MOD_ID.equals(id.getNamespace())) {
            return;
        }

        Text original = cir.getReturnValue();

        // The one `decorateName` override in the 1.12.2 tree.
        if ("infinite_power".equals(id.getPath())) {
            cir.setReturnValue(InfinitePowerRainbow.INSTANCE.apply(original));
            return;
        }

        Formatting color = EnchantmentNameColors.INSTANCE.of(id.getPath());
        if (color == null) {
            return;
        }
        cir.setReturnValue(original.copy().setStyle(original.getStyle().withColor(color)));
    }
}
