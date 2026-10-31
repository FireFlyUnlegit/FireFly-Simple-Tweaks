package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.MobEnhancementConfig;
import dev.firefly.simpletweaks.util.mobs.MobLevelSync;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prefixes a levelled mob's displayed name with {@code [Lv.N]}.
 *
 * <h2>Why {@code getDisplayName} and not {@code setCustomName}</h2>
 * Writing a custom name would have been a one-liner, but {@code hasCustomName()} feeds gameplay:
 * a mob with a custom name does not despawn, and several AI goals branch on it. That would quietly turn
 * every levelled mob into a persistent one. {@code getDisplayName} is a pure read used by the renderer
 * **and** by death messages, so decorating it costs nothing behaviourally and gives the level for free
 * where a player would want to see it.
 *
 * <h2>Override audit (AGENTS.md §4.3)</h2>
 * {@code Entity#getDisplayName} is declared once, on {@code Entity}; the only overrides in the class
 * hierarchy are {@code PlayerEntity}, {@code VillagerEntity}, {@code WanderingTraderEntity} and
 * {@code WitherEntity} (verified by scanning every {@code net/minecraft/entity} class for the name).
 * None of those is a normal hostile mob, so injecting on {@code Entity} covers every mob this feature
 * levels. A Wither, being a boss spawned through its own path, simply never shows a level — acceptable.
 *
 * <h2>Cost</h2>
 * One {@code instanceof}, two config field reads and an {@code int} compare on the common path (which
 * returns the original name untouched). Only a levelled mob pays for the {@code Text} it has to build,
 * and a name tag is only rendered for entities the client is already drawing.
 *
 * <p>Deliberately **not** cached: the decorated text depends on the base name, which a name tag rename
 * can change at any time. Caching it would need invalidation on `setCustomName` for a saving that only
 * shows up when dozens of name tags are on screen at once.
 */
@Mixin(Entity.class)
public abstract class EntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void simpletweaks$prefixMobLevel(CallbackInfoReturnable<Text> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof MobEntity mob)) {
            return;
        }
        if (!MobEnhancementConfig.getEnabled() || !MobEnhancementConfig.getLevelsEnabled()) {
            return;
        }
        // `MobLevelSync` returns the server-side field on the server and the synced value on the client
        // (the client field is always 0 — see its KDoc).
        int level = MobLevelSync.INSTANCE.levelOf(mob);
        // Level 1 is "ordinary mob": prefixing it would put [Lv.1] on every animal in a peaceful world.
        if (level <= 1) {
            return;
        }
        Text base = cir.getReturnValue();
        if (base == null) {
            return;
        }
        cir.setReturnValue(Text.literal("[Lv." + level + "] ").append(base));
    }
}
