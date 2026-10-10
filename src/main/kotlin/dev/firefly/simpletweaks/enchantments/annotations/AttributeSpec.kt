package dev.firefly.simpletweaks.enchantments.annotations

/**
 * One `minecraft:attributes` entry of a `@ModEnchantment` declaration.
 *
 * Used **nested** inside [ModEnchantment] — `attributes = [AttributeSpec("minecraft:generic.armor", 0.04)]` —
 * which is why nothing here is annotated with a `@Target`: this class is a parameter type, never applied
 * to a declaration of its own.
 *
 * ```kotlin
 * @ModEnchantment(
 *     id = "prismatic_blessing",
 *     // ...
 *     attributes = [AttributeSpec("minecraft:generic.max_health", 0.04)],
 * )
 * ```
 *
 * The generated datapack entry is the same one `tools/gen-enchantments.ps1` used to write by hand:
 *
 * ```json
 * {
 *   "amount": { "type": "minecraft:linear", "base": 0.04, "per_level_above_first": 0.04 },
 *   "attribute": "minecraft:generic.max_health",
 *   "id": "simple_tweaks:enchantment.<enchantment id>.<last segment of attribute>",
 *   "operation": "add_multiplied_base"
 * }
 * ```
 *
 * The `id` is derived, not passed: vanilla requires one per entry so that a single item cannot stack the
 * same attribute modifier twice, and `<enchantment>.<attribute>` is both unique and readable.
 */
annotation class AttributeSpec(
    /** Attribute id, e.g. `minecraft:generic.max_health` or `minecraft:player.block_break_speed`. */
    val attribute: String,

    /** Amount added per level; used for both `base` and `per_level_above_first`. */
    val perLevel: Double,

    /** `add_multiplied_base` / `add_value` / `add_multiplied_total`. */
    val operation: String = "add_multiplied_base",
)
