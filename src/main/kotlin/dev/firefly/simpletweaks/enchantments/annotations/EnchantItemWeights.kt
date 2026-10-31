package dev.firefly.simpletweaks.enchantments.annotations

/**
 * Enchanting-table weight per tier, as a **named constant usable as an annotation argument**.
 *
 * ## Where these numbers come from
 * They started out as the 1.12.2 rarity curve, copied from `EnchantmentCategories` in the reference
 * project:
 *
 * ```kotlin
 * // 1.12.2: FireFly's Simple Tweaks-1.12.2/src/main/kotlin/.../baseclass/EnchantmentCategories.kt
 * val weight: Int get() = if (rarity < 0) 0 else (10 - rarity * 1.5.roundToInt()).coerceAtLeast(1)
 * ```
 *
 * `1.5.roundToInt()` is `2`, so the curve is `10 - rarity * 2`, with rarity
 * `UNIQUE -1 / COMMON 0 / UNCOMMON 1 / RARE 2 / EPIC 3 / LEGENDARY 4 / MYTHIC 5 / MYSTERY 6`.
 *
 * **The author has since replaced that curve with a rarity ladder** (one vanilla tier harder for
 * every tier, and a strictly decreasing ladder instead of a `1/1/1` tail):
 *
 * | tier | rarity | 1.12.2 formula | shipped before | **now** |
 * |---|---|---|---|---|
 * | UNIQUE | -1 | `0` (special-cased) | 1 | **1** |
 * | COMMON | 0 | 10 | 10 | **8** |
 * | UNCOMMON | 1 | 8 | 8 | **5** |
 * | RARE | 2 | 6 | 6 | **3** |
 * | EPIC | 3 | 4 | 4 | **2** |
 * | LEGENDARY | 4 | 2 | 2 | **1** |
 * | MYTHIC | 5 | 1 (floored) | 1 | **1** |
 * | MYSTERY | 6 | 1 (floored) | 1 | **1** |
 *
 * Vanilla's own scale for reference: `10` = `sharpness`/`protection`/`efficiency`, `5` =
 * `unbreaking`/`feather_falling`, `2` = `mending`/`fortune`, `1` = `silk_touch`/`swift_sneak`.
 * Measured from the generated JSONs after the change: the mod's total weight is **196** against
 * vanilla's **146**, so its share of a weighted draw goes from 320/(320+146) = **69%** to
 * 196/(196+146) = **57%**. (`UNIQUE` never appears in that sum — it is excluded from the table by
 * category and its shipped file is hand-written.)
 *
 * **The one intentional deviation**: the 1.12.2 formula returns `0` for UNIQUE, and `0` is not usable
 * here — the KSP processor rejects a weight below 1, and every shipped datapack file uses `1`.
 * UNIQUE and MYSTERY are excluded from the enchanting table by **category**
 * (`ModEnchantmentProcessor.NOT_IN_ENCHANTING_TABLE_CATEGORIES`), so their weight is descriptive
 * only and cannot change what the table offers.
 *
 * ⚠️ **Weight is not the whole difficulty story.** It biases *which* eligible enchantment wins a
 * weighted draw. It cannot prevent a draw, and it does not touch the level: that is `min_cost`
 * (`Enchantment#getMinPower`, compared against the roll's power) and `max_cost`
 * (`Enchantment#getMaxPower`, which caps the level the roll may reach).
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
 *
 * Changed again deliberately (author's balance pass) to the `8/5/3/2/1/1` ladder documented above.
 * Unlike the cleanup20 change this one is recorded in `DEVLOG.md`, and **both copies moved together**
 * — the processor's drift report is the only automatic guard, and it can only catch a mismatch if the
 * two tables are edited in the same commit.
 */
object EnchantItemWeights {
    const val UNIQUE = 1
    const val COMMON = 8
    const val UNCOMMON = 5
    const val RARE = 3
    const val EPIC = 2
    const val LEGENDARY = 1
    const val MYTHIC = 1
    const val MYSTERY = 1
}
