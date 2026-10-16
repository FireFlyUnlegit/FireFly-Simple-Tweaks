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
     * Emits the standard `minecraft:damage` component — `linear { base = v, per_level_above_first = v }`,
     * i.e. exactly `+v` damage per level. Default (`DAMAGE_PER_LEVEL_FROM_CATEGORY`) or `0.0` emits no
     * damage component.
     *
     * ## The default is derived, and where the rule comes from
     * Left at [DAMAGE_PER_LEVEL_FROM_CATEGORY], the processor applies **its own tier curve** —
     * `rarity * 1.5 / 20` on `TIER_RARITY`, i.e. `COMMON 0 / UNCOMMON 0.075 / RARE 0.15 / EPIC 0.225 /
     * LEGENDARY 0.3 / MYTHIC 0.375 / MYSTERY 0.45`, and **`EnchantType.SWORD` is the only type that
     * gets any** (every other type is `0.0`, exactly as in the reference project).
     *
     * ⚠️ That curve is a `build=cleanup21` rebalance, **not** 1.12.2's `0.2 + rarity/20` (which the
     * port originally reproduced value-for-value). The authoritative copy is `tierDamagePerLevel` in
     * `ModEnchantmentProcessor`; this paragraph only describes it, so change both together.
     *
     * ## When to write it
     * Only to **override** the tier. 1.12.2 let an enchantment override `itemExtraDamage`, and six did:
     * `combo` (0.15), `fire_master` (0.25), `healing_blade` (1.0), `celestial_blessing` (2.0),
     * `heavenly_punishment` (5.0), plus `infinite_power`, whose override returned `Int.MAX_VALUE` as a
     * one-shot-kill hack and therefore carries `damagePerLevel = 0.0` — writing a *damage* there would
     * invent a component the enchantment never had. A declaration that wants no damage can say so
     * explicitly with `0.0`.
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
     * `ForgeEventBus` sorts by `EventPriority` with a **stable** sort, this is what decides dispatch
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