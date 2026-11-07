package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's {@code LivingDamageEvent} seam (16 usages in the 1.12.2 code).
 *
 * <p>Yarn 1.21.1 ground truth: {@code LivingEntity#applyDamage(DamageSource, float): void}
 * = {@code method_6074}, official {@code f}. Forge fired {@code LivingDamageEvent} from
 * {@code EntityLivingBase#applyDamage}, i.e. after armor/absorption reduction and immediately
 * before health is subtracted — the same position.
 *
 * <p>Only amount rewriting is needed here: Forge declared this event non-cancellable, and the two
 * handlers that set {@code isCanceled} on it ({@code EnchantDeathProtectionHandler},
 * {@code EnchantGrievousWoundsHandler}) also set {@code amount = 0f} on the preceding line. So a
 * cancelled event is mapped to zero remaining damage, which is what those handlers observe.
 *
 * <h2>⚠️ Why {@code PlayerEntity} is a second target</h2>
 * The third instance of the same Mixin trap as {@link EntityDamageMixin} and
 * {@link LivingEntityDeathMixin}, found by the server-side acceptance run: handlers on
 * {@code LivingHurtEvent} logged, handlers on {@code LivingDamageEvent} did not, for a player
 * victim.
 *
 * <p>The override audit (flattened-bytecode scan of the yarn jar) shows only three classes declare
 * {@code applyDamage(DamageSource, float)}:
 * <table>
 *   <tr><th>class</th><th>calls super.applyDamage?</th><th>covered?</th></tr>
 *   <tr><td>{@code LivingEntity}</td><td>n/a (top)</td><td>yes — target</td></tr>
 *   <tr><td>{@code PlayerEntity}</td><td><b>no</b> — a complete standalone re-implementation that
 *       calls {@code applyArmorToDamage} / {@code modifyAppliedDamage} / {@code setHealth} itself
 *       and then records player stats</td><td>yes — second target</td></tr>
 *   <tr><td>{@code WolfEntity}</td><td>yes, via {@code TameableEntity}</td><td>yes, via
 *       {@code LivingEntity}</td></tr>
 * </table>
 *
 * <p>{@code ServerPlayerEntity} does not declare it, so it inherits {@code PlayerEntity}'s override
 * and is therefore covered by targeting {@code PlayerEntity}. Every other living entity inherits
 * {@code LivingEntity}. No class both is a target and delegates, so nothing fires twice.
 */
@Mixin({LivingEntity.class, PlayerEntity.class})
public abstract class LivingEntityApplyDamageMixin {

    @ModifyVariable(
            method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float simpletweaks$rewriteAppliedDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingDamageEvent event = EventSeams.damage(self, source, amount);
        return event.isCanceled() ? 0.0f : event.getAmount();
    }

    @Inject(
            method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V",
            at = @At("RETURN")
    )
    private void simpletweaks$clearDamage(DamageSource source, float amount, CallbackInfo ci) {
        EventSeams.clearDamage();
    }
}
