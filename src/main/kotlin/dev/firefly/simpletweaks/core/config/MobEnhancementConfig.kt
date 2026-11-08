package dev.firefly.simpletweaks.core.config

/**
 * Mob-enhancement options: making hostile mobs harder, without making them farmable.
 *
 * Same Forge → Fabric mapping as [GeneralConfig] (see that file for the rationale); this is a new
 * section rather than a 1.12.2 port, so its defaults are chosen here rather than copied from an
 * annotation.
 *
 * <h2>Who reads what</h2>
 * | key | read by |
 * |---|---|
 * | [enabled] | `util/mobs/MobLevels`, `util/mobs/MobEnchantments`, `EntityDisplayNameMixin` — master switch |
 * | [enchantmentsEnabled] | `util/mobs/MobEnchantments#tryApplyAtSpawn` — gates the gear enchanting only |
 * | [levelsEnabled] | `util/mobs/MobLevels#ensureLevel` (spawn), `util/mobs/MobLevelProgression` (mid-fight level-up) and `EntityDisplayNameMixin` (name tag) |
 * | [levelUpOnHitPlayer] / [levelUpOnKill] / [levelUpWhenHurt] | `util/mobs/MobLevelProgression` only — three independent trigger chances in percent |
 *
 * ⚠️ **[enabled] does NOT gate the drop-side stripping.** `MobEnchantments#stripAll` runs
 * unconditionally, and that is deliberate: a world that was played with the feature on can still hold
 * mobs whose equipment carries mod enchantments, and turning the feature off must not turn those into
 * farmable loot. The strip is one component read per occupied slot at death — cheaper than the
 * bookkeeping needed to know whether a given mob predates the switch.
 */
object MobEnhancementConfig {

    /**
     * Master switch for mob enhancement (`/stconfig` page 5).
     *
     * Default **true**: the feature is opt-out, matching how the rest of this mod's combat features ship.
     */
    @JvmStatic
    var enabled: Boolean = true

    /**
     * Let mob gear carry this mod's enchantments at spawn.
     *
     * Default **true**. Excludes MYTHIC/MYSTERY/UNIQUE categories (see `MobEnchantments`), so
     * `infinite_power` / `kill_aura` / `unbreakable` can never appear on a mob.
     */
    @JvmStatic
    var enchantmentsEnabled: Boolean = true

    /**
     * Give mobs a level derived from local difficulty (`MobLevels`), raising max health, attack damage
     * and armour.
     *
     * Default **true**. Read by `MobLevels.ensureLevel` (the spawn path) and by the display-name mixin
     * (`EntityDisplayNameMixin`) — turning it off hides the `[Lv.N]` prefix as well, because a name that
     * advertises a level the mob does not have would be worse than no name at all.
     *
     * Levels already applied to existing mobs **stay** after turning this off: the modifiers are
     * persistent by design (see `MobLevels`), and silently weakening every loaded mob would be a
     * surprising side effect of a config toggle.
     */
    @JvmStatic
    var levelsEnabled: Boolean = true

    /**
     * Give some mobs a random element: part of their melee hit is converted into it, and they resist it
     * (`MobElements`).
     *
     * Default **true**. Read by `MobElements.tryAssign` (assignment), `MobElements.resistanceFor`
     * (incoming damage) and `ElementCombat` (the attacker branch of `LivingHurtEvent`).
     *
     * The element is stored per mob and persists, so turning this off stops new assignments but does not
     * strip an existing fire zombie of its element — same reasoning as [levelsEnabled].
     */
    @JvmStatic
    var elementsEnabled: Boolean = true

    /**
     * Level-up chance **in percent** when the mob lands a hit on a player (`/stconfig` page 5, 0-100).
     *
     * Read by `MobLevelProgression#onMobHitsPlayer`. `0` disables this trigger without touching the
     * other two — the three are separate knobs on purpose, because "mobs that fight back grow" and
     * "mobs that farm kills grow" are different designs.
     */
    @JvmStatic var levelUpOnHitPlayer: Int = 3

    /** Level-up chance **in percent** when the mob kills any living entity (0 = off). */
    @JvmStatic var levelUpOnKill: Int = 10

    /** Level-up chance **in percent** when a player hurts the mob and it survives (0 = off). */
    @JvmStatic var levelUpWhenHurt: Int = 1

    /**
     * A **persistent** difficulty bonus, added on top of everything else (`/fst difficulty modify`).
     *
     * `v2.2.4 build=cleanup27`. Distinct from `MobLevels.difficultyOffset`, which is deliberately
     * session-only ("a tuning aid should not outlive the session"): this one is written to
     * `config/simple_tweaks.json` and survives restarts, because the author wanted a way to nudge the
     * whole world's difficulty permanently.
     *
     * Read by `MobLevels.baseDifficulty` (every mob's spawn-time difficulty) and by
     * `/fst difficulty check`.
     */
    var difficultyModifier: Float = 0f
}
