package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.mobs.LevelledMob;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales a levelled creeper's explosion.
 *
 * <h2>Why the creeper needed its own seam</h2>
 * Every other hostile mob's threat is its melee hit or its gear, and those are already covered by the
 * level's attribute modifiers and by the gear enchantments. A creeper has **no equipment slots to
 * enchant** and its damage comes from `World#createExplosion`, which reads neither attack damage nor
 * armour — so a level-20 creeper detonated exactly like a level-1 one. That is the reported
 * "苦力怕好像没被加强".
 *
 * <h2>The injection point</h2>
 * `CreeperEntity#explode()` computes, verified in the bytecode:
 *
 * <pre>
 *   17: fconst_2 / 21: fconst_1        // powered ? 2.0f : 1.0f
 *   22: fstore_1                        // &lt;- the first (and only) float store in the method
 *   46: getfield explosionRadius
 *   49: i2f
 *   50: fload_1
 *   51: fmul                            // power * explosionRadius
 *   55: invokevirtual World.createExplosion(..., F, ...)
 * </pre>
 *
 * So the single `fstore_1` local **is** the explosion multiplier, and scaling it scales the blast
 * exactly. `@ModifyVariable(at = STORE, ordinal = 0)` targets that store; there is no other float local
 * in the method, which is what makes `ordinal = 0` unambiguous.
 *
 * <h2>Why not scale {@code explosionRadius} instead</h2>
 * That field is `private int` and, unlike most mob state, is **not written to NBT** (verified: the class
 * declares neither `writeCustomDataToNbt` nor `readCustomDataFromNbt`, and 1.21 no longer persists
 * `ExplosionRadius`). Scaling it once at spawn would therefore silently revert on the first chunk reload —
 * a buff that only works until you walk away. Deriving the multiplier at detonation time from the mob's
 * level, which *is* persisted, has no such failure mode.
 *
 * <h2>Bounds</h2>
 * `+15 %` per level above 1, capped at **×2.5**. Uncapped growth would let a deep-cave creeper level the
 * player's base from outside the render distance, which is not "harder", it is unplayable.
 */
@Mixin(CreeperEntity.class)
public abstract class CreeperExplosionMixin {

    /** Extra explosion power per level above 1. */
    private static final float POWER_PER_LEVEL = 0.15f;

    /** Ceiling on the multiplier; reached at level 11. */
    private static final float MAX_MULTIPLIER = 2.5f;

    @ModifyVariable(method = "explode", at = @At("STORE"), ordinal = 0)
    private float simpletweaks$scaleExplosionPower(float power) {
        CreeperEntity self = (CreeperEntity) (Object) this;
        int level = ((LevelledMob) self).getMobLevel();
        if (level <= 1) {
            return power;
        }
        float multiplier = Math.min(1.0f + POWER_PER_LEVEL * (level - 1), MAX_MULTIPLIER);
        return power * multiplier;
    }
}
