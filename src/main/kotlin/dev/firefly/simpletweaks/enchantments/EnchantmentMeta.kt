package dev.firefly.simpletweaks.enchantments

import dev.firefly.simpletweaks.enchantments.annotations.EnchantColor
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.enchantment.Enchantment
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Formatting

/**
 * The single lookup point for enchantment metadata.
 *
 * **All of it now comes from one generated file**, [GeneratedEnchantments]: KSP merges the
 * `@ModEnchantment` declarations with `tools/legacy-enchantments.json` — the 1.12.2-derived half, written
 * once by `tools/gen-enchantments.ps1` — and emits `CATEGORY` / `TYPE` / `MAX_LEVEL` covering both. That
 * is why the two-generation bridging this object used to do is gone: there is nothing left to bridge, and
 * the split was precisely what once let `fast_bow` render grey and go missing from the index.
 *
 * ## Why the lookups are string-shaped
 * The generated tables are typed (`EnchantCategory` / `EnchantType`), but every caller — the tooltip
 * mixin and `client/EnchantInfoScreen` — wants the 1.12.2 name as a lowercase string, which is what
 * [EnchantCategory.toString] / [EnchantType.toString] already produce. The enum-ness stops here.
 *
 * Only the enchantment **keys** still come from the PS1 generator ([ModEnchantmentKeys]): the 54 legacy
 * handlers reference their keys by name and those declarations predate KSP. [allKeys] is the union of
 * both, legacy first then annotated; nothing may depend on that order beyond "stable".
 */
object EnchantmentMeta {

    /**
     * The one 1.12.2 `decorateName` override that no colour can express, because the name is animated
     * rather than coloured — see `InfinitePowerRainbow` and `EnchantmentNameColorMixin`.
     *
     * `infinite_power` is still generator-era, so it has no `@ModEnchantment` declaration to carry
     * `color = EnchantColor.RAINBOW`; this set keeps the behaviour alive in the meantime. When it is
     * migrated, delete the entry and declare the colour on the handler instead — [isRainbow] consults
     * both, so that is a one-line change with no other caller to touch.
     */
    private val LEGACY_RAINBOW = setOf("infinite_power")

    /** Every known enchantment key, legacy + `@ModEnchantment`. */
    fun allKeys(): List<RegistryKey<Enchantment>> = ModEnchantmentKeys.ALL + GeneratedEnchantments.KEYS

    /** 1.12.2 `EnchantmentCategories` name, lowercased; `null` if unknown. */
    fun category(id: String): String? = GeneratedEnchantments.CATEGORY[id]?.toString()

    /** 1.12.2 `ModEnchantmentType` name, lowercased; `null` if unknown. */
    fun type(id: String): String? = GeneratedEnchantments.TYPE[id]?.toString()

    /** Max level; `null` if unknown. Callers that need a number use `?: 1`, as before. */
    fun maxLevel(id: String): Int? = GeneratedEnchantments.MAX_LEVEL[id]

    /**
     * The [EnchantColor] that applies, or `null` for an id no table knows about.
     *
     * `GeneratedEnchantments.COLOR` only holds *explicit* colours, so a declaration that leaves `color`
     * at its default ([EnchantColor.INHERIT]) is resolved here to its category's colour — which is what
     * keeps `COLOR` a pure "was it overridden?" table. The legacy half has no colour of its own either,
     * so it always takes this path; that is why the separate colour table could be deleted.
     */
    fun color(id: String): EnchantColor? =
        GeneratedEnchantments.COLOR[id] ?: GeneratedEnchantments.CATEGORY[id]?.color

    /**
     * `true` when the displayed name must be rendered as a per-character rainbow instead of a flat
     * colour. Checked by `EnchantmentNameColorMixin` **before** [colorOf], because [EnchantColor.RAINBOW]
     * carries no `Formatting` and would otherwise fall through.
     *
     * `@JvmStatic` because the caller is the Java mixin — a Kotlin `object` member is not static from
     * Java's point of view, and without this the call does not compile ("cannot reference a non-static
     * method from a static context").
     */
    @JvmStatic
    fun isRainbow(id: String): Boolean =
        GeneratedEnchantments.COLOR[id] == EnchantColor.RAINBOW || id in LEGACY_RAINBOW

    /**
     * `Formatting` for the enchantment's displayed name, or `null` to leave the vanilla colour alone.
     *
     * Both the tooltip mixin and the enchant index go through here, which is the point: reading a colour
     * table directly is how `fast_bow`, declared `color = EnchantColor.BLUE` at the time, once rendered in
     * the default grey — indistinguishable from a COMMON enchantment.
     */
    @JvmStatic
    fun colorOf(id: String): Formatting? = color(id)?.formatting
}
