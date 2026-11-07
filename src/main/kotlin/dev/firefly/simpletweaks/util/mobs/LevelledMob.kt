package dev.firefly.simpletweaks.util.mobs

/**
 * Implemented by `MobLevelMixin` on `net.minecraft.entity.mob.MobEntity`.
 *
 * ## Why an interface and not just a cast to the mixin
 * The level has to be readable from three places that are not the mob's own code: the display-name
 * mixin (on `Entity`), the spawn path (in [MobLevels]) and the enchant path. In each of those the
 * only thing known statically is `MobEntity`, so the field must be reachable through a type that the
 * compiler already trusts. A mixin class cannot be referenced by name from ordinary code (it is not
 * guaranteed to exist at compile time), which is why this interface lives in `util/` and the mixin
 * implements it — the same arrangement `interfaces/SimpleTweaksArrow` uses for projectiles.
 *
 * ## Persistence
 * The value itself is written to and read from the entity's NBT by the mixin (`simple_tweaks:mob_level`),
 * so a chunk reload or a server restart keeps the level. The *stat* modifiers are persisted by vanilla
 * already (attribute modifiers live in the entity's NBT); this field exists so the **displayed** level
 * survives too — reading it back out of a modifier's amount would mean parsing a float on every frame a
 * name tag is drawn.
 *
 * `0` means "not levelled yet", which is how the spawn path detects that it still has to run.
 */
interface LevelledMob {
    var mobLevel: Int
}
