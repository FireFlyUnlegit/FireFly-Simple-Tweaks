package dev.firefly.simpletweaks.interfaces;

/**
 * Per-projectile state used by the three mod arrow enchantments.
 *
 * <h2>Why this exists in Java, not Kotlin</h2>
 * The methods are named `simpletweaks$...` with a literal `$`, which is legal in Java identifiers and
 * conventional for mixin-added members. Declaring them as Kotlin properties would generate
 * `getSimpletweaks$...`/`setSimpletweaks$...` names, which do not match what the 1.12.2 handler bodies
 * call. Java also lets the mixin implement the interface directly, the way
 * `EntityArrowPiercingMixin` did in 1.12.2.
 *
 * <h2>1.12.2 original</h2>
 * 1.12.2 spread this across two types: `interfaces/IPiercingArrow` (the piercing trio) and a
 * `EntityArrowAccessor` for `inGround`. The piercing fields were backed by **synced**
 * `DataParameter`s created in `entityInit`, the rest by plain `entityData` NBT.
 *
 * <p>Here they live in one interface because a single mixin on `PersistentProjectileEntity` can back
 * all of them, and because the alternative — reading and writing NBT strings on every tick for the
 * tracking handler — is exactly the kind of thing 1.21 made unnecessary. The piercing trio stays
 * **synced** (`TrackedData`), matching 1.12.2, because `EnchantPiercingArrowHandler.handleHit` has a
 * client-side branch that needs the remaining count to decide whether the arrow keeps flying; the
 * multishot and tracking fields are server-only and are plain fields.
 */
public interface SimpleTweaksArrow {

    /** True for arrows spawned by the multishot handler, so their volley can ignore i-frames. */
    boolean simpletweaks$isMultishotArrow();

    void simpletweaks$setMultishotArrow(boolean value);

    /** Homing strength for `tracking_arrow`; 0 means "not a tracking arrow". */
    int simpletweaks$getTrackingLevel();

    void simpletweaks$setTrackingLevel(int value);

    /** True once `piercing_arrow` has been applied to this arrow. */
    boolean simpletweaks$isPiercing();

    void simpletweaks$setPiercing(boolean value);

    /** How many more entities this arrow may pass through. */
    int simpletweaks$getPierceRemaining();

    void simpletweaks$setPierceRemaining(int value);

    /** How many times this arrow may hit the **same** target (1.12.2 `maxPerTarget`). */
    int simpletweaks$getMaxPerTarget();

    void simpletweaks$setMaxPerTarget(int value);

    /** Hits already landed per target entity id, as 1.12.2 kept them in `st_pierce_hits`. */
    java.util.Map<Integer, Integer> simpletweaks$getPierceHits();
}
