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
 * The two damage types the elemental enchantment trio (`frostbite` / `venom` / `chain_lightning`)
 * deals its damage with, plus the helpers that hand that damage out.
 *
 * ## Why the trio needs its own types rather than `playerAttack`
 * Three reasons, all of them structural rather than cosmetic:
 *
 * 1. **Identity.** A venom tick and a chain arc both arrive as `LivingHurtEvent`s with the wearer as
 *    the attacker, which is exactly the shape every other combat enchantment in this mod keys on
 *    (`hunters_mark` consumes its mark on any non-arrow hit, `charged_strike` pools a fraction of
 *    "your next hit", `combat_master` extends its combo, `execute` / `assassin` test a health
 *    threshold). Without a way to recognise *our own* damage, a once-per-second poison tick would
 *    feed all of them and — worse — re-enter this mod's own trio handlers, which stack on every
 *    player-attributed hit. [isElementDamage] is that recognition, and every handler of the trio
 *    returns early on it.
 * 2. **The armour / resistance bypass** the author asked for on venom is expressed in 1.21 as
 *    **damage-type tags**, not as a flag on a `DamageSource`. Tag membership is what
 *    `LivingEntity#applyArmorToDamage` / `#modifyAppliedDamage` consult, so a custom type is the
 *    only way to get "ignores armour, Resistance and Protection" without a mixin. Same mechanism,
 *    and the same three tags, as `simple_tweaks:reflection` (see [ModDamageTypes]).
 * 3. **Attribution.** The damage is created with the wearer as both source and attacker
 *    (`DamageSources#create(key, entity, entity)` semantics, exactly like [ModDamageTypes] and the
 *    celestial type), so a kill by poison is credited to the player: it refreshes
 *    `LivingEntity.playerHitTimer` (which is what lets a mob drop XP at all) and produces a sane
 *    death message instead of "X died".
 *
 * ## The i-frame gate: why [damageElemental] exists at all
 * `LivingEntity#damage` (bytecode-verified) refuses damage that arrives while the entity is in its
 * invulnerability window:
 *
 * ```text
 *   getfield timeUntilRegen:I ; i2f ; ldc 10.0f ; fcmpl ; ifle <apply>
 *   <amount> <= <lastDamageTaken>  ->  return false      // nothing applied, no feedback
 * ```
 *
 * A 1-point-per-second poison tick therefore vanishes silently whenever the target is also being
 * hit by something bigger — i.e. precisely while the player is attacking. That is the exact
 * "没生效 vs 没触发" ambiguity this project keeps getting bitten by, so [damageElemental] re-opens
 * the gate (`timeUntilRegen` is a public field on `Entity`) and retries **once**, and reports which
 * of the two paths was taken.
 *
 * ### The declarative alternative, and why it was not taken
 * Vanilla has a tag for exactly this: the gate's first test is
 * `timeUntilRegen > 10 && !source.isIn(DamageTypeTags.BYPASSES_COOLDOWN)`, so putting a damage type in
 * `#minecraft:bypasses_cooldown` skips the gate with no code at all. It was rejected here because it
 * is **unobservable**: every tick would land, so "landed first try" and "was about to be swallowed"
 * become indistinguishable in the log, and the tag would silently change the behaviour of any future
 * caller of the same damage type. The retry costs one extra `damage()` call in the (common)
 * swallowed case and keeps both outcomes reportable. Reverting to the tag is a one-line data change
 * if the author prefers it.
 *
 * One consequence either way, worth knowing before reporting it as a bug: a landed tick sets
 * `timeUntilRegen = 20`, so for the following 10 ticks the *victim* takes only the excess of any
 * smaller subsequent hit. That is vanilla's i-frame rule, not something this file introduces — the
 * same applies to `starfall`'s explosions and `echo_shot`'s delayed hits.
 */
object ElementDamageTypes {

    /**
     * Venom's damage-over-time tick.
     *
     * Tagged `#minecraft:bypasses_armor` + `#minecraft:bypasses_resistance` +
     * `#minecraft:bypasses_enchantments` — the author's "无视护甲（真实伤害）无视抗性", with the
     * protection-enchantment layer included because that layer *is* part of what a player means by
     * "ignore armour". Deliberately **not** added to `#minecraft:witch_resistant_to`: that tag is
     * what makes a witch take 15% of magic damage, and taking a fifth of the damage would contradict
     * "ignores resistance".
     */
    val VENOM: RegistryKey<DamageType> = of("venom")

    /**
     * One arc of `chain_lightning`.
     *
     * **Not** in any bypass tag on purpose: the arc is a real hit, so armour, Resistance and
     * Protection all apply, exactly as the author specified (`连锁伤害 = 原伤害 × (0.3 + 0.1 × lvl)`
     * with no mention of ignoring anything).
     */
    val CHAIN_LIGHTNING: RegistryKey<DamageType> = of("chain_lightning")

    private fun of(path: String): RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, path))
}

/**
 * True when [this] is damage this mod's elemental trio created.
 *
 * Never infer this from `source.getAttacker()`: the wearer *is* the attacker on purpose (see
 * [ElementDamageTypes]), so the attacker cannot distinguish a real hit from a poison tick.
 */
fun DamageSource.isElementDamage(): Boolean =
    this.typeRegistryEntry.matchesKey(ElementDamageTypes.VENOM) ||
        this.typeRegistryEntry.matchesKey(ElementDamageTypes.CHAIN_LIGHTNING)

/**
 * Deals [amount] as elemental damage of type [key], credited to [attacker] when one is given.
 *
 * Behaviour:
 *  - the entity is re-checked for aliveness before *and* after the first attempt (a dead entity must
 *    not be damaged, and must not have its i-frames touched);
 *  - if the first attempt is swallowed by the invulnerability gate, `timeUntilRegen` is cleared and
 *    the damage is retried once. The gate is *observed* here rather than predicted, because
 *    `lastDamageTaken` — the other half of the comparison — is `protected`, so a handler cannot
 *    evaluate it without an accessor mixin;
 *  - the return value is `LivingEntity#damage`'s own: `false` means it took one of its early exits
 *    (the i-frame gate, `isInvulnerableTo`, fire resistance, creative mode, …) and applied nothing,
 *    `true` means it reached `applyDamage`. Callers log the two outcomes differently rather than
 *    asking "did it work?".
 *
 * Note for callers: `LivingEntity#damage` applies the entity's own damage logic, so this can
 * re-enter the event bus with [isElementDamage] true. Every elemental handler in this mod returns
 * early on that, which bounds the recursion to one level.
 */
fun LivingEntity.damageElemental(
    attacker: PlayerEntity?,
    key: RegistryKey<DamageType>,
    amount: Float,
): Boolean {
    if (!this.isAlive) return false
    if (amount <= 0f || amount.isNaN() || amount.isInfinite()) return false

    val source: DamageSource =
        if (attacker != null) attacker.damageSources.create(key, attacker)
        else this.damageSources.create(key)

    if (this.damage(source, amount)) return true

    if (!this.isAlive) return false
    this.timeUntilRegen = 0
    return this.damage(source, amount)
}
