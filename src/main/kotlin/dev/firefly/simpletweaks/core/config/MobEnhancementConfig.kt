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
 * | [levelsEnabled] | `util/mobs/MobLevels#ensureLevel` (spawn) and `EntityDisplayNameMixin` (name tag) |
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
}
