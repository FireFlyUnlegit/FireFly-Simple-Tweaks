package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.elements.ElementType;
import dev.firefly.simpletweaks.util.mobs.ElementalMob;
import dev.firefly.simpletweaks.util.mobs.LevelledMob;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stores the mob's level on the entity and keeps it across save/load.
 *
 * <p>The three stat modifiers would already survive a reload (vanilla writes attribute modifiers into the
 * entity's NBT), but the **displayed** level would not: it lives in this field. Deriving it back out of a
 * modifier amount at display time would mean reading floats every frame a name tag is drawn, which is
 * why the number is stored separately.
 *
 * <h2>Why {@code MobEntity} and not a subclass</h2>
 * {@code MobEntity} **declares** both methods (verified with {@code javap}: {@code public void
 * writeCustomDataToNbt(NbtCompound)} and the matching read), and every vanilla mob override calls
 * {@code super} — so injecting at {@code TAIL} here runs once per mob, after the subclass has written its
 * own data, and before it returns. Injecting into {@code LivingEntity} instead would also work but would
 * run for players, which have no level.
 *
 * <h2>Read ordering</h2>
 * {@code readCustomDataFromNbt} runs while the entity is being constructed from NBT, i.e. <b>before</b>
 * {@code initialize} (which is only called for genuinely new spawns). A mob loaded from disk therefore
 * arrives with a non-zero level, and {@code MobLevels.ensureLevel}'s "already levelled" guard stops it
 * from being re-rolled with whatever the difficulty happens to be now. That is the behaviour we want:
 * level is a spawn-time property, not a per-load property.
 */
@Mixin(MobEntity.class)
public abstract class MobLevelMixin implements LevelledMob, ElementalMob {

    /** NBT key; namespaced so it cannot collide with a vanilla or other-mod key. */
    private static final String NBT_LEVEL = "simple_tweaks:mob_level";

    /** NBT key for the attack element ordinal; absent = plain mob. */
    private static final String NBT_ELEMENT = "simple_tweaks:mob_element";

    /** `0` = not levelled yet. See {@link LevelledMob}. */
    @Unique
    private int simpletweaks$mobLevel = 0;

    /**
     * Attack element ordinal, or `-1` for "no element". See {@link ElementalMob}.
     *
     * Default `-1` and not `0`: `0` is {@code ElementType.PHYSICAL}, which would make every plain mob
     * look like it had an element and send [dev.firefly.simpletweaks.util.mobs.MobElements] down the
     * "already assigned" path without ever assigning one.
     */
    @Unique
    private int simpletweaks$mobElement = ElementType.NONE_ORDINAL;

    @Override
    public int getMobLevel() {
        return simpletweaks$mobLevel;
    }

    @Override
    public void setMobLevel(int level) {
        this.simpletweaks$mobLevel = level;
    }

    @Override
    public int getMobElement() {
        return simpletweaks$mobElement;
    }

    @Override
    public void setMobElement(int ordinal) {
        this.simpletweaks$mobElement = ordinal;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void simpletweaks$writeMobLevel(NbtCompound nbt, CallbackInfo ci) {
        if (simpletweaks$mobLevel > 0) {
            nbt.putInt(NBT_LEVEL, simpletweaks$mobLevel);
        }
        if (simpletweaks$mobElement != ElementType.NONE_ORDINAL) {
            nbt.putInt(NBT_ELEMENT, simpletweaks$mobElement);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void simpletweaks$readMobLevel(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(NBT_LEVEL)) {
            simpletweaks$mobLevel = nbt.getInt(NBT_LEVEL);
        }
        if (nbt.contains(NBT_ELEMENT)) {
            // Read raw: `ElementType.ofOrdinal` already treats an out-of-range ordinal as "no element",
            // so a save from a build with more elements degrades instead of throwing here.
            simpletweaks$mobElement = nbt.getInt(NBT_ELEMENT);
        }
    }
}
