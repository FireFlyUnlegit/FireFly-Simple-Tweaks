package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's {@code CriticalHitEvent} seam (3 handlers + {@code runPlayerAttack}).
 *
 * <h2>Yarn 1.21.1 ground truth (verified in the bytecode, not assumed)</h2>
 * <pre>
 *   net/minecraft/entity/player/PlayerEntity       -&gt; void attack(Entity)   [declares it, NO super]
 *   net/minecraft/server/network/ServerPlayerEntity -&gt; void attack(Entity)   [super -&gt; PlayerEntity]
 * </pre>
 * Only {@code PlayerEntity} and {@code ServerPlayerEntity} declare {@code attack}, and the latter
 * delegates — so {@code @Mixin(PlayerEntity.class)} covers both and nothing fires twice. (This is the
 * override audit the {@link EntityDamageMixin} incident made mandatory; see MIGRATION.md.)
 *
 * <h2>The three injectors, and why they are ordered safely</h2>
 * <ol>
 *   <li>{@code @Inject HEAD} — opens the per-swing state.</li>
 *   <li>{@code @ModifyConstant(floatValue = 1.5F)} at bytecode offset 343 — this <b>is</b> vanilla's
 *       <code>if (bl2) f *= 1.5F;</code>. It fires only when vanilla took the crit branch, which is
 *       the only reliable way to learn {@code isVanillaCritical} without duplicating the eight-part
 *       condition. It returns {@code 1.0f} so vanilla's multiplication becomes a no-op.</li>
 *   <li>{@code @ModifyArg} on the {@code Entity#damage} call at offset 458 — fires once, after every
 *       early return (exactly where {@code ForgeHooks.getCriticalHit} sat), posts the event and
 *       applies the Forge multiplier.</li>
 *   <li>{@code @Inject RETURN} — clears the state.</li>
 * </ol>
 * Their offsets are strictly increasing, so unlike the damage seams there is no get-or-fire
 * ambiguity: the order is fixed by the bytecode.
 *
 * <p>{@code 1.5F} occurs exactly once in {@code attack} (checked: the method's only float constant
 * loads are 0.5, 0.2, 0.8, 0.9, 1.5 and 0.017453292), so {@code @ModifyConstant} needs no
 * {@code ordinal}.
 *
 * <p>{@code @ModifyArg}'s target names owner {@code Entity}, and the method contains a second
 * {@code damage} call whose owner is {@code LivingEntity} (the sweeping-attack branch). Only the
 * first — the real hit — matches, which is what Forge did too: the sweep was never crit-rolled.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAttackMixin {

    @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void simpletweaks$beginCrit(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        // The 1.12.2 handlers all guard on !world.isRemote; keep crit dispatch server-authoritative.
        if (self.getWorld().isClient) {
            return;
        }
        EventSeams.beginCrit(self, target);
    }

    /**
     * `infinite_power`'s one-shot kill — the same HEAD position 1.12.2's `MixinEntityPlayerAttack` used.
     *
     * <p><b>Unlike 1.12.2 this does NOT cancel the attack</b>, and that is deliberate. Cancelling at
     * HEAD would skip two things that live later in `attack`: the attack-cooldown reset, and this
     * class's own {@code @Inject(at = RETURN)} that calls {@link EventSeams#endCrit()}. A cancelled
     * call never reaches RETURN, so `EventSeams.critActive()` would stay true forever and poison the
     * crit roll of every subsequent swing. Letting vanilla run means the already-dead target absorbs
     * one harmless no-op `damage()` call instead. See `docs/infinite-power-deferred.md` §5.1/§5.2/§5.4.
     *
     * <p>Ordering against {@code simpletweaks$beginCrit} does not matter: the crit seam only records
     * state here, and the kill reads nothing from it.
     */
    @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void simpletweaks$infinitePowerKill(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient) {
            return;
        }
        // ⚠️ Vanilla unwraps the dragon's body parts *after* this HEAD injection — `attack` offsets
        // 1052..1062 are `if (target instanceof EnderDragonPart) target = part.owner;`. An
        // EnderDragonPart extends Entity, NOT LivingEntity, so without this unwrap the guard below
        // rejects the only entity the dragon can ever be hit through and the dragon is unkillable by
        // melee. Confirmed from a real session: the `[ST-InfinitePower] outcome=one-shot` line appears
        // for every mob but never for the dragon, i.e. this handler simply never ran for it.
        //
        // 1.12.2 had the identical bug (its `EntityDragonPart` was not an `EntityLivingBase` either),
        // which is why `killDragon` existed but was unreachable by melee there — only the laser, which
        // scans for `EntityLivingBase` along its path, could reach the dragon body. So this is a fix,
        // not a deviation.
        if (target instanceof EnderDragonPart part) {
            target = part.owner;
        }
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        if (EnchantInfinitePowerHandler.heldLevel(self) <= 0) {
            return;
        }
        EnchantInfinitePowerHandler.handleAttack(self, living, self.getWorld());
    }

    @ModifyConstant(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            constant = @Constant(floatValue = 1.5F)
    )
    private float simpletweaks$neutraliseVanillaCrit(float original) {
        if (!EventSeams.critActive()) {
            return original;
        }
        EventSeams.markVanillaCrit();
        return 1.0f;
    }

    @ModifyArg(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            ),
            index = 1
    )
    private float simpletweaks$applyCritMultiplier(float amount) {
        if (!EventSeams.critActive()) {
            return amount;
        }
        return amount * EventSeams.critMultiplier();
    }

    @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
    private void simpletweaks$endCrit(Entity target, CallbackInfo ci) {
        EventSeams.endCrit();
    }
}
