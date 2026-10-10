package dev.firefly.simpletweaks.enchantments

import dev.firefly.simpletweaks.enchantments.annotations.EnchantColor
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.enchantment.Enchantment
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Formatting

/**
 * The single lookup point for enchantment metadata.
 *
 * **All of it comes from one generated file**, [GeneratedEnchantments]: every enchantment is declared with
 * `@ModEnchantment`, and KSP emits `CATEGORY` / `TYPE` / `MAX_LEVEL` / `KEYS` from those declarations. The
 * two-generation bridging this object used to do is gone — there is nothing left to bridge, and that split
 * was precisely what once let `fast_bow` render grey and go missing from the index.
 *
 * ## Why the lookups are string-shaped
 * The generated tables are typed (`EnchantCategory` / `EnchantType`), but every caller — the tooltip
 * mixin and `client/EnchantInfoScreen` — wants the 1.12.2 name as a lowercase string, which is what
 * [EnchantCategory.toString] / [EnchantType.toString] already produce. The enum-ness stops here.
 *
 * [allKeys] is that generated key list and nothing else; nothing may depend on its order beyond "stable",
 * since the index sorts by id itself.
 */
object EnchantmentMeta {

    /** Every known enchantment key. */
    fun allKeys(): List<RegistryKey<Enchantment>> = GeneratedEnchantments.KEYS

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
    fun isRainbow(id: String): Boolean = GeneratedEnchantments.COLOR[id] == EnchantColor.RAINBOW

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
