package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * `Cancel Vanilla Damage Indicator` — suppresses vanilla's own damage-indicator particle.
 *
 * <h2>1.12.2 original</h2>
 * A single **unfiltered** {@code @Redirect} on {@code WorldServer#spawnParticle} inside
 * {@code EntityPlayer#attackTargetEntityWithCurrentItem}, which returned immediately when the option
 * was on. Because it was unfiltered it suppressed *every* attack particle at that call site.
 *
 * <h2>Scope of this port: DAMAGE_INDICATOR only</h2>
 * The author explicitly scoped this down — the port must **not** touch the crit (`CRIT`) or
 * enchanted-hit (`ENCHANTED_HIT`) effects, only the damage indicator. An earlier revision of this
 * port also cancelled those two (via {@code addCritParticles} / {@code addEnchantedHitParticles}
 * injections) and was deleted on the author's ruling; this file intentionally does not restore them.
 *
 * <h2>Why the call site is already narrow</h2>
 * `PlayerEntity#attack(Entity)` contains exactly **one** {@code ServerWorld#spawnParticles} call, and
 * it spawns {@code ParticleTypes.DAMAGE_INDICATOR} (verified in the bytecode — the call is at offset
 * ~1285 and its {@code int} result is immediately {@code POP}ped). The particle-type check below is
 * therefore defensive rather than load-bearing: it keeps this mixin correct even if vanilla ever adds
 * a second particle spawn at the same call site, which an unfiltered {@code @Redirect} would silently
 * swallow.
 *
 * <h2>Why the target is {@code PlayerEntity} alone</h2>
 * {@code ServerPlayerEntity#attack} exists but only calls {@code super.attack}, so it contains no
 * {@code spawnParticles} call at all. Adding it as a second target would make this {@code @Redirect}
 * unresolvable there, and {@code defaultRequire: 1} would abort mod load. Injecting into
 * {@code PlayerEntity#attack} already covers both, because {@code ServerPlayerEntity#attack}
 * delegates here.
 *
 * <h2>Returning 0 is safe</h2>
 * Vanilla pops the call's {@code int} result immediately, so the count is never observed. Returning
 * {@code 0} therefore discards the effect without any downstream behavioural change.
 *
 * <p>This is the first mixin in the project to sit in the **common** list while being, in practice,
 * server-only: {@code ServerWorld} does not exist client-side, but the injection point is inside a
 * method that only spawns particles server-side anyway, and every type referenced here is a common
 * class — so no client-only class is loaded on a dedicated server.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDamageIndicatorParticleMixin {

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(" +
                            "Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
            )
    )
    private int simpletweaks$cancelVanillaDamageIndicator(ServerWorld world, ParticleEffect particle,
                                                          double x, double y, double z, int count,
                                                          double deltaX, double deltaY, double deltaZ,
                                                          double speed) {
        if (GeneralConfig.getCancelVanillaDamageIndicator()
                && particle.getType() == ParticleTypes.DAMAGE_INDICATOR) {
            return 0;
        }
        return world.spawnParticles(particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
    }
}
