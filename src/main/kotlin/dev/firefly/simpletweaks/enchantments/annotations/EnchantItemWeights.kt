package dev.firefly.simpletweaks.enchantments.annotations

/**
 * Enchanting-table weight per tier, as a **named constant usable as an annotation argument**.
 *
 * ## Where these numbers come from
 * They are the 1.12.2 rarity curve, copied from `EnchantmentCategories` in the reference project:
 *
 * ```kotlin
 * // 1.12.2: FireFly's Simple Tweaks-1.12.2/src/main/kotlin/.../baseclass/EnchantmentCategories.kt
 * val weight: Int get() = if (rarity < 0) 0 else (10 - rarity * 1.5.roundToInt()).coerceAtLeast(1)
 * ```
 *
 * `1.5.roundToInt()` is `2`, so the curve is `10 - rarity * 2`, with rarity
 * `UNIQUE -1 / COMMON 0 / UNCOMMON 1 / RARE 2 / EPIC 3 / LEGENDARY 4 / MYTHIC 5 / MYSTERY 6`:
 *
 * | tier | rarity | 1.12.2 formula | value here |
 * |---|---|---|---|
 * | UNIQUE | -1 | `0` (special-cased) | **1** |
 * | COMMON | 0 | 10 | **10** |
 * | UNCOMMON | 1 | 8 | **8** |
 * | RARE | 2 | 6 | **6** |
 * | EPIC | 3 | 4 | **4** |
 * | LEGENDARY | 4 | 2 | **2** |
 * | MYTHIC | 5 | 1 (floored) | **1** |
 * | MYSTERY | 6 | 1 (floored) | **1** |
 *
 * **The one intentional deviation**: the 1.12.2 formula returns `0` for UNIQUE, and `0` is not usable
 * here — the KSP processor rejects a weight below 1, and every shipped datapack file uses `1`.
 * UNIQUE and MYSTERY are excluded from the enchanting table by **category**
 * (`ModEnchantmentProcessor.NOT_IN_ENCHANTING_TABLE_CATEGORIES`), so their weight is descriptive
 * only and cannot change what the table offers.
 *
 * ## Do you need to write it at all?
 * **No.** `@ModEnchantment.weight` defaults to *"derive from the category"* and the processor applies
 * exactly this table. These constants exist for the two cases where the value must be written out:
 *  - **overriding** the tier's weight (e.g. `fast_bow = 5` while every other EPIC is 4);
 *  - **documenting** a value the reader would otherwise have to look up.
 *
 * ⚠️ **A constant is duplicated here, not derived.** An annotation argument must be a compile-time
 * constant, so `EnchantCategory` cannot supply these numbers, and the processor cannot import them
 * (it is a separate module that reads enum *names*). If you change a number here, change
 * `TIER_WEIGHTS` in `ModEnchantmentProcessor` in the same commit — the processor logs every explicit
 * value that disagrees with its table, which is what turns this drift visible instead of silent.
 *
 * ## History (why this file reads the way it does)
 * Introduced as `10/8/6/4/2/1` (the faithful curve). The `build=cleanup20` commit changed the first
 * five to `20/15/10/6/3/1` without a DEVLOG entry, which broke the invariant "the named constant is
 * the tier's canonical weight". Because 48 of the 67 declarations had already been switched to the
 * constants, that change silently doubled most enchanting-table odds the next time those files were
 * built. Restored in `build=cleanup21`; the evidence is in `DEVLOG.md`.
 */
object EnchantItemWeights {
    const val UNIQUE = 1
    const val COMMON = 10
    const val UNCOMMON = 8
    const val RARE = 6
    const val EPIC = 4
    const val LEGENDARY = 2
    const val MYTHIC = 1
    const val MYSTERY = 1
}
