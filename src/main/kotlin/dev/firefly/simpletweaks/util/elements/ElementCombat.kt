package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.mixin.accessors.LivingEntityAccessor
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.logic.ElementMath
import dev.firefly.simpletweaks.util.ArcaneEcho
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.CelestialDamageSources
import dev.firefly.simpletweaks.util.damagesource.ElementDamageTypes
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.EnchantPresence
import dev.firefly.simpletweaks.util.getElementSpecificEnchantLevel
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.mobs.MobElements
import dev.firefly.simpletweaks.util.mobs.MobLevelSync
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.item.ItemStack
import java.util.UUID

/**
 * The shared engine behind every `xx_conversion` and every `xx_protection` enchantment.
 *
 * The curves, caps and their combination rules live in [ElementMath] (pure Kotlin, unit tested in
 * `logic/src/test`) so they cannot drift from their own documentation; this object only turns them into
 * events. What is recorded here is the part that is *not* arithmetic:
 *
 * - Conversion runs on `LivingHurtEvent` (head of `Entity#damage`, before armour). Cancelling part of a
 *   hit after armour has priced it reads as "armour suddenly stopped mattering".
 * - The re-dealt part is a separate `damageElemental` call: 1.21 damage sources are immutable, and the
 *   per-element resistances have to see it as its own element.
 * - `priority = 20` puts it after the arrow seam and the flat scalers (it converts an already boosted
 *   number) and before the `LOW` handlers such as `hunters_mark` (they see the converted amount).
 * - Protection runs on `LivingDamageEvent` (post-armour), matching `magic_protection`.
 */
object ElementCombat : dev.firefly.simpletweaks.core.Listenable {

    // ---------------------------------------------------------------- conversion tuning

    /** Converted share at level 1 and the growth per level (see [ElementMath.conversionFraction]). */
    const val BASE_FRACTION = ElementMath.BASE_FRACTION

    const val PER_LEVEL_FRACTION = ElementMath.PER_LEVEL_FRACTION

    const val MAX_FRACTION = ElementMath.MAX_FRACTION
    const val ELEMENT_SCALE = ElementMath.ELEMENT_SCALE
    const val BONUS_SCALE = ElementMath.BONUS_SCALE

    // ---------------------------------------------------------------- protection tuning

    const val PROTECTION_PER_LEVEL = ElementMath.PROTECTION_PER_LEVEL
    const val PROTECTION_CAP = ElementMath.PROTECTION_CAP
    /** Per total level of `elemental_supremacy`: its own mitigation share (see [supremacyReduction]). */
    const val SUPREMACY_PER_LEVEL = ElementMath.SUPREMACY_PER_LEVEL
    const val SUPREMACY_CAP = ElementMath.SUPREMACY_CAP

    // ---------------------------------------------------------------- mastery tuning

    /**
     * Extra elemental damage per mastery level, applied to **the elemental part only**.
     *
     * `elemental_mastery` is the one element enchantment that is *not* tied to an element: it is a
     * global multiplier on every element's output, so it is in neither the conversion exclusive set nor
     * either protection.
     */
    const val MASTERY_PER_LEVEL = ElementMath.MASTERY_PER_LEVEL

    /** Hard cap on the mastery bonus (`2.0` = +200%, i.e. 鑴?). */
    const val MASTERY_CAP = ElementMath.MASTERY_CAP

    // ---------------------------------------------------------------- cross-element tuning

    /** `elemental_ward`: all-element damage reduction per level, and its cap (3 levels = 9 %). */
    const val WARD_PER_LEVEL = ElementMath.WARD_PER_LEVEL
    const val WARD_CAP = ElementMath.WARD_CAP

    /** `elemental_resonance`: reaction damage bonus per level, and its cap (3 levels = +30 %). */
    const val RESONANCE_PER_LEVEL = ElementMath.RESONANCE_PER_LEVEL
    const val RESONANCE_CAP = ElementMath.RESONANCE_CAP

    /** `elemental_penetration`: share of the target's elemental mitigation ignored, per level. */
    const val PENETRATION_PER_LEVEL = ElementMath.PENETRATION_PER_LEVEL
    const val PENETRATION_CAP = ElementMath.PENETRATION_CAP

    /**
     * The converted fraction of `xx_conversion` at [level]: `BASE + PER_LEVEL 鑴?(level - 1)`, capped.
     *
     * ## Current curve (`build=cleanup12`, author's tuning)
     * `1.5 % 鑴?level`, capped at `7.5 %` 閳?level 5 is the ceiling and the five conversion levels are
     * `1.5 / 3.0 / 4.5 / 6.0 / 7.5 %`. The author cut it from `12 % + 6 %/level, cap 50 %` in the same
     * breath as restoring the armour bypass: a *small* slice that ignores armour is a flavour/gimmick,
     * a 36 % slice that ignores armour was a second attack.
     *
     * ## The net-damage question, and why it is no longer a problem
     * The re-dealt share is `ELEMENT_SCALE + BONUS_SCALE = 1.5 鑴砢 the cancelled share, so the target
     * takes `(1 閳?f) + 1.5f = 1 + 0.5f` times the original hit (before mastery and resistances) 閳?with
     * `f = 0.075` that is a `+3.75 %` net, i.e. conversion is now a *sidegrade with a purpose* rather
     * than a damage amplifier. Two historical traps that made earlier, larger curves read as a
     * downgrade are still worth knowing, because both are silent:
     *
     * 1. **The elemental half can be discarded by the i-frame gate** unless the element type is in
     *    `#minecraft:bypasses_cooldown` 閳?since `build=cleanup14` the element types are **not** in that tag; `damageElemental` lifts the gate for its own instance and restores it (see there).
     * 2. **A curve whose level-1 value is inside vanilla's 鍗?.5 damage roll reads as "no change"** 閳?     *    at `f = 0.015` the elemental share is deliberately below that noise on a 10-damage hit, which
     *    is fine now that the point of the enchantment is *element identity* (marks, reactions,
     *    protections), not raw damage.
     */
    fun conversionFraction(level: Int): Float = ElementMath.conversionFraction(level)

    /**
     * Net damage multiplier of a conversion at [level], relative to the unconverted hit.
     *
     * `(1 閳?f) + (ELEMENT_SCALE + BONUS_SCALE) 鑴?f 鑴?mastery` 閳?exposed so the startup self-check and the
     * acceptance checklist can talk about the same number instead of each re-deriving it.
     *
     * With the shipped constants (`ELEMENT_SCALE = 1.0`, `BONUS_SCALE = 0.5`) this is
     * `1 + 0.5 鑴?f 鑴?mastery`, which is `閳?1` for every `f 閳?0` 閳?i.e. **a conversion can never make
     * the hit smaller**. That is an invariant, not a coincidence: it is what the first field report
     * ("鐎垫帒鍟洪梽鍕摕娴笺倕顔婃潻妯哄綁娴ｅ簼绨?) violated, and the self-check below fails the mod loudly if a future
     * rebalance breaks it.
     */
    fun netMultiplier(level: Int, mastery: Float = 1.0f): Float =
        ElementMath.netMultiplier(level, mastery)

    /**
     * The mastery multiplier for [level]: `1 + MASTERY_PER_LEVEL 鑴?level`, capped.
     *
     * `1.0` (no bonus) when absent, so every call site can multiply unconditionally.
     */
    fun masteryMultiplier(level: Int): Float = ElementMath.masteryMultiplier(level)

    /** Mastery level on the attacker's main hand (`elemental_mastery`). */
    fun masteryOf(attacker: PlayerEntity): Int = masteryLevel(attacker.mainHandStack)

    /**
     * Mastery level on one stack.
     *
     * 閳跨媴绗?**This used to return 0 for every item in the game**, and the bug is the reason
     * `elemental_mastery` appeared to do nothing at all. It called
     * `keyOfElement("elemental_mastery", protective = false)`, and that helper *builds* a name:
     *
     * ```
     * id = if (protective) "<elementId>_protection" else "<elementId>_conversion"
     * ```
     *
     * so it looked for `simple_tweaks:elemental_mastery_conversion`, which does not exist 閳?`null` 閳?     * `return 0`. Every conversion line in a real session logged `mastery=1.00`, i.e. the enchantment was
     * never read at all. `keyOfElement` is only correct for the 14 enchantments whose ids are literally
     * `<element>_conversion` / `<element>_protection`; mastery is not keyed to an `ElementType`, so it
     * must use its own generated key.
     */
    fun masteryLevel(stack: ItemStack): Int =
        getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ELEMENTAL_MASTERY)

    /** Mastery level from a set of armour slots' stacks (used for armour-borne mastery, if any). */
    fun armorMasteryLevel(entity: LivingEntity): Int =
        EnchantPresence.armorLevelOf(entity, GeneratedEnchantments.ELEMENTAL_MASTERY)

    /**
     * Startup self-check: **a conversion must never reduce the hit.**
     *
     * Runs once from `init()`. It exists because the "conversion made my damage smaller" report was
     * *arithmetically impossible* with the shipped constants, so the bug had to be a discarded damage
     * call 閳?and a future rebalance of `ELEMENT_SCALE` / `BONUS_SCALE` / the fraction curve could make
     * it arithmetically possible for real, silently. A `warn` here is a red flag in any build log, and
     * 鎼侫K of the checklist tells the author to report it.
     */
    private fun selfCheck() {
        val worst = (1..9).minOf { netMultiplier(it) }
        if (worst < 1.0f) {
            SimpleTweaks.LOGGER.warn(
                "Elemental conversion self-check FAILED: net multiplier can drop to {} at some level; " +
                    "a conversion must never reduce damage. Check ELEMENT_SCALE / BONUS_SCALE / the " +
                    "fraction curve in ElementCombat.",
                "%.3f".format(worst),
            )
        } else {
            FSTLog.log("Element") {
                "outcome=self-check-ok, level1=${"%.3f".format(netMultiplier(1))}x, " +
                    "level5=${"%.3f".format(netMultiplier(5))}x, worst=${"%.3f".format(worst)}x"
            }
        }
    }

    /** [Listenable.init] hook 閳?registers nothing, only runs [selfCheck]. */
    override fun init() {
        selfCheck()
        selfCheckProtectionKeys()
    }

    /** The conversion the attacker's main hand performs, or `null` when there is none.
     *
     * At most one can be present because the seven conversion enchantments share one `exclusive_set`
     * (`#simple_tweaks:exclusive_set/elements`). This does **not** trust that: exclusivity is a datapack
     * property a pack can edit away, so the first match in [ElementType] order wins and a second one is
     * reported as `outcome=multiple-conversions` instead of silently changing which element the player
     * gets.
     */
    fun conversionOf(
        attacker: PlayerEntity,
        stack: ItemStack = attacker.mainHandStack,
        target: LivingEntity? = null,
    ): Conversion? {
        var found: Conversion? = null
        for (element in ElementType.CONVERTIBLE) {
            val level = getElementSpecificEnchantLevel(stack, element, protective = false)
            if (level <= 0) continue
            if (found != null) {
                FSTLog.log("Element") {
                    "outcome=multiple-conversions, kept=${found.element.id}, ignored=${element.id}, " +
                        "note=exclusive_set is missing or was edited by a pack"
                }
                break
            }
            found = Conversion(element, level)
        }
        if (found == null || target == null) return found

        // `elemental_sovereign`: pick the element that would actually react with the target's aura.
        val sovereign = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.ELEMENTAL_SOVEREIGN)
        if (sovereign <= 0) return found

        val aura = ElementAuras.peek(target)
        if (aura == null) {
            // Nothing to react with: keep the element the weapon declares. A random pick would look like a
            // bug to the player, and a "weakest resistance" pick needs resistance data the target may not
            // have 閳?both were rejected on purpose.
            return found
        }
        val reacting = ElementType.CONVERTIBLE.firstOrNull { candidate ->
            candidate != aura.first && ElementReactions.of(aura.first, candidate) != null
        }
        if (reacting == null) return found
        if (reacting != found.element) {
            FSTLog.log("Element") {
                "outcome=sovereign-element, declared=${found.element.id}, chosen=${reacting.id}, " +
                    "aura=${aura.first.id}"
            }
        }
        return Conversion(reacting, found.level)
    }

    /** One conversion: which element, and at what level. */
    data class Conversion(val element: ElementType, val level: Int)

    /**
     * Per-target: how much health the **previous** converted hit actually removed, end to end
     * (armour on the kept share + armour on the elemental share 閳?elements respect armour since
 * `build=cleanup10`). Diagnostic only 閳?it exists so the log can show
     * the number the player sees instead of the pre-armour figure, which is what "sword says 9.13,
     * monster loses 5.84" was about.
     *
     * Keyed by UUID in a `WeakHashMap`-free plain map with no lifetime guarantee: entries are
     * overwritten on every hit and never read except for logging. Bounded by the number of entities the
     * player has hit since login 閳?deliberately **not** cleaned up, because doing so would need a tick
     * handler for a log line.
     */
    private val LAST_HIT = java.util.concurrent.ConcurrentHashMap<UUID, Float>()

    // ---------------------------------------------------------------- seams

    /**
     * Conversion, marking and reactions 閳?on the pre-armour seam, **after every other damage handler**.
     *
     * ## Why `priority = 200` and not [EventPriority.LOWEST] (`100`)
     * Conversion must see the **final** damage number: the crit, the mastery, `charged_strike`'s release,
     * `combo`'s stacks, `hunters_mark` 閳?everything that adjusts a hit. If it runs earlier, each of
     * those multiplies the *pre-conversion* figure and then conversion carves its share out of a number
     * that is about to change again, so the elemental share no longer tracks the hit proportionally.
     *
     * But `100` does **not** mean "last": the bus breaks ties by **registration order**, and this object
     * is registered from `SimpleTweaks.onInitialize` **before** `EnchantmentManager.initHandlers()`, so
     * at `100` it would actually run *ahead* of `charged_strike` / `echo_shield` / `aoe_attack` /
     * `adopted_armor` 閳?i.e. "last" in name only. A value above `LOWEST` is the only way to express
     * "after all of them" with an int priority (which is exactly what ints were adopted for).
     *
     * ## What the later position changes, and why that is acceptable
     * The handlers at `100` now see the **converted** amount rather than the raw one:
     *
     * | handler | before | after |
     * |---|---|---|
     * | `charged_strike` | stores a share of the raw hit | stores a share of the kept (physical) share |
     * | `echo_shield` | buffer compared against the raw hit | compared against the kept share |
     * | `aoe_attack` | splits the raw hit | splits the kept share |
     * | `adopted_armor` | reduces the raw hit | reduces the kept share |
     *
     * That is the intended direction: conversion is a *distribution* change (physical 閳?elemental), and
     * "how much of this swing is physical" is what those four are about. The elemental remainder is
     * dealt separately and is not subject to a physical splitters' arithmetic.
     *
     * 閳跨媴绗?The elemental damage itself is **queued**, never dealt from inside this handler 閳?see
     * [onWorldTick] and the note in the conversion body. Calling `LivingEntity#damage` from an event
     * handler corrupts `EventSeams`' ThreadLocal cache and silently discards the caller's `e.amount`.
     */
    @SubscribeEvent(priority = 200)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.source.attacker as? LivingEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        val incoming = ElementType.of(e.source)

        // 閳跨媴绗?**A hit a raised shield is about to stop must not be converted.**
        //
        // `LivingHurtEvent` fires at the HEAD of `LivingEntity#damage`, which is **before** the shield
        // check further down the same method:
        //
        //   if (this.blockedByShield(source)) { this.damageShield(amount); 閳?return false; }
        //
        // So this handler used to cancel a share of the incoming damage and queue the elemental rider
        // *before* the shield was ever consulted. The shield then blocked the (now smaller) physical
        // hit 閳?and the rider, which `damageElemental` deals with the gate lifted for its own instance, and is applied on the
        // world tick, landed anyway. That is the reported "閸忓啰绀屾导銈咁唺閸欘垯浜掔粚璺ㄦ禈": a raised shield stopped
        // only the physical share, mob elemental attacks went straight through, and the barrier was
        // therefore "婵備粙姣﹂梼韫秶".
        //
        // Returning early leaves `e.amount` untouched, so vanilla blocks the whole hit and the shield
        // takes its normal damage. `blockedByShield` is a pure predicate here (bytecode: tag check +
        // `isBlocking` + facing dot-product, no writes), so calling it from the event is safe.
        if (target.blockedByShield(e.source)) {
            FSTLog.log("Element") {
                "attacker=${attacker.type.name.string}, target=${target.type.name.string}, " +
                    "incoming=${incoming}, amount=${"%.2f".format(e.amount)}, " +
                    "outcome=conversion-skipped-target-blocking"
            }
            return
        }

        // Remember what just hit this entity, for the floating damage number 閳?**before** the
        // own-damage early return below.
        //
        // The elemental share of a converted hit *is* one of our own damage types, so the earlier
        // ordering (remember after the return) meant a converted hit was never recorded: every
        // conversion weapon produced a **physical-red** number, on every client, which is the reported
        // "闂勫嫭婀侀崗鍐鏉烆剙瀵查惃鍕劅閸ｃ劋绗傞弨璇插毊娑旂喐妲哥痪銏ｅ鐎涙ぞ缍?. Recording first only affects the *colour*; the early
        // return below still stops the element from being converted a second time.
        //
        // A hit no element claims (a plain swing) is skipped so it keeps the indicator's original colour.
        if (incoming != null) rememberElement(target, incoming)

        // Our own re-dealt element damage must never be converted again 閳?that would chain forever.
        if (isOwnElementalDamage(e)) return

        // A mob with an element takes over from here: it has no enchantments to read, so none of the
        // player path below applies to it.
        if (attacker !is PlayerEntity) {
            handleMobAttack(attacker, target, incoming, e)
            return
        }

        // Mastery is read **before** the conversion branch, because it must also affect a hit that is
        // already elemental (a fire weapon on a fire hit, an arrow from a fire arrow, ...). Reading it
        // inside the conversion branch only would make the enchantment silently do nothing to any
        // player who is not carrying one of the seven conversions 閳?the "闁款喖鐡ㄩ崷?閳?閺堝姹夌拠璇茬暊" shape.
        // The weapon **that fired this shot**, not whatever is in hand now: swapping to a
        // strong melee weapon after releasing the bow must not upgrade the arrow in flight.
        val weapon = attacker.combatStack(e.source)
        // `arcane_echo` amplifies the elemental output of every hit, so it joins mastery here rather than
        // in a second handler reading the same `e.amount`.
        val mastery = masteryMultiplier(masteryLevel(weapon)) *
            (1.0 + ArcaneEcho.statsOf(attacker).element).toFloat()

        val conversion = conversionOf(attacker, weapon, e.entityLiving)

        if (conversion == null) {
            // No conversion enchantment. Two things can still happen here:
            //
            // 1. an **already-elemental** hit (a fire weapon, an arrow, a mob's own elemental source)
            //    gets the mastery multiplier and leaves a mark;
            // 2. a **non-elemental** hit leaves no mark (having no element is the base case and reacting on
            //    it would make every plain sword hit mark the target) but **still gets mastery**.
            //
            // 閳跨媴绗?Case 2 is the one the first revision got wrong: the early return wrapped the whole block,
            // so with a sword and no conversion enchantment `elemental_mastery` did *nothing at all* 閳?            // the reported "閸忓啰绀岀划楣冣偓姘弓鐟欎焦鍨氶弫?. Mastery is an elemental-output multiplier, and on a
            // non-elemental hit the elemental output is zero, so the correct behaviour for mastery alone
            // on a plain hit is to do nothing *visible* 閳?but it must still be reachable, otherwise it
            // silently never fires and cannot be told apart from a broken enchantment. It is logged
            // (see `outcome=mastery-only-non-elemental`), which is what makes the two distinguishable.
            if (incoming == null) {
                if (mastery != 1.0f) {
                    FSTLog.log("Element") {
                        "incoming=none, mastery=${"%.2f".format(mastery)}, " +
                            "note=no element to scale (equip a conversion for mastery to apply), " +
                            "outcome=mastery-only-non-elemental"
                    }
                }
                return
            }

            val before = e.amount
            e.amount *= mastery
            // Same gate as the conversion branch: a swing vanilla is about to discard must not leave a mark
            // or start a reaction, because the reaction's rider lifts the gate and would land alone.
            if (wouldBeDiscardedByIFrames(target, e.source, e.amount)) {
                FSTLog.log("Element") {
                    "amount=%.2f, outcome=skipped-swing-would-be-gated".format(e.amount)
                }
                return
            }
            // 婢х偛绠?reaction: `melt`-style multipliers are applied to the *hit*, which is why they
            // come back from `record` instead of being dealt as extra damage.
            val outcome = ElementMarks.record(attacker, target, incoming, e.amount, source = e.source)
            if (outcome.amplify > 1f) {
                e.amount *= outcome.amplify
                FSTLog.log("Element") {
                    "element=${incoming.id}, reaction=${outcome.key}, " +
                        "amplify=${outcome.amplify}, amount=${before}->${e.amount}, outcome=amplified"
                }
            }
            if (mastery != 1.0f) {
                FSTLog.log("Element") {
                    "element=${incoming.id}, mastery=${"%.2f".format(mastery)}, " +
                        "amount=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, " +
                        "outcome=mastered"
                }
            }
            return
        }

        val element = conversion.element

        if (incoming == element) {
            // Already the target element (an actual fire hit with a fire weapon): nothing to convert,
            // but it must still count as that element or the enchantment would disable its own reactions,
            // and mastery still applies to it.
            val before = e.amount
            e.amount *= mastery
            // Same gate as the conversion branch: a swing vanilla is about to discard must not leave a mark
            // or start a reaction, because the reaction's rider lifts the gate and would land alone.
            if (wouldBeDiscardedByIFrames(target, e.source, e.amount)) {
                FSTLog.log("Element") {
                    "amount=%.2f, outcome=skipped-swing-would-be-gated".format(e.amount)
                }
                return
            }
            // Every element has a reaction row now, so recording is unconditional here.
            val outcome = ElementMarks.record(attacker, target, element, e.amount, source = e.source)
            if (outcome.amplify > 1f) {
                e.amount *= outcome.amplify
                FSTLog.log("Element") {
                    "element=${element.id}, reaction=${outcome.key}, " +
                        "amplify=${outcome.amplify}, amount=${before}->${e.amount}, outcome=amplified"
                }
            }
            FSTLog.log("Element") {
                "element=${element.id}, mastery=${"%.2f".format(mastery)}, " +
                    "amount=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, " +
                    "outcome=mastered (already this element)"
            }
            return
        }

        val fraction = conversionFraction(conversion.level)
        if (fraction <= 0f) return

        val original = e.amount
        if (original <= 0f) return

        // Health of the target **before the whole hit**, so the line below can report what the target
        // actually loses in total rather than only the elemental slice.
        val healthAtStart = target.health

        // Mastery scales the **elemental part only**, never the cancelled physical part 閳?that is what
        // makes it "elemental" mastery and keeps it from becoming a plain damage multiplier. It was
        // already read above; this is the same value, applied to the re-dealt share.
        val cancelled = original * fraction
        val reDealt = (cancelled * ELEMENT_SCALE + cancelled * BONUS_SCALE) * mastery

        // 1) cancel the converted share from the original hit
        e.amount = (original - cancelled).coerceAtLeast(0f)

        // 閳跨媴绗?**A swing the i-frame gate is about to discard must not spawn a rider.**
        //
        // `LivingHurtEvent` fires at the HEAD of `LivingEntity#damage`, *before* the gate:
        //
        //   timeUntilRegen > 10 && !source.isIn(#minecraft:bypasses_cooldown)
        //       && amount <= lastDamageTaken   ->   return false   (nothing applied)
        //
        // Clicking faster than the target's i-frames therefore fires this handler for a hit the game will
        // throw away 閳?and the rider we queue for it lives in `#minecraft:bypasses_cooldown` (it must, or
        // it could never land), so **the elemental share went through while the swing did not**. Free
        // damage on every discarded click; that is the reported "閸忓啰绀屾导銈咁唺閺冪姾顫嬮弮鐘虫櫕鐢冨箵閺€璇插毊閺佸奔姹?.
        //
        // 閳跨媴绗?**The amount must be the one vanilla will see, i.e. `e.amount` AFTER the cancellation above.**
        // The first revision of this guard tested the *incoming* `original`, and that is why it never fired:
        // with `original=9.13`, `kept=5.84` and `lastDamageTaken=5.84`, the pre-conversion comparison
        // (`9.13 <= 5.84`) was false 閳?so the guard passed 閳?while vanilla then compared the rewritten
        // `5.84 <= 5.84` and discarded the whole swing. The log showed exactly that: `total_this_hit` equal
        // to `planned_elemental` and nothing else.
        if (wouldBeDiscardedByIFrames(target, e.source, e.amount)) {
            FSTLog.log("Element") {
                "target=${target.type.name.string}, original=${"%.2f".format(original)}, " +
                    "kept=${"%.2f".format(e.amount)}, timeUntilRegen=${target.timeUntilRegen}, " +
                    "outcome=conversion-skipped-swing-would-be-gated"
            }
            return
        }

        // 2) **Defer** the elemental damage instead of dealing it here.
        //
        // 閳跨媴绗?This is the fix for "20 HP takes 4 hits", and the mechanism is worth spelling out because
        // the first three revisions all got it wrong in different ways.
        //
        // `LivingHurtEvent` is fired from `EntityDamageMixin` at the HEAD of `LivingEntity#damage`, and
        // the event object lives in a **ThreadLocal cache** (`EventSeams.HURT`) that
        // `@ModifyVariable` reads again moments later to rewrite the argument. Calling
        // `target.damage(...)` *inside* that event re-enters `EventSeams.hurt`, which **overwrites the
        // cache with the inner event** and then `clearHurt()`s it on the way out. When the outer
        // `@ModifyVariable` then asks for "the event for (target, player_attack)" it finds nothing,
        // fires a **fresh** event with the original amount, and every mutation this handler made to
        // `e.amount` is lost.
        //
        // The measured symptom was exactly that: `kept=5.84` was computed and logged, and the target
        // lost only the elemental 4.93 per hit (`prev_armor_kept=4.93`) 閳?so a 20 HP mob needed
        // ceil(20 / 4.93) = 4 hits. The physical share never landed at all.
        //
        // Fixing it by clearing the cache around the nested call would be fragile (the outer event's
        // mutations are not re-applied automatically). Dealing the element **after** the outer damage
        // call has finished is the only version where both shares are counted exactly once, so the
        // pending elemental hit is queued here and flushed from the world tick.
        PENDING.computeIfAbsent(target.world) { java.util.ArrayDeque() }.add(
            PendingElement(attacker, target, element, reDealt, original * mastery, healthAtStart),
        )

        // 3) Record the mark / resolve the reaction now: the reaction's own damage is also queued, and
        //    the mark must be this hit's element regardless of what the reaction does.
        // A conversion rider is a **strong** application (2 units): the reference's "skill-grade"
        // application, which survives a weak trigger instead of being eaten by it.
        val reacted = ElementMarks.recordAndReact(
            attacker, target, element, original * mastery, units = ElementAuras.STRONG,
            source = e.source,
        )
        if (reacted.amplify > 1f) {
            // 婢х偛绠? the multiplier lands on the physical share of this very swing.
            e.amount *= reacted.amplify
        }

        // `FSTLog`, i.e. **off unless `-Dsimpletweaks.debug=true`** (or `SIMPLETWEAKS_DEBUG=1`).
        // This was unconditional for one build while the "damage is lower" report was being chased 閳?        // see `DEVLOG.md` 鎼?2.6 for what it was needed for (it is what produced the `prev=` number that
        // finally located the real bug). Once that was found it goes back behind the switch: one line
        // per converted hit is far too much for normal play.
        //
        // `prev_total` is still worth keeping: it is the end-to-end health the *previous* converted hit
        // removed (physical share after armour + elemental share), which is the only figure that can be
        // compared against what the player sees.
        FSTLog.log("Element") {
            "element=${element.id}, lvl=${conversion.level}, " +
                "fraction=${"%.2f".format(fraction)}, mastery=${"%.2f".format(mastery)}, " +
                "incoming=${incoming?.id ?: "none"}, " +
                "original=${"%.2f".format(original)}, kept=${"%.2f".format(e.amount)}, " +
                "planned_elemental=${"%.2f".format(reDealt)}, landed=pending, " +
                "reaction=${reacted.key ?: "none"}, " +
                "prev_total=${LAST_HIT[target.uuid]?.let { "%.2f".format(it) } ?: "n/a"}, " +
                "outcome=converted"
        }
        LAST_HIT[target.uuid] = healthAtStart - target.health
    }

    /** One elemental hit waiting to be dealt after the current damage call unwinds. */
    private class PendingElement(
        val attacker: LivingEntity,
        val target: LivingEntity,
        /** `null` for an unreduced rider that carries no element (`enqueueUnreduced`). */
        val element: ElementType?,
        val amount: Float,
        /** The triggering hit's damage after mastery 閳?the reaction table scales from this. */
        val baseDamage: Float,
        /** Health before the whole hit, for the end-to-end `total_this_hit` measurement. */
        val healthAtStart: Float,
    )

    /**
     * Queues [element] damage against [target] to be dealt at the end of this tick.
     *
     * **Never call `LivingEntity#damage` from inside a `LivingHurtEvent` handler.** See
     * [onWorldTick] and the note in `onLivingHurt` for the event-cache corruption that causes.
     */
    fun enqueueElemental(attacker: LivingEntity, target: LivingEntity, element: ElementType, amount: Float) {
        // `resonance_core` and any future "elemental damage amplifier" answer here instead of being
        // imported into the engine: `ElementDamageSeam` is a no-op when nothing is registered.
        val finalAmount = ElementDamageSeam.modify(attacker, target, element, amount)

        // 閳跨媴绗?A non-finite amount must never be queued. `NaN` health is not recoverable in-game: the
        // entity can never be killed (`health <= 0` is false forever) and every later `health + x`
        // stays `NaN`. `build=cleanup11` was that report ("閹垫挷绗夊缁樷偓顏嗗⒖ / lag on 0hp"): a handler
        // multiplied its own re-dealt damage until it overflowed to `Infinity` and then `NaN`.
        //
        // The message distinguishes "the caller asked for something invalid" from "a seam modifier
        // produced it", otherwise a bug in an amplifier would look like a bug in the caller.
        if (finalAmount <= 0f || !finalAmount.isFinite()) {
            FSTLog.log("Element") {
                "element=${element.id}, requested=$amount, after_seam=$finalAmount, " +
                    "target=${target.type.name.string}, outcome=element-amount-not-finite"
            }
            return
        }
        PENDING.computeIfAbsent(target.world) { java.util.ArrayDeque() }.add(
            PendingElement(attacker, target, element, finalAmount, finalAmount, target.health),
        )
    }

    /**
     * Queues [amount] of **unreduced, non-elemental** damage against [target], to be dealt at the end of
     * the tick like [enqueueElemental].
     *
     * This is the rider path for flat bonuses that used to borrow the physical element type (shield bash,
     * shockwave, skyfall): deferring is mandatory (see [enqueueElemental]), and `true_damage` is in
     * `#minecraft:bypasses_armor` with no protection row, which is the same "cannot be reduced, cannot
     * re-enter the on-hit stack" behaviour those riders need now that no physical element exists.
     */
    fun enqueueUnreduced(attacker: LivingEntity, target: LivingEntity, amount: Float) {
        if (amount <= 0f || !amount.isFinite()) {
            FSTLog.log("Element") {
                "requested=$amount, target=${target.type.name.string}, outcome=unreduced-amount-not-finite"
            }
            return
        }
        PENDING.computeIfAbsent(target.world) { java.util.ArrayDeque() }.add(
            PendingElement(attacker, target, null, amount, amount, target.health),
        )
    }

    /** Queued elemental damage, one deque per world, flushed on the server tick. */
    private val PENDING = java.util.concurrent.ConcurrentHashMap<net.minecraft.world.World, java.util.ArrayDeque<PendingElement>>()

    /** Upper bound on elemental hits dealt per world per tick; see [onWorldTick]. */
    private const val FLUSH_CAP_PER_TICK = 256

    /**
     * Deals every queued elemental hit and logs the end-to-end total.
     *
     * Runs on `WorldTickEvent` (END), i.e. after every `LivingEntity#damage` of the tick has fully
     * unwound 閳?which is what makes `healthAtStart - health` a trustworthy total for the whole hit.
     */
    @SubscribeEvent
    fun onWorldTick(e: dev.firefly.simpletweaks.compat.event.TickEvent.WorldTickEvent) {
        if (e.phase != dev.firefly.simpletweaks.compat.event.TickEvent.Phase.END) return
        val queue = PENDING[e.world] ?: return
        // A hard cap on hits flushed per world per tick. Element hits are normally one or two per
        // player swing; the only way to need dozens is a handler that re-queues its own output (which
        // `build=cleanup11` caught: the queue grew exponentially until the floats overflowed). The cap
        // is a backstop so a future feedback loop costs one logged warning instead of a dead server.
        var flushed = 0
        while (queue.isNotEmpty() && flushed < FLUSH_CAP_PER_TICK) {
            flushed++
            val pending = queue.poll() ?: break
            val target = pending.target
            // Re-check on flush: the target may have died to the physical share of the very hit that
            // queued this, in which case dealing more damage would be a phantom hit on a corpse.
            if (!target.isAlive) continue
            val before = target.health
            val landed = target.damageElemental(
                pending.attacker,
                pending.element?.damageType ?: ModDamageTypes.TRUE_DAMAGE,
                pending.amount,
            )
            val actual = (before - target.health).coerceAtLeast(0f)
            // Debug-gated like the conversion line above 閳?same `[FST-Element]` tag, debug switch.
            FSTLog.log("Element") {
                "element=${pending.element?.id ?: "true"}, " +
                    "flushed_elemental=${"%.2f".format(pending.amount)}, landed=$landed, " +
                    "actual_elemental=${"%.2f".format(actual)}, " +
                    "total_this_hit=${"%.2f".format(pending.healthAtStart - target.health)}, " +
                    "outcome=element-dealt"
            }
            LAST_HIT[target.uuid] = pending.healthAtStart - target.health
        }
    }

    /**
     * A mob's elemental melee attack: convert a fixed share of the hit into its element.
     *
     * Same shape as the player's `xx_conversion` (cancel a share, re-deal it as the element, add a
     * bonus), deliberately: the player's `xx_protection` is then the correct counter with no
     * special-casing, and the floating number colours the hit the same way regardless of who swung.
     *
     * The elemental part is **queued** like every other re-deal 閳?dealing damage from inside a
     * `LivingHurtEvent` corrupts `EventSeams`' cache (see `DEVLOG` 鎼?2.6).
     */
    private fun handleMobAttack(attacker: LivingEntity, target: LivingEntity, incoming: ElementType?, e: LivingHurtEvent) {
        val mob = attacker as? MobEntity ?: return
        val element = MobElements.elementOf(mob) ?: return
        if (incoming == element) return

        val original = e.amount
        if (original <= 0f) return

        // Conversion scales with the mob's level (1 % per 5 levels, cap 50 %); below level 5 the hit stays
        // physical, so a fresh world does not start with elemental zombies.
        val level = MobLevelSync.levelOf(mob)
        val fraction = MobElements.conversionFraction(level)
        if (fraction <= 0f) return

        val cancelled = original * fraction
        val reDealt = original * ElementMath.elementalShare(fraction, MobElements.BONUS_SCALE)
        val kept = (original - cancelled).coerceAtLeast(0f)

        // 閳跨媴绗?Same guard as the player path: a swing the i-frame gate is about to discard must not
        // queue a rider. Without it a mob attacking faster than the target's i-frames still landed the
        // elemental share 閳?"閹亞澧块幍鎾茬稑閺冪姾顫嬮弮鐘虫櫕鐢? 閳?because the rider is in `#minecraft:bypasses_cooldown`
        // and therefore immune to the very gate that stopped the swing.
        if (wouldBeDiscardedByIFrames(target, e.source, kept)) {
            FSTLog.log("Element") {
                "mob=${mob.type.name.string}, target=${target.type.name.string}, " +
                    "element=${element.id}, original=${"%.2f".format(original)}, kept=${"%.2f".format(kept)}, " +
                    "timeUntilRegen=${target.timeUntilRegen}, " +
                    "outcome=mob-conversion-skipped-swing-would-be-gated"
            }
            return
        }
        e.amount = kept

        rememberElement(target, element)
        // The victim also gets the aura: without it a mob's element could only ever hit, never set up a
        // reaction on the player 閳?i.e. the whole element game was one-directional.
        ElementAuras.attach(target, element, MobElements.AURA_UNITS)
        enqueueElemental(attacker, target, element, reDealt)

        FSTLog.log("Element") {
            "mob=${mob.type.name.string}, level=$level, element=${element.id}, " +
                "amount=${"%.2f".format(original)}->${"%.2f".format(e.amount)}, " +
                "fraction=$fraction, elemental=${"%.2f".format(reDealt)}, " +
                "aura=${ElementAuras.describe(target)}, outcome=mob-elemental-attack"
        }
    }

    /** Protection, on the post-armour seam. */
    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val element = ElementType.of(e.source)
        // Read before the element check: `elemental_supremacy` is the one source that also answers a hit
        // no element claims (`build=cleanup99`).
        val supremacyReduction = supremacyReduction(e.entityLiving)

        if (element == null) {
            reduceNonElemental(e, supremacyReduction)
            return
        }

        // `build=cleanup21`: venom and reflection sit in `#minecraft:bypasses_enchantments` on purpose
        // (the author's "閺冪姾顫嬮幎妤佲偓?). Now that venom counts as `nature` and reflection mirrors an element,
        // they would otherwise start being reduced here 閳?the very enchantments they are documented to
        // ignore. Honouring the tag keeps that promise.
        if (e.source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            FSTLog.log("Element") {
                "protected=${element.id}, amount=${"%.2f".format(e.amount)}, " +
                    "outcome=reduction-skipped-bypasses-enchantments"
            }
            return
        }

        // A player's own `xx_protection`, `elemental_ward`, a mob's own element resistance and the
        // attacker's `elemental_penetration` are independent sources that happen to act on the same
        // number. Mitigations are combined **multiplicatively** (neither can drive the total past
        // 100 % by itself); penetration is then applied as a multiplier on the combined mitigation, so
        // it can never turn an 80 % protection into a negative one.
        val level = protectionLevel(e.entityLiving, element)
        val enchantReduction = ElementMath.protectionReduction(level)
        val wardReduction = wardReduction(e.entityLiving)
        val mob = e.entityLiving as? MobEntity
        val mobReduction = if (mob != null) MobElements.resistanceFor(mob, element) else 0f
        val penetration = penetrationOf(e.source.attacker as? LivingEntity, e.source)

        // `elemental_supremacy` is its **own** mitigation source, not a change to the affinity cap: the chain is
        // multiplicative, so an extra factor can never drive the total to 100 %. This is what gives the
        // enchantment "height" as well as "breadth" 閳?four maxed fire pieces already cap the affinity at 80 %,
        // and supremacy then removes a further slice of what is left.
        // The combination rules live in `ElementMath` so they are unit tested; these comments only
        // record why the shape is what it is. Mitigations multiply, penetration multiplies the product.
        val effective = ElementMath.mitigatedShare(
            enchantReduction, mobReduction, wardReduction, supremacyReduction, penetration
        )

        if (effective <= 0f) return

        val before = e.amount
        e.amount = (e.amount * (1.0f - effective)).coerceAtLeast(0f)

        FSTLog.log("Element") {
            "protected=${element.id}, lvl=$level, " +
                "enchantReduction=${"%.2f".format(enchantReduction)}, " +
                "ward=${"%.2f".format(wardReduction)}, " +
                "supremacy=${"%.2f".format(supremacyReduction)}, " +
                "mobResistance=${"%.2f".format(mobReduction)}, " +
                "penetration=${"%.2f".format(penetration)}, " +
                "amount=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, outcome=reduced"
        }
    }

    /**
     * The one mitigation row a hit **no element claims** can still meet: `elemental_supremacy`.
     *
     * A plain swing has no `xx_protection` (there is no element to pick), no mob resistance and no ward,
     * so the elemental chain above would be an all-zero multiply. Supremacy is deliberately different: it
     * is the enchantment's whole promise 閳?"all methods return to one" 閳?so it is the only source that
     * also covers the non-elemental half, using the same 4.5 %/level, 90 % cap zone it applies to elements.
     *
     * `#minecraft:bypasses_enchantments` is still honoured (`true_damage`, `venom`, `reflection`), for the
     * same reason as in [onLivingDamage].
     */
    private fun reduceNonElemental(e: LivingDamageEvent, supremacyReduction: Float) {
        if (supremacyReduction <= 0f) return
        if (e.source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            FSTLog.log("Element") {
                "protected=none, supremacy=${"%.2f".format(supremacyReduction)}, " +
                    "amount=${"%.2f".format(e.amount)}, " +
                    "outcome=reduction-skipped-bypasses-enchantments"
            }
            return
        }
        val before = e.amount
        e.amount = (e.amount * (1.0f - supremacyReduction)).coerceAtLeast(0f)
        FSTLog.log("Element") {
            "protected=none, supremacy=${"%.2f".format(supremacyReduction)}, " +
                "amount=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, " +
                "outcome=reduced-non-elemental"
        }
    }

    // ---------------------------------------------------------------- cross-element helpers

    /**
     * `elemental_ward` level on [entity]'s four armour slots (max 3, so the reduction is capped at
     * `WARD_CAP` = 9 %). Deliberately **not** per element: that is what `xx_protection` is for.
     */
    fun wardLevel(entity: LivingEntity): Int =
        EnchantPresence.armorMax(entity, GeneratedEnchantments.ELEMENTAL_WARD)

    /** `elemental_ward` reduction, capped. */
    fun wardReduction(entity: LivingEntity): Float = ElementMath.wardReduction(wardLevel(entity))

    /**
     * `elemental_resonance` level from the weapon **that dealt this hit** (reaction damage only).
     *
     * [source] is threaded through so an arrow uses the bow it was fired with; `null` means "no source
     * available" (the caller could not supply one), which falls back to the main hand.
     */
    fun resonanceOf(attacker: LivingEntity?, source: DamageSource? = null): Int =
        if (attacker == null) 0
        else getItemSpecificEnchantLevel(attacker.combatStack(source), GeneratedEnchantments.ELEMENTAL_RESONANCE)

    /** Reaction damage multiplier from `elemental_resonance`, capped. */
    fun resonanceMultiplier(attacker: LivingEntity?, source: DamageSource? = null): Float =
        ElementMath.resonanceMultiplier(resonanceOf(attacker, source))

    /** `elemental_penetration` level from the weapon that dealt this hit (see [resonanceOf]). */
    fun penetrationOf(attacker: LivingEntity?, source: DamageSource? = null): Float {
        if (attacker == null) return 0f
        val lvl = getItemSpecificEnchantLevel(attacker.combatStack(source), GeneratedEnchantments.ELEMENTAL_PENETRATION)
        return ElementMath.penetration(lvl)
    }

    // ---------------------------------------------------------------- helpers

    /**
     * True when `LivingEntity#damage` is about to discard this hit because of the target's i-frames.
     *
     * Mirrors the vanilla gate exactly (bytecode offsets 228..260, verified):
     *
     * ```
     * timeUntilRegen > 10 && !source.isIn(#minecraft:bypasses_cooldown) && amount <= lastDamageTaken
     * ```
     *
     * `lastDamageTaken` is `protected`, hence [LivingEntityAccessor]. All three halves are necessary:
     * dropping the `amount` comparison would skip conversion on *legitimate* hits that happen to arrive
     * inside a weakened i-frame window.
     */
    /**
     * True when vanilla's i-frame gate is about to discard this hit.
     *
     * Public because handlers that deal **their own** damage inside `LivingHurtEvent` need the same test: the
     * event fires at the HEAD of `LivingEntity#damage`, before the gate, so a rider queued here can land while
     * the swing it belongs to is thrown away 閳?that is the "elemental damage ignores i-frames" bug this guard
     * exists to prevent (`build=cleanup61`, second time it bit).
     */
    fun wouldBeDiscardedByIFrames(
        target: LivingEntity,
        source: net.minecraft.entity.damage.DamageSource,
        amount: Float,
    ): Boolean {
        if (target.timeUntilRegen <= 10) return false
        if (source.isIn(DamageTypeTags.BYPASSES_COOLDOWN)) return false
        val last = (target as? LivingEntityAccessor)?.modLastDamageTaken ?: return false
        return amount <= last
    }

    /**
     * Last element that hit [entity], for the floating damage number.
     *
     * Deliberately a plain `ConcurrentHashMap` keyed by UUID and **not** cleared per tick: the
     * indicator reads it when it emits a number, and a stale entry only means a number inherits the
     * previous hit's colour 閳?harmless. Clearing it would need a second tick pass to decide *when*,
     * for no benefit.
     */
    private val LAST_ELEMENT = java.util.concurrent.ConcurrentHashMap<UUID, ElementType>()

    private fun rememberElement(entity: LivingEntity, element: ElementType) {
        LAST_ELEMENT[entity.uuid] = element
    }

    /** Ordinal of the element that last hit [entity], or [ElementType.NONE_ORDINAL]. */
    fun lastElementOrdinal(entity: LivingEntity): Int =
        LAST_ELEMENT[entity.uuid]?.ordinal ?: ElementType.NONE_ORDINAL

    /**
     * Sum of the element's `xx_protection` levels across [entity]'s four armour slots.
     *
     * ## `elemental_supremacy`
     * With that enchantment worn, the **highest** single-element total is used for **every** element instead
     * of each element's own total. So four pieces of fire protection defend exactly as well against frost 閳?     * "specialise into one affinity, gain all seven" 閳?but they do **not** stack: the answer is a maximum,
     * not a sum, so wearing one piece of each element still gives you the single highest one.
     *
     * This is deliberately implemented here and nowhere else: [protectionLevel] is the only reader of the
     * protective levels, so the rule cannot disagree with itself (the same "one owner per rule" reasoning as
     * `ConfigBounds`).
     */
    fun protectionLevel(entity: LivingEntity, element: ElementType): Int {
        val own = rawProtectionLevel(entity, element)
        if (!wearsSupremacy(entity)) return own
        var best = own
        for (candidate in ElementType.entries) {
            val level = rawProtectionLevel(entity, candidate)
            if (level > best) best = level
        }
        return best
    }

    /** Per-element sum, without the `elemental_supremacy` override. */
    private fun rawProtectionLevel(entity: LivingEntity, element: ElementType): Int =
        EnchantPresence.armorSum(entity, PROTECTION_KEYS.getValue(element))

    /**
     * `elemental_supremacy`'s own mitigation **zone**: `4.5 % 鑴?total level`, capped at 90 %.
     *
     * This is a separate zone from the 80 % affinity cap, not a refinement of it: at a full set (20 levels)
     * the zone alone is 90 %, and it composes multiplicatively with whatever affinity you have
     * (`1 閳?(1 閳?affinity) 鑴?(1 閳?zone)`). With a maxed affinity that lands at 98 %, which the engine's
     * final clamp reduces to the **95 %** ceiling every elemental hit is subject to.
     */
    /**
     * How much `elemental_supremacy` shortens every ICD, as a multiplier on the window (`1.0` = no change).
     *
     * Scales with the **total level across the four slots**: at 20 levels (level V on all four pieces) the
     * factor reaches 0, i.e. every gate collapses to a single tick. Half investment (10 levels) halves the
     * windows. This is the enchantment's capstone: at full investment the wearer's own attachments and
     * reactions are essentially unthrottled.
     */
    fun supremacyHaste(entity: LivingEntity): Float {
        if (!wearsSupremacy(entity)) return 1f
        return ElementMath.supremacyHaste(
            EnchantPresence.armorLevelOf(entity, GeneratedEnchantments.ELEMENTAL_SUPREMACY)
        )
    }

    /** Shortens one ICD window by [supremacyHaste]; never below one tick. */
    fun hasteWindow(windowTicks: Long, entity: LivingEntity?): Long {
        if (entity == null) return windowTicks
        val factor = supremacyHaste(entity)
        if (factor >= 1f) return windowTicks
        return (windowTicks * factor).toLong().coerceAtLeast(1L)
    }

    fun supremacyReduction(entity: LivingEntity): Float =
        ElementMath.supremacyReduction(EnchantPresence.armorLevelOf(entity, GeneratedEnchantments.ELEMENTAL_SUPREMACY))

    /** Whether [entity] wears `elemental_supremacy` (any armour slot). */
    fun wearsSupremacy(entity: LivingEntity): Boolean =
        EnchantPresence.armorLevelOf(entity, GeneratedEnchantments.ELEMENTAL_SUPREMACY) > 0

    /**
     * True when this hit's damage type is one of **our own** `element_<id>` types.
     *
     * Keyed on the exact registry key, not on the tag, on purpose: a vanilla `minecraft:in_fire` hit is
     * in the fire tag too, and that one **must** stay convertible (otherwise a converted fire hit would
     * never convert, silently, only for fire).
     */
    fun isOwnElementalDamage(e: LivingHurtEvent): Boolean {
        val key = e.source.typeRegistryEntry.key.orElse(null) ?: return false
        if (ElementType.CONVERTIBLE.any { it.damageType == key }) return true
        // `build=cleanup21`: these four are **our own** damage too, and three of them are now real
        // elements (venom = nature, chain_lightning = lightning, celestial_particle = light), so they
        // are in an `elemental_*` tag and `ElementType.of` classifies them. Without this they would take
        // the "already elemental hit" path and be **converted again** 閳?a chain arc carrying a fire
        // conversion, a poison tick spawning extra riders 閳?which is not what any of these enchantments
        // is supposed to do.
        return key == ElementDamageTypes.VENOM ||
            key == ElementDamageTypes.CHAIN_LIGHTNING ||
            key == CelestialDamageSources.CELESTIAL_PARTICLE ||
            key == ModDamageTypes.REFLECTION ||
            // Non-elemental riders (`enqueueUnreduced`, `true_damage`) and the moon/star tiers are ours
            // too. They carry no element, so `ElementType.of` reads `null` and the conversion branch below
            // would happily convert them again 閳?a shield bash with a fire conversion would cancel a share
            // of the bash, mark the target and queue a *second* rider. Removing `PHYSICAL` (which used to
            // sit in `CONVERTIBLE` and therefore caught this) is what exposed it.
            key == ModDamageTypes.TRUE_DAMAGE ||
            key == ModDamageTypes.MOON ||
            key == ModDamageTypes.STAR
    }

    private val ARMOR_SLOTS = arrayOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    /** The `<element>_protection` key per element, so [rawProtectionLevel] never builds a key per hit. */
    private val PROTECTION_KEYS: Map<ElementType, Array<RegistryKey<Enchantment>>> by lazy {
        ElementType.entries.associateWith { element ->
            arrayOf(
                requireNotNull(GeneratedEnchantments.keyOfElement(element.id, protective = true)) {
                    "missing protection key for ${element.id}"
                },
            )
        }
    }

    /** Startup self-check: the per-element protection key table must cover every element once. */
    private fun selfCheckProtectionKeys() {
        if (PROTECTION_KEYS.size == ElementType.entries.size) return
        SimpleTweaks.LOGGER.warn(
            "Elemental protection self-check FAILED: {} key(s) for {} element(s); protection would be under-counted.",
            PROTECTION_KEYS.size,
            ElementType.entries.size,
        )
    }
}
