package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.compat.FSTLog;
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig;
import dev.firefly.simpletweaks.util.mobs.MobLevelSync;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Makes a levelled mob's name tag actually render.
 *
 * <h2>The bug this fixes — and why the first attempt did not</h2>
 * `EntityDisplayNameMixin` (common) replaces `Entity#getDisplayName` with a `[Lv.N]`-prefixed text, but
 * nothing draws it: vanilla only renders a mob's label when the renderer says so. `build=cleanup46`
 * tried to force that by injecting into `EntityRenderer#hasLabel` and **nothing happened**, because
 * `hasLabel` is overridden twice and the base declaration is never executed for a mob
 * (`AGENTS.md` §4.3, first static gate — a mixin rewrites only its own class's body):
 *
 * <pre>
 *   EntityRenderer#hasLabel(T)                 -> called via invokevirtual only for renderers with no override
 *   LivingEntityRenderer#hasLabel(T)           -> overrides; distance / sneaky / invisibility / team rules
 *   MobEntityRenderer#hasLabel(T)              -> overrides again; super &amp;&amp; (shouldRenderName || custom-name)
 *   MobEntityRenderer#hasLabel(LivingEntity)   -> bridge -> hasLabel(MobEntity)
 *   MobEntityRenderer#hasLabel(Entity)         -> bridge -> hasLabel(MobEntity)
 * </pre>
 *
 * So the injected method was dead code: the game always dispatched to `MobEntityRenderer`.
 *
 * <h2>Why `shouldRenderName` and not a renderer subclass</h2>
 * The render path calls `hasLabel` **virtually** (`EntityRenderer#render`, `invokevirtual hasLabel(Entity)`),
 * so a mob goes to `MobEntityRenderer#hasLabel(MobEntity)`, whose bytecode is:
 *
 * <pre>
 *   if (!super.hasLabel(mob)) return false;                      // LivingEntityRenderer: distance,
 *                                                                // sneaky, invisibility, team rules —
 *                                                                // it never calls shouldRenderName
 *   if (mob.shouldRenderName()) return true;                     // &lt;-- the leaf this mixin forces
 *   return mob.hasCustomName() &amp;&amp; mob == dispatcher.targetedEntity;
 * </pre>
 *
 * `LivingEntity#shouldRenderName` (bytecode: `return isCustomNameVisible()`) is declared **once** — mobs
 * do not override it, only `PlayerEntity` does — so forcing it true for a levelled mob makes that one
 * condition true without touching a renderer class at all. Vanilla's distance / sneaky / team visibility
 * rules still apply through `super`, which is what should happen. `build=cleanup47`.
 *
 * <p>The alternative (injecting into `MobEntityRenderer#hasLabel(MobEntity)`) would work for vanilla
 * mobs but silently stop working for any renderer that overrides `hasLabel` again — the exact failure
 * being fixed here. A `javap` scan of 1.21.1 shows the method is overridden only by
 * `LivingEntityRenderer`, `MobEntityRenderer`, `ArmorStandEntityRenderer` and `ItemFrameEntityRenderer`.
 *
 * <h2>Why the config gate is here</h2>
 * Same gate as `EntityDisplayNameMixin`: if levels are switched off, no label is forced, so a mob cannot
 * show a level tag while the feature is disabled.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRenderNameMixin {

    /**
     * Entity ids already reported, so the probe logs once per mob instead of once per frame.
     *
     * Capped at [MAX_REPORTED]: the client sees thousands of entity ids in a long session and this is
     * debug output, not an audit trail.
     */
    @Unique
    private static final Set<Integer> simpletweaks$reportedNames = new HashSet<>();

    @Unique
    private static final int MAX_REPORTED = 64;

    @Inject(method = "shouldRenderName", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$forceNameForLevelledMob(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof MobEntity mob)) {
            return;
        }
        if (!MobEnhancementConfig.getEnabled() || !MobEnhancementConfig.getLevelsEnabled()) {
            return;
        }
        // Client-side field is always 0; the value comes from the sync packet (see `MobLevelSync`).
        int level = MobLevelSync.INSTANCE.levelOf(mob);
        if (level <= 1) {
            return;
        }
        cir.setReturnValue(true);

        // `[FST-MobLevel] outcome=name-forced` — proves the gate was reached *and* which level the
        // client actually holds. Without it "no nametag" cannot be split into "never synced" versus
        // "synced but not rendered", which is what cost a whole build (`cleanup46`).
        if (FSTLog.INSTANCE.getEnabled()
            && simpletweaks$reportedNames.size() < MAX_REPORTED
            && simpletweaks$reportedNames.add(mob.getId())) {
            FSTLog.INSTANCE.log(
                "MobLevel",
                "entity=" + mob.getType().getName().getString()
                    + ", entityId=" + mob.getId()
                    + ", level=" + level
                    + ", outcome=name-forced"
            );
        }
    }
}
