package dev.firefly.simpletweaks.util.damagesource

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * The mod's own damage type, used for Echo Shield's reflection.
 *
 * ## Why this exists
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
 */
object ModDamageTypes {

    val REFLECTION: RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "reflection"))
}

/**
 * Deals [rawDamage] to this entity, attributed to [attacker], with armour / resistance / protection
 * skipping — the 1.21 equivalent of 1.12.2's `ignoreArmorAndPotion = true`.
 *
 * Mirrors the pre-checks the 1.12.2 `runPlayerAttack` performed for the reflection call sites
 * (`forceHit = true`, so the i-frame counter is cleared, plus the alive and creative checks).
 * Deliberately separate from `util/PlayerUtils.runPlayerAttack` so that no other caller's behaviour
 * changes — that helper's `ignoreArmorAndPotion` flag stays documented as inert.
 */
fun LivingEntity.damageBypassingArmor(attacker: PlayerEntity, rawDamage: Float): Float {
    if (!this.isAlive) return 0f
    // 1.12.2 passed forceHit = true -> hurtResistantTime = 0 (1.21: timeUntilRegen)
    this.timeUntilRegen = 0
    if (this is PlayerEntity && this.abilities.creativeMode) return -1f

    val source: DamageSource = attacker.damageSources.create(ModDamageTypes.REFLECTION, attacker)
    this.damage(source, rawDamage)
    return rawDamage
}
