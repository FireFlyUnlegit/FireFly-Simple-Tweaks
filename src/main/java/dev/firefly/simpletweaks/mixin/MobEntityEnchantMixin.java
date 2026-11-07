package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.mobs.MobElements;
import dev.firefly.simpletweaks.util.mobs.MobEnchantments;
import dev.firefly.simpletweaks.util.mobs.MobLevels;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stronger mobs, but nothing to farm: mob gear gets this mod's enchantments on spawn, and loses them
 * again before it hits the ground.
 *
 * <p>All of the decision-making lives in {@link MobEnchantments}; this class is only the two seams.
 *
 * <h2>Why these two targets and not the obvious ones</h2>
 * <table>
 *   <tr><th>target</th><th>why not something else</th></tr>
 *   <tr><td>{@code MobEntity#enchantEquipment} (the <b>private</b> 5-arg overload)</td>
 *       <td>{@code updateEnchantments} is overridden by <b>7</b> vanilla classes (AbstractSkeleton,
 *           Pillager, Vindicator, WitherSkeleton, Vex, Zombie, Piglin) — injecting there would need an
 *           override audit and would still be wrong for any subclass that replaces it wholesale. The
 *           private overload <b>cannot be overridden</b> (private methods are not virtual), and it is
 *           the single funnel both the main-hand path and the armour loop go through. It also already
 *           carries the {@link LocalDifficulty} and {@link Random} this feature needs, so no extra state
 *           has to be recovered.</td></tr>
 *   <tr><td>{@code MobEntity#dropEquipment}</td>
 *       <td>{@code LivingEntity#dropEquipment} is an empty method ({@code 0: return}); every equipment
 *           drop goes through the {@code MobEntity} override. Eight vanilla classes override it, but
 *           every one of them calls {@code super} (traced: Creeper/Enderman/Skeleton/WitherSkeleton →
 *           {@code HostileEntity}/{@code AbstractSkeletonEntity}, which do <b>not</b> override it, so the
 *           chain lands here; Piglin → {@code AbstractPiglinEntity}, also not overriding). Injecting at
 *           {@code HEAD} therefore runs for every mob.</td></tr>
 * </table>
 *
 * <h2>Ordering matters on the drop side</h2>
 * The strip runs at {@code HEAD}, i.e. before vanilla reads the stacks, so the stripped stack is the one
 * that drops. Stripping after the drop loop would be too late — the {@code ItemStack} would already be
 * scheduled to drop (and mutating it then would still leave the dropped copy clean, but relying on that
 * is a coincidence rather than a contract).
 *
 * <h2>Performance</h2>
 * Both injectors fire <b>once per mob</b> — spawn and death. There is no per-tick cost, and the pool of
 * eligible enchantments is cached per registry (see {@link MobEnchantments}). Do not move any of this
 * onto a tick seam.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityEnchantMixin {

    /**
     * The private overload that actually applies the provider. Kept as a literal descriptor because
     * {@code enchantEquipment} has two overloads and Mixin would otherwise have to guess.
     */
    private static final String ENCHANT_EQUIPMENT =
            "enchantEquipment(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/entity/EquipmentSlot;"
                    + "Lnet/minecraft/util/math/random/Random;FLnet/minecraft/world/LocalDifficulty;)V";

    /**
     * After vanilla's own enchant for this slot: maybe add one of this mod's.
     *
     * <p>{@code RETURN} and not {@code TAIL}: the method has a single {@code return} instruction, and
     * {@code RETURN} is the more explicit of the two for "whenever this finishes, however it finished"
     * (the empty-stack and chance-miss branches both jump to it).
     */
    @Inject(method = ENCHANT_EQUIPMENT, at = @At("RETURN"))
    private void simpletweaks$enchantMobGear(ServerWorldAccess world, EquipmentSlot slot, Random random,
            float chance, LocalDifficulty difficulty, CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;

        // One difficulty for all three features: vanilla's unclamped local difficulty plus a term for the
        // nearest player's armour and max health. Computed once here (it scans for the nearest player) and
        // handed to each consumer, so a well-geared player meets tougher mobs on every axis at once.
        float effective = MobLevels.INSTANCE.effectiveDifficulty(self, world, difficulty);

        // Level first: it changes max health / attack / armour, and `ensureLevel` is idempotent, so running
        // it from this per-slot injector (which fires up to five times per spawn) is safe and costs one int
        // compare after the first call.
        MobLevels.INSTANCE.ensureLevel(self, effective, random);
        MobElements.INSTANCE.tryAssign(self, random, effective);
        MobEnchantments.INSTANCE.tryApplyAtSpawn(self, slot, world, random, effective);
    }

    /**
     * Before vanilla collects the equipment: remove this mod's enchantments so they cannot be farmed.
     */
    @Inject(
            method = "dropEquipment(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At("HEAD")
    )
    private void simpletweaks$stripModEnchantmentsOnDeath(ServerWorld world, DamageSource source,
            boolean recentlyHit, CallbackInfo ci) {
        MobEnchantments.INSTANCE.stripAll((MobEntity) (Object) this, world);
    }
}
