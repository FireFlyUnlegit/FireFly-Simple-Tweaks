package dev.firefly.simpletweaks.util.damagesource

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.entity.damage.DamageType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * The mod's own damage types that are **not** elements.
 *
 * ## `reflection`
 * 1.12.2's `runPlayerAttack(..., ignoreArmorAndPotion = true)` skipped armour and potion reduction by
 * calling the accessor-level pipeline directly (`applyArmorCalculations` /
 * `applyPotionDamageCalculations`). Neither exists in 1.21, so the ported shared helper cannot honour
 * that flag — and Echo Shield's two reflection call sites *do* pass `true`, meaning the reflected
 * damage was being reduced by the attacker's armour where 1.12.2 reflected it raw.
 *
 * ## The fix (data-driven, no mixin)
 * 1.21 lets a damage type declare that it skips those stages, via vanilla damage-type tags. This mod
 * therefore ships its own type `simple_tweaks:reflection` and adds it to **three** vanilla tags:
 *
 * | tag | reproduces |
 * |---|---|
 * | `#minecraft:bypasses_armor` | the armour half of `ignoreArmorAndPotion` |
 * | `#minecraft:bypasses_resistance` | the potion (resistance) half |
 * | `#minecraft:bypasses_enchantments` | 1.12.2 folded Protection into the armour calculation, so it was skipped too |
 *
 * No vanilla type can express all three: `magic`/`generic` only bypass armour, while the only types
 * in `bypasses_resistance` are `generic_kill` and `out_of_world`, which also bypass invulnerability
 * (far too strong for a reflection). Hence a custom type rather than reusing `DamageTypes.MAGIC`.
 *
 * `message_id` is `thorns` on purpose — vanilla's "X was killed while trying to hurt Y" death message
 * is exactly right for reflected damage, and it needs no new translation keys.
 *
 * Verification: `DamageTypeTags.BYPASSES_ARMOR` = `field_42241`; `RegistryKeys.DAMAGE_TYPE` =
 * `field_42534`; `DamageSources.create(RegistryKey, Entity)` = `method_48796`.
 *
 * ⚠️ **Nothing emits `reflection` any more.** Since `build=cleanup21` Echo Shield mirrors the element
 * that hit the shield and falls back to [TRUE_DAMAGE] for a plain hit, so the bounce is either a real
 * `element_*` type or `true_damage` — see `EnchantEchoShieldHandler`. The key and its JSON are kept
 * because `isAuxiliaryDamage` / `isOwnElementalDamage` / `EnchantEchoShieldHandler.isReflection` still
 * name it: they are the "our own rider" filters, and deleting the type would silently widen them.
 * Either the type gets a producer again or those three checks go with it — not one at a time.
 */
object ModDamageTypes {

    val REFLECTION: RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "reflection"))

    /** 月 blessings: the flat damage a reaction is re-expressed as. Armour-bypassing, never an element. */
    val MOON: RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "moon"))

    /** Generic armour-bypassing hit for shockwaves and other non-elemental "true" damage. */
    val TRUE_DAMAGE: RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "true_damage"))

    /** 星 blessings: same shape as [MOON], separate type so the two never share a death message or tag. */
    val STAR: RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "star"))
}
