package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-creates Forge's {@code LivingAttackEvent} + {@code LivingHurtEvent} seam on 1.21.
 *
 * <h2>Yarn 1.21.1 ground truth</h2>
 * <pre>
 *   net/minecraft/entity/Entity        -&gt; boolean damage(DamageSource, float)  [method_5643]
 *   net/minecraft/entity/LivingEntity  -&gt; boolean damage(DamageSource, float)  [method_5643]  (override)
 * </pre>
 * Verified against the <b>intermediary</b> jar ({@code minecraft-merged-intermediary-…}): both
 * {@code class_1297} (Entity) and {@code class_1309} (LivingEntity) <b>declare</b> {@code method_5643}.
 *
 * <h2>⚠️ Why the target is {@code LivingEntity} and not {@code Entity}</h2>
 * This class previously targeted {@code Entity} on the assumption, recorded in the old KDoc, that
 * "LivingEntity does not override damage in 1.21". <b>That assumption was wrong and the seam was
 * dead.</b> {@code LivingEntity#damage} is a complete standalone override — its bytecode contains no
 * {@code invokespecial … Entity.damage} at all (it re-implements the whole armour / shield /
 * invulnerability pipeline and then calls {@code applyDamage} directly). Mixin only rewrites the
 * body of the class it targets; it does <b>not</b> propagate injections to overrides. So every
 * call to {@code damage} on a living entity dispatched to {@code LivingEntity#damage} and the
 * injections below never ran — meaning {@code LivingHurtEvent} and {@code LivingAttackEvent} fired
 * for nothing and all ~40 damage-based handlers were inert. Only the harvest/break-speed seams
 * (injected into methods with no overriding subclass) worked, which is why the first acceptance
 * round passed while the damage layer was in fact dead.
 *
 * <p>Retargeting to {@code LivingEntity} fixes every subclass that delegates upward. The full
 * override audit (flattened-bytecode scan of the yarn jar) shows which living classes do and do not
 * reach {@code LivingEntity#damage}:
 * <table>
 *   <tr><th>class</th><th>calls super.damage?</th><th>covered by this mixin?</th></tr>
 *   <tr><td>{@code LivingEntity}</td><td>n/a (top)</td><td>yes — target</td></tr>
 *   <tr><td>{@code PlayerEntity}, {@code ServerPlayerEntity}, {@code ZombieEntity}, {@code WolfEntity},
 *           {@code WitherEntity}, {@code EndermanEntity}, {@code WardenEntity}, {@code RaiderEntity},
 *           {@code IronGolemEntity}, {@code HoglinEntity}, {@code EnderDragonEntity}, …</td>
 *       <td>yes</td><td>yes, via {@code LivingEntity}</td></tr>
 *   <tr><td>{@code ArmorStandEntity}</td><td><b>no</b></td><td>yes — second target below</td></tr>
 *   <tr><td>{@code ClientPlayerEntity}, {@code OtherClientPlayerEntity}</td><td><b>no</b></td>
 *       <td>no — deliberately: they are client-only and this seam is server-authoritative
 *           (see the {@code getWorld().isClient} guard). Vanilla never runs them on a server.</td></tr>
 *   <tr><td>{@code EnderDragonPart}, {@code EndCrystalEntity}, {@code VehicleEntity},
 *           {@code ItemEntity}, {@code ExperienceOrbEntity}, {@code BlockAttachedEntity}</td>
 *       <td><b>no</b></td><td>no — not {@code LivingEntity}, so no handler could ever match them.</td></tr>
 * </table>
 *
 * <p>No double-firing is possible: each of these classes either is the target or delegates to it,
 * never both.
 *
 * <h2>Three cooperating injectors</h2>
 * Their relative order does not matter — see {@link EventSeams} for why:
 * <ol>
 *   <li>{@code @Inject HEAD cancellable} — fires the events, honours cancellation (true
 *       suppression, matching Forge: cancelling {@code LivingAttackEvent} prevents the hit).</li>
 *   <li>{@code @ModifyVariable HEAD argsOnly} — applies a rewritten damage amount
 *       (25 handler call sites in the 1.12.2 code mutate it).</li>
 *   <li>{@code @Inject RETURN} — clears the per-invocation cache.</li>
 * </ol>
 *
 * <p><b>Lesson recorded in MIGRATION.md:</b> a Mixin target must be the class that actually
 * <i>executes</i> the method, i.e. the topmost override that does not call {@code super}. Verify
 * that by scanning the bytecode for the {@code super} call, not by assuming a subclass "obviously"
 * delegates.
 */
@Mixin({LivingEntity.class, ArmorStandEntity.class})
public abstract class EntityDamageMixin {

    @Inject(
            method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void simpletweaks$fireLivingHurt(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // The 1.12.2 handlers all guarded on !world.isRemote; keep dispatch server-authoritative.
        if (self.getWorld().isClient) {
            return;
        }

        LivingHurtEvent event = EventSeams.hurt(self, source, amount);
        if (event.isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(
            method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float simpletweaks$rewriteHurtAmount(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient) {
            return amount;
        }
        return EventSeams.hurt(self, source, amount).getAmount();
    }

    @Inject(
            method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void simpletweaks$clearHurt(DamageSource source, float amount,
                                        CallbackInfoReturnable<Boolean> cir) {
        EventSeams.clearHurt();
    }
}
