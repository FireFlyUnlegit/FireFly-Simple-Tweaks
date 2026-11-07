package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.ArmorCurve;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Continues vanilla's armour formula past its 20-point wall instead of truncating it.
 *
 * <h2>What vanilla does (verified with {@code javap -c} on 1.21.1)</h2>
 * <pre>
 *   f = 2.0f + toughness / 4.0f;
 *   g = MathHelper.clamp(armor - damage / f, armor * 0.2f, 20.0f);   // &lt;-- the wall
 *   h = g / 25.0f;                                                   // max 0.8
 *   ItemStack weapon = source.getWeaponStack();
 *   if (weapon != null &amp;&amp; entity.getWorld() instanceof ServerWorld sw)
 *       h = MathHelper.clamp(EnchantmentHelper.getArmorEffectiveness(sw, weapon, entity, source, h), 0f, 1f);
 *   return damage * (1.0f - h);
 * </pre>
 *
 * <h2>What this injection changes, and what it leaves alone</h2>
 * Only the **upper bound of the clamp** (vanilla's `20.0f` becomes `armor`) and the mapping from `g` to
 * the reduction fraction, which {@link ArmorCurve#reductionForEffectiveArmor} extends smoothly
 * (`1 - 4/g` above the wall, with the same value and slope as vanilla at `g = 20`).
 *
 * <p>When `armor <= 20` the injection returns immediately, so **vanilla runs untouched** — that is the
 * cheapest possible guarantee that nothing below the wall can drift. Everything else in the method (the
 * damage-dependent term, the toughness divisor, the `armor_effectiveness` enchantment hook) is
 * reproduced exactly as vanilla computes it.
 *
 * <h2>Why the whole method rather than one patched constant</h2>
 * The wall is an `ldc 20.0f` feeding a `MathHelper.clamp(FFF)F`, and there are **two** clamp calls in
 * the method (the second one clamps the armour-effectiveness fraction into `[0, 1]`). A
 * {@code @ModifyArg} would need ordinal juggling and a {@code @Redirect} on {@code clamp} would fire for
 * both — including the one that must not change. Re-implementing the 15 instructions is the version that
 * cannot silently patch the wrong call site.
 *
 * <h2>Static gate (AGENTS.md §4.3)</h2>
 * {@code DamageUtil#getDamageLeft} is a static utility declared once and never overridden; callers are
 * {@code LivingEntity#applyArmorToDamage} and {@code LivingEntity#applyDamage}. The body above was
 * copied instruction by instruction from the 1.21.1 jar.
 */
@Mixin(DamageUtil.class)
public abstract class DamageUtilMixin {

    @Inject(method = "getDamageLeft", at = @At("HEAD"), cancellable = true)
    private static void simpletweaks$continueArmorPastTheWall(
        LivingEntity entity,
        float damage,
        DamageSource source,
        float armor,
        float armorToughness,
        CallbackInfoReturnable<Float> cir
    ) {
        if (!ArmorCurve.INSTANCE.appliesTo(entity)) {
            return;
        }
        // Below the wall the two formulas are bit-identical, so vanilla stays in charge.
        if (armor <= ArmorCurve.VANILLA_CAP) {
            return;
        }
        if (!(damage > 0.0f)) {
            return;
        }

        float divisor = 2.0f + armorToughness / 4.0f;
        // Vanilla's effective-armour term, including vanilla's own 20-point ceiling: the *percentage*
        // part must stay exactly what vanilla computes, because only the overflow changes kind.
        float effective = MathHelper.clamp(armor - damage / divisor, armor * 0.2f, ArmorCurve.VANILLA_CAP);
        float reduction = effective / ArmorCurve.REDUCTION_DIVISOR;

        ItemStack weapon = source.getWeaponStack();
        if (weapon != null && entity.getWorld() instanceof ServerWorld serverWorld) {
            reduction = MathHelper.clamp(
                EnchantmentHelper.getArmorEffectiveness(serverWorld, weapon, entity, source, reduction),
                0.0f,
                1.0f
            );
        }

        float afterPercent = damage * (1.0f - reduction);

        // The overflow: flat damage removed per hit, bounded by a fraction of what survived the
        // percentage so no amount of armour makes a hit free (see `ArmorCurve`).
        float flat = ArmorCurve.INSTANCE.flatReduction(armor, afterPercent);
        cir.setReturnValue(Math.max(0.0f, afterPercent - flat));
    }
}
