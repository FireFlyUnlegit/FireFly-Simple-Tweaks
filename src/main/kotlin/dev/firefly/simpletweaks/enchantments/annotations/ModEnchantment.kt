package dev.firefly.simpletweaks.enchantments.annotations

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

    /** Enchanting-table weight. */
    val weight: Int,

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
     * i.e. exactly `+v` damage per level. `0.0` (default) emits no damage component.
     *
     * Explicit per-enchantment (not derived from [category]) to avoid duplicating the
     * `0.2 + rarity/20` rule in two places.
     */
    val damagePerLevel: Double = 0.0,

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
     * As of the 1.21.1 port this is **not needed by any of the 53 legacy enchantments**: their `effects`
     * are either `{}`, `minecraft:damage`, or `minecraft:attributes` — the first two are covered by
     * [damagePerLevel] and the third by [attributes]. It exists for the next exotic one.
     */
    val jsonEmit: Boolean = true,
)