package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.ShotWeapon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opens/closes the "damage window" that makes the main hand report the **firing** weapon while a hit is
 * being resolved.
 *
 * <h2>Why not edit every handler</h2>
 * `build=cleanup30`–`34` chased this the hard way: each handler that scaled something off the weapon had to
 * be changed to `attacker.combatStack(source)`. That works, but every *future* handler would have to
 * remember the same rule, and the failure mode is silent — an arrow hit just deals a bit too much damage
 * after a weapon swap.
 *
 * <p>Instead the substitution happens at the one place all of them read: `mainHandStack`. During
 * `LivingEntity#damage` the getter returns the weapon the hit was fired with; the moment damage returns,
 * the real hand is back. Nothing in the HUD, inventory, animations or item tooltips is affected because
 * the window only exists inside that call.
 *
 * <h2>Static gate ({@code AGENTS.md} §4.3)</h2>
 * `LivingEntity#getMainHandStack()` is declared concretely on `LivingEntity` in 1.21.1 (`javap`) and is not
 * overridden by `PlayerEntity` or `MobEntity`, so one injection covers every entity. The abstract
 * `getEquippedStack(EquipmentSlot)` cannot be injected; the two concrete overrides
 * (`PlayerEntity`, `MobEntity`) are covered by their own mixins.
 *
 * <h2>Nesting</h2>
 * Our elemental riders call `damage()` from inside a handler, so `damage` re-enters while an outer window
 * is still open. Push/pop pairs in {@code ShotWeapon} keep the stack balanced, and each level sees its own
 * attacker's weapon; a mob's rider therefore never inherits the player's bow.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageWindowMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void simpletweaks$beginDamageWindow(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ShotWeapon.INSTANCE.beginHit(source);
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void simpletweaks$endDamageWindow(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ShotWeapon.INSTANCE.endHit();
    }
}
