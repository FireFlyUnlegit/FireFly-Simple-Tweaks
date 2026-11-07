package dev.firefly.simpletweaks.enchantments.handlers.element

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * The eight `xx_conversion` and eight `xx_protection` declarations.
 *
 * ## Why every one of these is a *pure declaration* with no `@SubscribeEvent`
 * All the behaviour lives in one engine, `util/elements/ElementCombat`, which is registered once from
 * `SimpleTweaks.onInitialize`. Sixteen handlers each listening to `LivingHurtEvent` /
 * `LivingDamageEvent` would run sixteen element lookups per hit — and, more importantly, would be
 * sixteen places for the "which element is this?" rule to drift. A conversion enchantment's entire
 * definition is its **element**, its **level curve** and its **cost**, which is exactly what an
 * annotation plus a table entry is.
 *
 * ⚠️ A pure declaration is not a warning-free one: `ForgeEventBus.register` logs
 * `No @SubscribeEvent methods found on …` for every object it registers without listeners. Sixteen of
 * those lines on every startup is noise that will make a real problem hard to spot, so these
 * declarations are registered **for their keys only** — see the `ELEMENT_HANDLERS` note in
 * `EnchantmentManager`. They still appear in `GeneratedEnchantments.HANDLERS` (KSP puts one entry per
 * declaration in there), so `initHandlers` must skip them explicitly.
 *
 * ## Element → enchantment mapping
 * | element | conversion | protection |
 * |---|---|---|
 * | physical | `physical_conversion` | `physical_protection` |
 * | fire | `fire_conversion` | `fire_protection` |
 * | frost | `frost_conversion` | `frost_protection` |
 * | lightning | `lightning_conversion` | `lightning_protection` |
 * | water | `water_conversion` | `water_protection` |
 * | nature | `nature_conversion` | `nature_protection` |
 * | dark | `dark_conversion` | `dark_protection` |
 * | light | `light_conversion` | `light_protection` |
 *
 * ⚠️ **`fire_protection` collides with a vanilla enchantment id.** Vanilla has
 * `minecraft:fire_protection`; this mod's is `simple_tweaks:fire_protection`. Different namespaces, so
 * no registry clash, but the tooltip and `/enchant` listing show two enchantments with the same
 * translated name unless the lang strings differ. The lang for this mod's one says
 * "Fire Affinity" /「火焰亲和」rather than「火焰保护」for exactly that reason. The registry id stays
 * `fire_protection` so the `<element>_protection` convention in `ElementType` holds.
 *
 * ## Plus one that belongs to no element
 * `elemental_mastery`（元素精通）is the sixteenth-plus-one declaration and the only one that is *not*
 * keyed to an `ElementType`: it multiplies the output of **whichever** element a conversion produces
 * (+10% per level, capped at +200%), and it is in no exclusive set. See the note above its declaration.
 */
object ElementEnchantments {

    /** The shared `exclusive_set` tag: an item can carry at most one of the eight conversions. */
    const val ELEMENT_EXCLUSIVE_SET = "#simple_tweaks:exclusive_set/elements"

    /**
     * Every element handler object, so `EnchantmentManager` can keep them off the event bus while still
     * letting KSP register their keys and data.
     */
    val ALL: List<Listenable> = listOf(
        PhysicalConversion, FireConversion, FrostConversion, LightningConversion,
        WaterConversion, NatureConversion, DarkConversion, LightConversion,
        PhysicalProtection, FireProtection, FrostProtection, LightningProtection,
        WaterProtection, NatureProtection, DarkProtection, LightProtection,
        ElementalMastery,
    )
}

/*
 * ---------------------------------------------------------------- conversions
 *
 * A conversion is a SWORD enchantment. `maxLevel = 5` for every element keeps the fraction cap
 * (0.10 + 0.05 × 4 = 0.30 at V) well under `MAX_FRACTION`, so the level the player can actually reach
 * is the one the curve was tuned for; reaching 0.50 is only possible by editing the table.
 *
 * Weights: EPIC for all eight. They are mutually exclusive, so the *effective* value of one is its
 * share of the EPIC pool spread over eight ids — a COMMON or RARE weight here would make the pool they
 * compete in worse, not better, once exclusivity is taken into account.
 */

@ModEnchantment(
    id = "physical_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object PhysicalConversion : Listenable

@ModEnchantment(
    id = "fire_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object FireConversion : Listenable

@ModEnchantment(
    id = "frost_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object FrostConversion : Listenable

@ModEnchantment(
    id = "lightning_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object LightningConversion : Listenable

@ModEnchantment(
    id = "water_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object WaterConversion : Listenable

@ModEnchantment(
    id = "nature_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object NatureConversion : Listenable

@ModEnchantment(
    id = "dark_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object DarkConversion : Listenable

@ModEnchantment(
    id = "light_conversion",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    exclusiveSet = ElementEnchantments.ELEMENT_EXCLUSIVE_SET,
)
object LightConversion : Listenable

/*
 * ---------------------------------------------------------------- protections
 *
 * Armour enchantments, RARE, maxLevel 5. Four pieces at V = 20 pieces-of-level, and
 * `PROTECTION_PER_LEVEL = 0.04` caps at 0.80 exactly like `magic_protection`, so a full elemental set
 * is 80% against one element — the same promise the existing magic protection makes.
 *
 * ⚠️ Protections are **not** in the exclusive set. Wearing fire protection boots and a frost
 * protection chest is the intended way to build a set; only the *conversions* exclude each other.
 */

@ModEnchantment(
    id = "physical_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object PhysicalProtection : Listenable

@ModEnchantment(
    id = "fire_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object FireProtection : Listenable

@ModEnchantment(
    id = "frost_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object FrostProtection : Listenable

@ModEnchantment(
    id = "lightning_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object LightningProtection : Listenable

@ModEnchantment(
    id = "water_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object WaterProtection : Listenable

@ModEnchantment(
    id = "nature_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object NatureProtection : Listenable

@ModEnchantment(
    id = "dark_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object DarkProtection : Listenable

@ModEnchantment(
    id = "light_protection",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 10,
    minCostBase = 4,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object LightProtection : Listenable

/*
 * ---------------------------------------------------------------- mastery
 *
 * `elemental_mastery` is deliberately **not** in `ELEMENT_EXCLUSIVE_SET`: it is the one element
 * enchantment that stacks with a conversion instead of competing with it. Making it exclusive would
 * force the player to choose between "convert" and "mastery", which removes the whole point of a
 * multiplier on conversion's output.
 *
 * Its bonus is applied by `ElementCombat` to the **elemental share and to reactions**, never to the
 * cancelled/physical share, so it cannot degenerate into a generic damage multiplier that competes with
 * `sharpness`.
 *
 * ```
 * Level curve: +10% per level, capped at +200% (×3) by `ElementCombat.MASTERY_CAP`. At the attainable
 * maximum (V) that is **+50%** to elemental output — significant but not a one-shot, in keeping with
 * the conversion tuning notes.
 *
 * ⚠️ `damagePerLevel = 0.0` is **load-bearing**, not decoration. Its LEGENDARY tier would otherwise
 * derive `minecraft:damage` at `rarity × 1.5 / 20 = 0.3` per level (see `ModEnchantmentProcessor`
 * `tierDamagePerLevel`), i.e. the enchantment would ALSO add +1.5 flat melee damage at V. That
 * contradicts the whole design: mastery is supposed to raise *elemental* output, and a flat physical
 * bonus would make it a plain `sharpness` competitor that silently overrides its own stated purpose.
 * The explicit `0.0` is exactly the "opt out of the derived component" escape hatch the annotation
 * documents.
 */

@ModEnchantment(
    id = "elemental_mastery",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.SWORD,
    maxLevel = 5,
    // No `minecraft:damage` component: elemental damage only. See the note above.
    damagePerLevel = 0.0,
    weight = EnchantItemWeights.LEGENDARY,
    anvilCost = 12,
    minCostBase = 30,
    minCostPerLevel = 12,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
)
object ElementalMastery : Listenable
