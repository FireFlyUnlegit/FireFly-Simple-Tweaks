package dev.firefly.simpletweaks.enchantments.annotations

/**
 * Sentinel for [ModEnchantment.weight]: "use the tier's canonical weight".
 *
 * A real weight is always `>= 1` (the processor rejects anything lower), so a negative value is free
 * to mean "derive" — which is the default. See [EnchantItemWeights] for the curve.
 */
const val WEIGHT_FROM_CATEGORY = -1

/**
 * Sentinel for [ModEnchantment.damagePerLevel]: "use the tier's canonical damage".
 *
 * Same reasoning as [WEIGHT_FROM_CATEGORY] — a negative damage per level is meaningless, so it can
 * carry the "derive" meaning. See [ModEnchantment.damagePerLevel] for the rule.
 */
const val DAMAGE_PER_LEVEL_FROM_CATEGORY = -1.0

/**
 * Declares an enchantment on its handler: **one file is the whole definition.**
 *
 * Example:
 * ```kotlin
 * @ModEnchantment(
 *     id = "fast_bow",
 *     category = EnchantCategory.RARE,
 *     type = EnchantType.BOW,
 *     maxLevel = 3, weight = 5, anvilCost = 6,
 *     minCostBase = 20, minCostPerLevel = 10,
 *     supportedItems = "#minecraft:enchantable/bow",
 *     slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
 * )
 * object EnchantFastBowHandler : Listenable { ... }
 * ```
 *
 * The KSP processor generates the datapack JSON, `RegistryKey`, index metadata and the
 * event-bus registration entry. Enchantment name and description stay hand-written in
 * `lang/en_us.json` / `lang/zh_cn.json` as `enchantment.simple_tweaks.<id>` and `.desc`.
 *
 * @see EnchantCategory
 * @see EnchantColor
 * @see EnchantType
 * @see EnchantSlot
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ModEnchantment(
    /** Registry path, JSON file name, lang key suffix. `[a-z0-9_]`. */
    val id: String,

    /** Enchant index grouping. Color defaults to this category's color. */
    val category: EnchantCategory,

    /** "Applies to" text in the index. */
    val type: EnchantType,

    /**
     * Tooltip name color.
     * - [EnchantColor.INHERIT] (default): use [category]'s color.
     * - [EnchantColor.RAINBOW]: per-character rainbow, resolved at runtime.
     * - Any other value: explicit override.
     */
    val color: EnchantColor = EnchantColor.INHERIT,

    /** Maximum level. */
    val maxLevel: Int,

    /**
     * Enchanting-table weight. **Defaults to the tier's canonical value** ([WEIGHT_FROM_CATEGORY]):
     * the processor derives it from [category] with the `EnchantItemWeights` curve, so a declaration
     * only writes this in order to **override** its tier (e.g. `fast_bow = 5`, where every other EPIC
     * is 4). The processor logs every explicit value that disagrees with its table.
     */
    val weight: Int = WEIGHT_FROM_CATEGORY,

    /** `anvil_cost` in the datapack JSON. */
    val anvilCost: Int,

    /** `min_cost.base`. */
    val minCostBase: Int,

    /** `min_cost.per_level_above_first`. */
    val minCostPerLevel: Int = 0,

    /** `supported_items`, e.g. `#minecraft:enchantable/bow`. */
    val supportedItems: String,

    /** `primary_items`. Empty = same as [supportedItems]. */
    val primaryItems: String = "",

    /** `slots`. */
    val slots: Array<EnchantSlot>,

    /** `max_cost.base`. */
    val maxCostBase: Int = 65535,

    /** `max_cost.per_level_above_first`. */
    val maxCostPerLevel: Int = 0,

    /**
     * `exclusive_set` in the datapack JSON — the id of an enchantment **set** this enchantment may not
     * be combined with. Empty (the default) emits no `exclusive_set` key at all.
     *
     * Written either with the leading `#` (`"#simple_tweaks:exclusive_set/elements"`, the canonical
     * form) or without it; a namespaced id gets its `#` added when rendered, because vanilla's own sets
     * are tags (`sharpness` uses `#minecraft:exclusive_set/damage`).
     *
     * ⚠️ **This processor is the only producer of the sets it names.** For
     * `exclusive_set/elements` the tag's membership is derived from the declarations that reference it
     * (see `ModEnchantmentProcessor.writeExclusiveSets`), so hand-writing a matching tag file would
     * collide at the same jar path — the trap `AGENTS.md` §2 warns about for the other generated tags.
     */
    val exclusiveSet: String = "",

    /**
     * Emits the standard `minecraft:damage` component — `linear { base = v, per_level_above_first = v }`,
     * i.e. exactly `+v` damage per level. Default (`DAMAGE_PER_LEVEL_FROM_CATEGORY`) or `0.0` emits no
     * damage component.
     *
     * ## The default is derived, and where the rule comes from
     * Left at [DAMAGE_PER_LEVEL_FROM_CATEGORY], the processor applies the **rarity ladder** —
     * `rarity * 2.5 / 6` on `TIER_RARITY`, i.e. `COMMON 0 / UNCOMMON 0.417 / RARE 0.833 / EPIC 1.25 /
     * LEGENDARY 1.667 / MYTHIC 2.083 / MYSTERY 2.5`, and the weapon types (`SWORD`, `WEAPON`, `HELD`) are
     * the only ones that get it — bows are projectile-only, `BREAKABLE` covers anything.
     *
     * ⚠️ The author's rule is "rarity decides the flat damage", so this is **not** 1.12.2's
     * `0.2 + rarity/20` and not the `build=cleanup21` `rarity × 1.5 / 20` either (that one was SWORD-only
     * and topped out at 0.45, which is why later enchantments looked like they added no damage). The
     * authoritative copy is `tierDamagePerLevel` in `ModEnchantmentProcessor`; this paragraph only
     * describes it, so change both together.
     *
     * ## When to write it
     * Only to **override** the ladder — either down (`combo` 0.15, `fire_master` 0.25) or up
     * (`void_collapse` 3.0). `0.0` is load-bearing for the rider-only enchantments: it means "this one's
     * damage comes from its own code, not from a flat component" (`chain_lightning`, `frostbite`,
     * `infinite_power`, the elemental conversions) — the build log names every such override so they stay
     * deliberate.
     *
     * The other half of the rule lives next door: mining types (`TOOL`) derive mining speed and the armour
     * types derive max health / armour / toughness. See `deriveAttributes` there.
     */
    val damagePerLevel: Double = DAMAGE_PER_LEVEL_FROM_CATEGORY,

    /**
     * `minecraft:attributes` entries, one per attribute. Empty (the default) emits no attributes block.
     *
     * Independent of [damagePerLevel]: when both are set the JSON carries both components.
     */
    val attributes: Array<AttributeSpec> = [],

    /**
     * Registration order. `GeneratedEnchantments.HANDLERS` is sorted by this, and since
     * `ForgeEventBus` sorts by the int `priority` with a **stable** sort, this is what decides dispatch
     * order among listeners that share a priority — i.e. it is *semantically load bearing*, not
     * cosmetic.
     *
     * Default `Int.MAX_VALUE` = appended after everything else, which is what a brand-new KSP
     * enchantment wants. A handler migrated out of `EnchantmentManager.handlerList` must instead carry
     * the position it had there, or its listeners silently move to the end of the bus (that is what once
     * disabled `EnchantCritDamageHandler` on every forced crit).
     */
    val order: Int = Int.MAX_VALUE,

    /**
     * Whether KSP generates the datapack JSON for this declaration.
     *
     * `true` (default): the generated JSON is the definition — delete the hand-written copy under
     * `src/main/resources/data/simple_tweaks/enchantment/`, or the two would land at the same jar path.
     *
     * `false`: the JSON is **not** generated and the hand-written file stays the definition. Everything
     * else is still generated — key, `HANDLERS`, index metadata — which is the point: an enchantment whose
     * `effects` use a component this processor cannot emit yet can still get its key and index entry from
     * the annotation. Nothing else needs to change for that: the hand-written JSON simply stays where it is.
     *
     * ⚠️ **While this is `false`, every datapack field in the annotation — `weight`, `anvilCost`,
     * `minCostBase`, `damagePerLevel`, `supportedItems`, `slots` — is INERT.** The hand-written JSON is
     * what ships, so editing them changes nothing in game and the two silently drift apart. That is not
     * hypothetical: `build=cleanup21` found `heavenly_punishment` declaring `damagePerLevel = 2.5` and
     * `anvilCost = 90` while its JSON shipped `5` and `14`. Treat those fields as documentation while the
     * flag is off, and flip it on as soon as the JSON is expressible.
     *
     * As of `build=cleanup21` exactly **one** declaration needs this flag: `unbreakable`, whose `effects`
     * use `minecraft:item_damage` (`set 0`), which this processor does not emit. Every other legacy
     * enchantment is `{}`, `minecraft:damage` (covered by [damagePerLevel]) or `minecraft:attributes`
     * ([attributes]) — the last four hand-written files (`reforge`, `infinite_power`,
     * `celestial_blessing`, `heavenly_punishment`) were flipped to generated JSONs then, which is what
     * made the drift above impossible to keep.
     */
    val jsonEmit: Boolean = true,
)