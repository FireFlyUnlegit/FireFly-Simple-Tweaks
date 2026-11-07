package dev.firefly.simpletweaks.util.damagesource

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.mixin.accessors.LivingEntityAccessor
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageType
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
 * Damage this mod dealt **on its own behalf**, as a follow-up to a real hit.
 *
 * Today: the poison tick, one chain arc, one holy-light hit, one shield reflection. All four are
 * credited to the player, so to every `LivingHurtEvent` listener they look exactly like a fresh sword
 * swing — which is why they must be filtered out of the **on-hit** stack (`build=cleanup22`).
 * Without this, one poisoned target re-fired crit / combo / double strike / aoe / charged strike once
 * per second and the damage exploded (the reported "然后就会导致伤害爆高").
 *
 * Deliberately **not** the eight `element_*` damage types: a mob's elemental attack uses those
 * directly, so the defensive half of the enchantment list (echo shield, immortal, damage reduction,
 * adopted armour) has to keep seeing them.
 */
fun DamageSource.isAuxiliaryDamage(): Boolean =
    auxDamageInFlight ||
        this.isElementDamage() ||
        this.typeRegistryEntry.matchesKey(CelestialDamageSources.CELESTIAL_PARTICLE) ||
        this.typeRegistryEntry.matchesKey(ModDamageTypes.REFLECTION)

/**
 * True while [damageElemental] is inside `LivingEntity#damage`, i.e. while a rider this mod queued is
 * landing.
 *
 * Needed *in addition* to [isAuxiliaryDamage]'s per-type checks: the eight `element_*` types are also
 * the type a **mob's own** elemental attack uses, so they cannot be classified as auxiliary by type.
 * The flag disambiguates by call path instead — a converted / reaction / shield-reflection rider is ours,
 * a mob swinging an elemental weapon is not.
 */
val auxDamageInFlight: Boolean get() = AUX_DEPTH.get() > 0

private val AUX_DEPTH: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }



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
/** Runs [block] with [auxDamageInFlight] set, restoring the previous value afterwards. */
private inline fun <T> withAuxFlag(block: () -> T): T {
    AUX_DEPTH.set(AUX_DEPTH.get() + 1)
    try {
        return block()
    } finally {
        AUX_DEPTH.set(AUX_DEPTH.get() - 1)
    }
}

fun LivingEntity.damageElemental(
    attacker: LivingEntity?,
    key: RegistryKey<DamageType>,
    amount: Float,
): Boolean {
    if (!this.isAlive) return false
    if (amount <= 0f || amount.isNaN() || amount.isInfinite()) return false

    val source: DamageSource =
        if (attacker != null) attacker.damageSources.create(key, attacker)
        else this.damageSources.create(key)

    if (withAuxFlag { this.damage(source, amount) }) return true

    if (!this.isAlive) return false

    // ⚠️ Clearing `timeUntilRegen` is **not** enough, and the first revision of this file was wrong
    // about that. `LivingEntity#damage`'s i-frame gate has two halves (bytecode offsets 228..260):
    //
    //   timeUntilRegen > 10 && !source.isIn(#minecraft:bypasses_cooldown)
    //       && amount <= lastDamageTaken   ->   return false      (nothing applied)
    //
    // `timeUntilRegen` is the only half a caller can observe; `lastDamageTaken` is `protected`. So a
    // small elemental tick (venom: 0.2/stack) arriving after a normal sword hit (say 8.0) was
    // *swallowed every single time* — the player swung, the arrow/crit/poison registered on the client
    // and in every event, and the number never moved. `timeUntilRegen = 0` alone made the first half
    // pass and the second half still refuse it.
    //
    // The fix is the second half: a damage type in `#minecraft:bypasses_cooldown` skips **both** tests.
    // Every `simple_tweaks:element_*` and `simple_tweaks:venom` / `chain_lightning` type is in that
    // tag (see `data/minecraft/tags/damage_type/`), which is why the retry below now lands. The
    // `timeUntilRegen` clear is kept as a second line of defence for damage types a datapack moved out
    // of the tag.
    // ⚠️ A rider must not disturb the i-frame accounting of the swing that produced it.
    //
    // `LivingEntity#damage`'s post-gate path (bytecode 250..274) does:
    //
    //     if (amount > lastDamageTaken) applyDamage(source, amount - lastDamageTaken)
    //     else                          return false
    //     lastDamageTaken = amount
    //     timeUntilRegen  = 20
    //
    // and it runs this for **every** landed hit, including the ones that skip the gate because their type
    // is in `#minecraft:bypasses_cooldown`. So after an elemental rider of 4.93 the target is left with
    // `lastDamageTaken = 4.93` and a fresh 20-tick i-frame — and the **player's next real swing** then
    // deals only `its amount - 4.93`, or nothing at all.
    //
    // Measured in a real session: `kept=5.84` + `planned_elemental=4.93` should total `10.77`, but the log
    // showed `total_this_hit=5.23` and `actual_elemental=4.32` — both reduced by the same **0.61** left
    // over from an earlier small hit — plus `prev_total=0.00` on some hits (fully swallowed). That is the
    // reported "切武器连打，物理部分丢伤害".
    //
    // The fix is to save and restore both fields around the rider: the swing that will come next must be
    // judged against the *swing's* numbers, not against this rider's.
    val accessor = this as? LivingEntityAccessor
    val savedRegen = this.timeUntilRegen
    val savedLast = accessor?.modLastDamageTaken ?: 0f
    // The hurt **animation** is separate state from the i-frame arithmetic. `applyDamage` sets
    // `hurtTime = maxHurtTime = 10`, which is what makes the entity flash red and restart its hurt
    // wobble. Restoring only `lastDamageTaken` left the target re-flashing once per rider — visually
    // indistinguishable from being hit repeatedly, i.e. the "combo" look survived the event fix. Both
    // fields are public, so no accessor is needed.
    val savedHurtTime = this.hurtTime
    val savedMaxHurtTime = this.maxHurtTime

    this.timeUntilRegen = 0
    // Lift **both** halves of the gate for this one instance. The element types are no longer in
    // `#minecraft:bypasses_cooldown` (author, `build=cleanup14`: "元素不应该无视无敌帧"), so the rider
    // would otherwise be compared against the swing that produced it (`1.03 <= 8.44` -> discarded) and
    // every conversion would silently do nothing.
    //
    // Zeroing `lastDamageTaken` here is what makes the rider "part of the swing" rather than an extra
    // hit: it can only exist when the swing itself landed (the conversion path skips gated swings), and
    // both fields are restored below, so the **next** swing is judged exactly as vanilla would.
    if (accessor != null) accessor.modLastDamageTaken = 0f
    var landed = withAuxFlag { this.damage(source, amount) }

    if (!landed && this.isAlive) {
        // Second attempt for a type a datapack moved out of `#minecraft:bypasses_cooldown`; see above.
        this.timeUntilRegen = 0
        if (accessor != null) accessor.modLastDamageTaken = 0f
        landed = withAuxFlag { this.damage(source, amount) }
    }

    // ⚠️ **Unconditionally**, while the target is alive — not only on the retry path.
    //
    // This block used to sit *after* an `if (this.damage(source, amount)) return true`, which made it
    // **unreachable in practice**: every `simple_tweaks:element_*` type is in
    // `#minecraft:bypasses_cooldown`, so the first attempt always lands and the function returned
    // before restoring anything. Consequence (the reported "怎么又无视无敌帧了"): after each rider the
    // target was left with `lastDamageTaken = <rider amount, e.g. 1.03>` and `timeUntilRegen = 20`,
    // and vanilla's gate is
    //
    //   timeUntilRegen > 10 && !bypasses_cooldown && amount <= lastDamageTaken  ->  discard
    //
    // so the **next real swing** compared `8.44 <= 1.03`, failed, and always landed: i-frames were
    // effectively switched off for every target that had been hit by an elemental rider. The log
    // proved it — `rider-iframe-restored` appeared **0 times** in a 900 KB session that dealt 159
    // elemental hits.
    if (this.isAlive) {
        this.timeUntilRegen = savedRegen
        if (accessor != null) accessor.modLastDamageTaken = savedLast
        this.hurtTime = savedHurtTime
        this.maxHurtTime = savedMaxHurtTime
        dev.firefly.simpletweaks.compat.FSTLog.log("Element") {
            "outcome=rider-iframe-restored, type=${key.value}, amount=${"%.2f".format(amount)}, " +
                "savedRegen=$savedRegen, savedLastDamageTaken=${"%.2f".format(savedLast)}, " +
                "savedHurtTime=$savedHurtTime, landed=$landed, accessor=${accessor != null}"
        }
    }

    // Debug-gated like every other `[FST-*]` probe. It was unconditional for one build while the
    // "elemental damage does nothing" report was being chased, but `damageElemental` is also the DoT
    // path for `venom` / `chain_lightning`, so an unconditional line here can repeat every tick per
    // affected entity — exactly the flood `AGENTS.md` §4.6 warns about.
    if (!landed) {
        dev.firefly.simpletweaks.compat.FSTLog.log("Element") {
            "outcome=element-damage-refused, type=${key.value}, target=${this.type.name.string}, " +
                "amount=${"%.2f".format(amount)} " +
                "(both attempts returned false: i-frame gate, invulnerability or fire resistance). " +
                "Check that the type is in #minecraft:bypasses_cooldown."
        }
    }
    return landed
}
