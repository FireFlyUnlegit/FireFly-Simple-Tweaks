package dev.firefly.simpletweaks.enchantments.annotations

/**
 * Declares an enchantment on its handler: **one file is the whole definition.**
 *
 * ```kotlin
 * @ModEnchantment(
 *     id = "fast_bow",
 *     category = "rare",
 *     type = "bow",
 *     maxLevel = 3,
 *     weight = 5,
 *     anvilCost = 6,
 *     minCostBase = 20,
 *     minCostPerLevel = 10,
 *     supportedItems = "#minecraft:enchantable/bow",
 *     slots = ["mainhand", "offhand"],
 * )
 * object EnchantFastBowHandler : Listenable { ... }
 * ```
 *
 * The KSP processor (`ksp-processor/`) turns this into
 *
 *  - `resources/data/simple_tweaks/enchantment/<id>.json` — the datapack definition,
 *  - `GeneratedEnchantments.<ID>` / `.KEYS` — the `RegistryKey`,
 *  - `.CATEGORY` / `.TYPE` / `.COLOR` / `.MAX_LEVEL` — the enchant index metadata,
 *  - `.HANDLERS` — the event-bus registration list, so the handler is **not** hand-added to
 *    `EnchantmentManager`.
 *
 * The enchantment **name and description stay hand-written** in `lang/en_us.json` and
 * `lang/zh_cn.json` as `enchantment.simple_tweaks.<id>` and `.desc`; text is the one thing an
 * annotation cannot carry readably.
 *
 * ## Why this annotation lives in the mod rather than in the processor module
 * So a handler file needs no import from a build-only module. The processor looks it up by
 * fully-qualified name as a string — which also means renaming or moving this class silently stops
 * generation; `MIGRATION.md` §15 records that trade-off and the self-check that catches it.
 *
 * ## Retention
 * [AnnotationRetention.SOURCE]: the annotation is consumed at build time and must not end up in the
 * mod jar. It is not a runtime marker.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ModEnchantment(
    /** Enchantment id: the registry path, the JSON file name and the lang key suffix. `[a-z0-9_]`. */
    val id: String,

    /** 1.12.2 `EnchantmentCategories` name, lowercased — the enchant index groups by this. */
    val category: String,

    /** 1.12.2 `ModEnchantmentType` name, lowercased — the index prints it as "applies to". */
    val type: String,

    /** Tooltip name colour (`Formatting` name). Empty = derive from [category]. */
    val color: String = "",

    /** Maximum level. Also written to the datapack JSON. */
    val maxLevel: Int,

    /** Enchanting-table weight, as in the datapack JSON. */
    val weight: Int,

    /** `anvil_cost` in the datapack JSON. */
    val anvilCost: Int,

    /** `min_cost.base`. */
    val minCostBase: Int,

    /** `min_cost.per_level_above_first`. */
    val minCostPerLevel: Int = 0,

    /** `supported_items`, an item-tag id such as `#minecraft:enchantable/bow`. */
    val supportedItems: String,

    /** `primary_items`. Empty = same as [supportedItems], which is what the old generator did. */
    val primaryItems: String = "",

    /** `slots`, e.g. `["mainhand", "offhand"]`. */
    val slots: Array<String>,

    /** `max_cost.base`. */
    val maxCostBase: Int = 65535,

    /** `max_cost.per_level_above_first`. */
    val maxCostPerLevel: Int = 0,
)
