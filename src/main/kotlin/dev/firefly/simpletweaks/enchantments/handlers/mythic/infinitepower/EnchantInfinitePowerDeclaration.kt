package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantColor
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * The `@ModEnchantment` declaration for `infinite_power`.
 *
 * ## Why this is its own object, with no listeners
 * `infinite_power` is the one enchantment whose behaviour is not carried by a single listener.
 * `EnchantInfinitePowerHandler` is **not** a `Listenable` — its one-shot kill is invoked directly from
 * `PlayerEntityAttackMixin`'s HEAD seam — and four separate aura handlers (`FlightHandler`,
 * `ForgeHandler`, `SoulBindHandler`, `ToolHandler`) stay in `EnchantmentManager.handlerList` at their
 * original positions 46–49, because they are not the object carrying this declaration. So this file
 * contributes only the definition: the key, the index metadata and the tags.
 *
 * It therefore declares no `order`: there is nothing here to place, and `Int.MAX_VALUE` keeps it out of the
 * ordering table entirely. The aura handlers keep their own numbers.
 *
 * ## The datapack definition is generated (since `build=cleanup21`)
 * This declaration used to be `jsonEmit = false`, because 1.12.2's generator wrote no `primary_items`
 * field for treasure enchantments (`infinite_power` and `reforge` are the two) while this processor
 * always writes it. That reasoning did not survive the bytecode/data check: **the vanilla codec defaults
 * `primary_items` to `supported_items`**, which is exactly what the hand-written file was relying on by
 * omitting the key — the two files that omit it load fine. So the generated JSON is equivalent, the
 * hand-written one is deleted, and the fields below are now the single definition instead of
 * documentation that could drift (it had: `damagePerLevel` and `anvilCost` disagreed on
 * `heavenly_punishment`).
 *
 * ## The rainbow
 * 1.12.2's `EnchantInfinitePower` overrode `decorateName` to animate the name. `color =
 * EnchantColor.RAINBOW` *is* that override, now declared in the same place as every other enchantment's
 * colour — which is what let `EnchantmentMeta.LEGACY_RAINBOW` (a hand-kept set holding exactly this one
 * id) be deleted.
 *
 * Note `minCostBase = 65535`: the annotation default is 1, and 1.12.2 returned `Int.MAX_VALUE` from both
 * cost bounds so that this enchantment can never be rolled at an enchanting table. Omitting it here would
 * silently make it obtainable.
 */
@ModEnchantment(
    id = "infinite_power",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.SWORD,
    color = EnchantColor.RAINBOW,
    maxLevel = 1,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 65535,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    // Explicit opt-out, and load bearing: this is a SWORD, so the tier default would emit
    // `minecraft:damage` +0.45/level. 1.12.2's override returned `Int.MAX_VALUE` as a one-shot-kill hack
    // (see `EnchantInfinitePowerHandler`), not as weapon damage — the enchantment never carried a damage
    // component, and the hand-written JSON this replaced had `effects: {}`.
    damagePerLevel = 0.0,
)
object EnchantInfinitePowerDeclaration : Listenable
