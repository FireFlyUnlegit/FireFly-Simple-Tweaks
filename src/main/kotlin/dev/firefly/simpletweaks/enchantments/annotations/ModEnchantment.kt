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
)