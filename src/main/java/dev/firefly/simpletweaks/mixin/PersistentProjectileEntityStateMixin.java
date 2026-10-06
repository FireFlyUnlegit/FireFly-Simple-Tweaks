package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.interfaces.SimpleTweaksArrow;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-arrow state for the three arrow enchantments, backing {@link SimpleTweaksArrow}.
 *
 * <h2>1.12.2 original</h2>
 * `EntityArrowPiercingMixin` added three **synced** `DataParameter`s in `entityInit`
 * (`ST_PIERCING` / `ST_REMAINING` / `ST_MAX_PER_TARGET`), and the multishot and tracking handlers used
 * `entityData` NBT (`st_multishot_arrow`, `st_tracking_level`, and a `st_pierce_hits` string packed as
 * `id:count,id:count`).
 *
 * <h2>What is kept, and what changed</h2>
 * <ul>
 *   <li>The piercing trio stays **synced** (`TrackedData`), because
 *       `EnchantPiercingArrowHandler.handleHit` has a client-side branch that reads the remaining
 *       count to decide whether the arrow keeps flying. Dropping the sync would make the client stop
 *       the arrow on the first hit while the server lets it continue — a visible desync, which is
 *       presumably why 1.12.2 synced them.</li>
 *   <li>The multishot flag and tracking level are **plain fields**: both are read only on the logical
 *       server, so syncing them would be pure overhead. This also removes the NBT round-trip 1.12.2
 *       did on every arrow every tick for tracking.</li>
 *   <li>The per-target hit map is a field rather than the `st_pierce_hits` NBT string. 1.12.2 packed
 *       and reparsed that string on every hit, which is a `split`/`join` per arrow per collision; the
 *       map is the same information without the churn. The packing/unpacking helpers therefore have no
 *       equivalent here — see `docs/phase6-client-notes.md` §21.</li>
 * </ul>
 *
 * <p>`PersistentProjectileEntity` is the right target rather than `ArrowEntity`: the mod's
 * enchantments are bow enchantments, but the same state should also apply to spectral and tipped
 * arrows, all of which extend `PersistentProjectileEntity`. In 1.12.2 `EntityArrow` was that common
 * base.
 *
 * <p><b>Override audit:</b> `initDataTracker` is declared by `Entity` and overridden by every entity
 * class down the chain (`PersistentProjectileEntity` declares its own and calls `super`), so injecting
 * at its TAIL in this class runs **after** the projectile's own tracked data is registered — which is
 * required, because `DataTracker.Builder#add` must not run before the superclass registration. The
 * handler adds only our three, so no vanilla data is disturbed.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityStateMixin implements SimpleTweaksArrow {

    @Unique
    private static final TrackedData<Boolean> ST_PIERCING =
            DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Unique
    private static final TrackedData<Integer> ST_REMAINING =
            DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Unique
    private static final TrackedData<Integer> ST_MAX_PER_TARGET =
            DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Unique
    private boolean simpletweaks$multishotArrow = false;

    @Unique
    private int simpletweaks$trackingLevel = 0;

    @Unique
    private final Map<Integer, Integer> simpletweaks$pierceHits = new HashMap<>();

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void simpletweaks$initArrowState(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(ST_PIERCING, false);
        builder.add(ST_REMAINING, 0);
        builder.add(ST_MAX_PER_TARGET, 0);
    }

    @Override
    public boolean simpletweaks$isMultishotArrow() {
        return this.simpletweaks$multishotArrow;
    }

    @Override
    public void simpletweaks$setMultishotArrow(boolean value) {
        this.simpletweaks$multishotArrow = value;
    }

    @Override
    public int simpletweaks$getTrackingLevel() {
        return this.simpletweaks$trackingLevel;
    }

    @Override
    public void simpletweaks$setTrackingLevel(int value) {
        this.simpletweaks$trackingLevel = value;
    }

    /** Reads the synced value, so this is valid on the client too (see the class KDoc). */
    @Override
    public boolean simpletweaks$isPiercing() {
        return ((PersistentProjectileEntity) (Object) this).getDataTracker().get(ST_PIERCING);
    }

    @Override
    public void simpletweaks$setPiercing(boolean value) {
        ((PersistentProjectileEntity) (Object) this).getDataTracker().set(ST_PIERCING, value);
    }

    @Override
    public int simpletweaks$getPierceRemaining() {
        return ((PersistentProjectileEntity) (Object) this).getDataTracker().get(ST_REMAINING);
    }

    @Override
    public void simpletweaks$setPierceRemaining(int value) {
        ((PersistentProjectileEntity) (Object) this).getDataTracker().set(ST_REMAINING, value);
    }

    @Override
    public int simpletweaks$getMaxPerTarget() {
        return ((PersistentProjectileEntity) (Object) this).getDataTracker().get(ST_MAX_PER_TARGET);
    }

    @Override
    public void simpletweaks$setMaxPerTarget(int value) {
        ((PersistentProjectileEntity) (Object) this).getDataTracker().set(ST_MAX_PER_TARGET, value);
    }

    @Override
    public Map<Integer, Integer> simpletweaks$getPierceHits() {
        return this.simpletweaks$pierceHits;
    }
}
