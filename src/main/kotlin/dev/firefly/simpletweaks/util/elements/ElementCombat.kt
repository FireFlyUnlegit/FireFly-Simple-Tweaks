package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.mixin.accessors.LivingEntityAccessor
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.getElementSpecificEnchantLevel
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.mobs.MobElements
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.item.ItemStack
import java.util.UUID

/**
 * The shared engine behind every `xx_conversion` and every `xx_protection` enchantment.
 *
 * ## Conversion — the semantics the author asked for
 * > "Cancel 部分原伤害, 这部分伤害转化为对应元素伤害, 造成额外的元素伤害"
 *
 * For a hit of `damage` landing on a target, with conversion level `L` of element `E` on the
 * attacker's weapon:
 *
 * ```
 * fraction  = BASE_FRACTION + PER_LEVEL_FRACTION × (L - 1)      // capped at MAX_FRACTION
 * cancelled = damage × fraction                                  // removed from the original hit
 * re-dealt  = cancelled × ELEMENT_SCALE                          // as element E
 * bonus     = cancelled × BONUS_SCALE                            // the "额外的" part
 * damage   -= cancelled                                          // the original hit shrinks
 * ```
 *
 * The target therefore receives `damage − cancelled` of the **original** element *and*
 * `re-dealt + bonus` of element `E`.
 *
 * ### The constants
 * | constant | value | meaning |
 * |---|---|---|
 * | [BASE_FRACTION] | `0.10` | level 1 converts a tenth of the hit |
 * | [PER_LEVEL_FRACTION] | `0.05` | +5 points per level above 1 |
 * | [MAX_FRACTION] | `0.50` | never converts more than half, even at level 9 |
 * | [ELEMENT_SCALE] | `1.00` | the converted part keeps its size |
 * | [BONUS_SCALE] | `0.50` | half of it again is added as bonus |
 *
 * At the cap the net multiplier is `1 − 0.5 + 0.5 × 1.5 = 1.25` — **+25% total damage**, all of the
 * added part being elemental. Deliberately modest: conversion's real value is *changing the element*
 * (per-type armour/resistance, and driving reactions), not raw numbers. A big flat multiplier is what
 * got `damage_lottery` removed.
 *
 * ### Why `LivingHurtEvent` (pre-armour) rather than `LivingDamageEvent`
 * "Cancel part of it" has to happen **before** armour is computed, or the target's armour bar has
 * already priced a hit that is about to shrink — which reads as "armour suddenly stopped mattering".
 * `LivingHurtEvent` fires at the head of `Entity#damage`, which is exactly that point.
 *
 * ### Why the re-deal is a separate damage call
 * 1.21 has no mutable damage-source type, and the whole point is that per-element resistances see it.
 * It is dealt through [damageElemental] — the same helper the venom/chain trio uses, including its
 * i-frame handling (clear + retry once) and its credit-to-the-attacker behaviour.
 *
 * ## Ordering (`priority = 20`)
 * Between `NORMAL` (`0`) and `LOW` (`50`). It must run **after** the arrow seam and the direct damage
 * scalers (so it converts a real, already-boosted number) and **before** the `LOW` handlers such as
 * `hunters_mark` / `echo_shot` (so they see the final, converted amount rather than a number that is
 * about to shrink).
 *
 * ## Protection
 * `xx_protection L` reduces incoming damage of that element by `PROTECTION_PER_LEVEL × L`, summed over
 * the four armour slots, capped at [PROTECTION_CAP]. It runs at `LivingDamageEvent` (post-armour),
 * matching `magic_protection` so the two behave identically.
 */
object ElementCombat : dev.firefly.simpletweaks.core.Listenable {

    // ---------------------------------------------------------------- conversion tuning

    /**
     * Converted fraction at level 1. Raised from `0.10` because at the old curve the level-1 net gain
     * was `1.05×`, indistinguishable from vanilla's ±0.5 damage roll (reported as "伤害变低").
     */
    const val BASE_FRACTION = 0.12f

    /** Extra fraction per level above 1. Raised from `0.05` for the same reason as [BASE_FRACTION]. */
    const val PER_LEVEL_FRACTION = 0.06f

    const val MAX_FRACTION = 0.50f
    const val ELEMENT_SCALE = 1.0f
    const val BONUS_SCALE = 0.5f

    // ---------------------------------------------------------------- protection tuning

    const val PROTECTION_PER_LEVEL = 0.04f
    const val PROTECTION_CAP = 0.80f

    // ---------------------------------------------------------------- mastery tuning

    /**
     * Extra elemental damage per mastery level, applied to **the elemental part only**.
     *
     * `elemental_mastery` is the one element enchantment that is *not* tied to an element: it is a
     * global multiplier on every element's output, so it is in neither the conversion exclusive set nor
     * either protection.
     */
    const val MASTERY_PER_LEVEL = 0.10f

    /** Hard cap on the mastery bonus (`2.0` = +200%, i.e. ×3). */
    const val MASTERY_CAP = 2.0f

    /**
     * The converted fraction of `xx_conversion` at [level]: `BASE + PER_LEVEL × (level - 1)`, capped.
     *
     * ## The floor that makes conversion never a downgrade
     * A conversion is **net positive or neutral, by construction**: the re-dealt share is
     * `ELEMENT_SCALE + BONUS_SCALE = 1.5 ×` the cancelled share, so the target always takes
     * `(1 − f) + 1.5f = 1 + 0.5f` times the original hit (before mastery and before resistances).
     *
     * It still *felt* like a downgrade in the field report, for two separate reasons that are both
     * fixed elsewhere and worth recording here because they are what "damage went down" was:
     *
     * 1. **The elemental half was being silently discarded.** `LivingEntity#damage`'s i-frame gate
     *    (`amount <= lastDamageTaken` while `timeUntilRegen > 10`) swallowed it because the element type
     *    was not in `#minecraft:bypasses_cooldown`. The original hit had already set
     *    `lastDamageTaken = original`, and `1.5 × f × original` at the reachable levels (0.30 at V after
     *    the level cap) is smaller than `original`, so it was dropped every time — leaving only the
     *    `1 − f` part. Fixed by the tag; see [damageElemental].
     * 2. **The floor of the curve.** At level 1 (`f = 0.10`) the net is `1.05×`, which is inside the
     *    noise of vanilla's ±0.5 damage roll and reads as "no change or worse". `PER_LEVEL` was raised
     *    from `0.05` to `0.06` and `BASE` from `0.10` to `0.12` so even level 1 is a visible `+6%`, and
     *    V is `f = 0.36` → `+18%` net before mastery.
     *
     * ⚠️ `MAX_FRACTION = 0.50` remains the hard cap: even at level 9 the original hit keeps half its
     * physical identity, which matters because the point of conversion is *changing the element*, not
     * erasing the physical part.
     */
    fun conversionFraction(level: Int): Float {
        if (level <= 0) return 0f
        return (BASE_FRACTION + PER_LEVEL_FRACTION * (level - 1)).coerceAtMost(MAX_FRACTION)
    }

    /**
     * Net damage multiplier of a conversion at [level], relative to the unconverted hit.
     *
     * `(1 − f) + (ELEMENT_SCALE + BONUS_SCALE) × f × mastery` — exposed so the startup self-check and the
     * acceptance checklist can talk about the same number instead of each re-deriving it.
     *
     * With the shipped constants (`ELEMENT_SCALE = 1.0`, `BONUS_SCALE = 0.5`) this is
     * `1 + 0.5 × f × mastery`, which is `≥ 1` for every `f ≥ 0` — i.e. **a conversion can never make
     * the hit smaller**. That is an invariant, not a coincidence: it is what the first field report
     * ("寒冰附魔伤害还变低了") violated, and the self-check below fails the mod loudly if a future
     * rebalance breaks it.
     */
    fun netMultiplier(level: Int, mastery: Float = 1.0f): Float {
        val f = conversionFraction(level)
        if (f <= 0f) return 1.0f
        val kept = 1.0f - f
        val reDealt = (ELEMENT_SCALE + BONUS_SCALE) * f * mastery
        return kept + reDealt
    }

    /**
     * The mastery multiplier for [level]: `1 + MASTERY_PER_LEVEL × level`, capped.
     *
     * `1.0` (no bonus) when absent, so every call site can multiply unconditionally.
     */
    fun masteryMultiplier(level: Int): Float {
        if (level <= 0) return 1.0f
        return (1.0f + MASTERY_PER_LEVEL * level).coerceAtMost(1.0f + MASTERY_CAP)
    }

    /** Mastery level on the attacker's main hand (`elemental_mastery`). */
    fun masteryOf(attacker: PlayerEntity): Int = masteryLevel(attacker.mainHandStack)

    /**
     * Mastery level on one stack.
     *
     * ⚠️ **This used to return 0 for every item in the game**, and the bug is the reason
     * `elemental_mastery` appeared to do nothing at all. It called
     * `keyOfElement("elemental_mastery", protective = false)`, and that helper *builds* a name:
     *
     * ```
     * id = if (protective) "<elementId>_protection" else "<elementId>_conversion"
     * ```
     *
     * so it looked for `simple_tweaks:elemental_mastery_conversion`, which does not exist → `null` →
     * `return 0`. Every conversion line in a real session logged `mastery=1.00`, i.e. the enchantment was
     * never read at all. `keyOfElement` is only correct for the 16 enchantments whose ids are literally
     * `<element>_conversion` / `<element>_protection`; mastery is the seventeenth and is not keyed to an
     * `ElementType`, so it must use its own generated key.
     */
    fun masteryLevel(stack: ItemStack): Int =
        getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ELEMENTAL_MASTERY)

    /** Mastery level from a set of armour slots' stacks (used for armour-borne mastery, if any). */
    fun armorMasteryLevel(entity: LivingEntity): Int {
        var total = 0
        for (slot in ARMOR_SLOTS) {
            total += masteryLevel(entity.getEquippedStack(slot))
        }
        return total
    }

    /**
     * Startup self-check: **a conversion must never reduce the hit.**
     *
     * Runs once from `init()`. It exists because the "conversion made my damage smaller" report was
     * *arithmetically impossible* with the shipped constants, so the bug had to be a discarded damage
     * call — and a future rebalance of `ELEMENT_SCALE` / `BONUS_SCALE` / the fraction curve could make
     * it arithmetically possible for real, silently. A `warn` here is a red flag in any build log, and
     * §AK of the checklist tells the author to report it.
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

    /** [Listenable.init] hook — registers nothing, only runs [selfCheck]. */
    override fun init() {
        selfCheck()
    }

    /** The conversion the attacker's main hand performs, or `null` when there is none.
     *
     * At most one can be present because the eight conversion enchantments share one `exclusive_set`
     * (`#simple_tweaks:exclusive_set/elements`). This does **not** trust that: exclusivity is a datapack
     * property a pack can edit away, so the first match in [ElementType] order wins and a second one is
     * reported as `outcome=multiple-conversions` instead of silently changing which element the player
     * gets.
     */
    fun conversionOf(attacker: PlayerEntity): Conversion? {
        val stack = attacker.mainHandStack
        var found: Conversion? = null
        for (element in ElementType.CONVERTIBLE) {
            // `physical_conversion` is the exception: PHYSICAL is the default element, so a physical
            // conversion means "convert *back* to physical", i.e. it only applies when the incoming hit
            // is some other element. It is still a normal declared enchantment.
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
        return found
    }

    /** One conversion: which element, and at what level. */
    data class Conversion(val element: ElementType, val level: Int)

    /**
     * Per-target: how much health the **previous** converted hit actually removed, end to end
     * (armour on the kept share + the elemental share). Diagnostic only — it exists so the log can show
     * the number the player sees instead of the pre-armour figure, which is what "sword says 9.13,
     * monster loses 5.84" was about.
     *
     * Keyed by UUID in a `WeakHashMap`-free plain map with no lifetime guarantee: entries are
     * overwritten on every hit and never read except for logging. Bounded by the number of entities the
     * player has hit since login — deliberately **not** cleaned up, because doing so would need a tick
     * handler for a log line.
     */
    private val LAST_HIT = java.util.concurrent.ConcurrentHashMap<UUID, Float>()

    // ---------------------------------------------------------------- seams

    /**
     * Conversion, marking and reactions — on the pre-armour seam, **after every other damage handler**.
     *
     * ## Why `priority = 200` and not [EventPriority.LOWEST] (`100`)
     * Conversion must see the **final** damage number: the crit, the mastery, `charged_strike`'s release,
     * `combo`'s stacks, `hunters_mark` — everything that adjusts a hit. If it runs earlier, each of
     * those multiplies the *pre-conversion* figure and then conversion carves its share out of a number
     * that is about to change again, so the elemental share no longer tracks the hit proportionally.
     *
     * But `100` does **not** mean "last": the bus breaks ties by **registration order**, and this object
     * is registered from `SimpleTweaks.onInitialize` **before** `EnchantmentManager.initHandlers()`, so
     * at `100` it would actually run *ahead* of `charged_strike` / `echo_shield` / `aoe_attack` /
     * `adopted_armor` — i.e. "last" in name only. A value above `LOWEST` is the only way to express
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
     * That is the intended direction: conversion is a *distribution* change (physical → elemental), and
     * "how much of this swing is physical" is what those four are about. The elemental remainder is
     * dealt separately and is not subject to a physical splitters' arithmetic.
     *
     * ⚠️ The elemental damage itself is **queued**, never dealt from inside this handler — see
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

        // ⚠️ **A hit a raised shield is about to stop must not be converted.**
        //
        // `LivingHurtEvent` fires at the HEAD of `LivingEntity#damage`, which is **before** the shield
        // check further down the same method:
        //
        //   if (this.blockedByShield(source)) { this.damageShield(amount); … return false; }
        //
        // So this handler used to cancel a share of the incoming damage and queue the elemental rider
        // *before* the shield was ever consulted. The shield then blocked the (now smaller) physical
        // hit — and the rider, which lives in `#minecraft:bypasses_cooldown` and is applied on the
        // world tick, landed anyway. That is the reported "元素伤害可以穿盾": a raised shield stopped
        // only the physical share, mob elemental attacks went straight through, and the barrier was
        // therefore "好难防住".
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

        // Remember what just hit this entity, for the floating damage number — **before** the
        // own-damage early return below.
        //
        // The elemental share of a converted hit *is* one of our own damage types, so the earlier
        // ordering (remember after the return) meant a converted hit was never recorded: every
        // conversion weapon produced a **physical-red** number, on every client, which is the reported
        // "附有元素转化的武器上攻击也是红色字体". Recording first only affects the *colour*; the early
        // return below still stops the element from being converted a second time.
        //
        // Physical is skipped so a plain hit keeps the indicator's original colour.
        if (incoming != ElementType.PHYSICAL) rememberElement(target, incoming)

        // Our own re-dealt element damage must never be converted again — that would chain forever.
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
        // player who is not carrying one of the eight conversions — the "键存在 ≠ 有人读它" shape.
        val mastery = masteryMultiplier(masteryLevel(attacker.mainHandStack))

        val conversion = conversionOf(attacker)

        if (conversion == null) {
            // No conversion enchantment. Two things can still happen here:
            //
            // 1. an **already-elemental** hit (a fire weapon, an arrow, a mob's own elemental source)
            //    gets the mastery multiplier and leaves a mark;
            // 2. a **physical** hit leaves no mark (physical is the "no element" base and reacting on it
            //    would make every plain sword hit mark the target) but **still gets mastery**.
            //
            // ⚠️ Case 2 is the one the first revision got wrong: `if (incoming != PHYSICAL)` wrapped the
            // whole block, so with a sword and no conversion enchantment `elemental_mastery` did
            // *nothing at all* — the reported "元素精通未见成效". Mastery is an elemental-output
            // multiplier, and on a physical hit the elemental output is zero, so the correct behaviour
            // for mastery alone on a plain hit is to do nothing *visible* — but it must still be
            // reachable, otherwise it silently never fires and cannot be told apart from a broken
            // enchantment. It is logged (see `outcome=mastery-only-physical`), which is what makes the
            // two distinguishable.
            if (incoming == ElementType.PHYSICAL) {
                if (mastery != 1.0f) {
                    FSTLog.log("Element") {
                        "incoming=physical, mastery=${"%.2f".format(mastery)}, " +
                            "note=no element to scale (equip a conversion for mastery to apply), " +
                            "outcome=mastery-only-physical"
                    }
                }
                return
            }

            val before = e.amount
            e.amount *= mastery
            ElementMarks.record(attacker, target, incoming, e.amount)
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
            ElementMarks.record(attacker, target, element, e.amount)
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

        // Mastery scales the **elemental part only**, never the cancelled physical part — that is what
        // makes it "elemental" mastery and keeps it from becoming a plain damage multiplier. It was
        // already read above; this is the same value, applied to the re-dealt share.
        val cancelled = original * fraction
        val reDealt = (cancelled * ELEMENT_SCALE + cancelled * BONUS_SCALE) * mastery

        // 1) cancel the converted share from the original hit
        e.amount = (original - cancelled).coerceAtLeast(0f)

        // ⚠️ **A swing the i-frame gate is about to discard must not spawn a rider.**
        //
        // `LivingHurtEvent` fires at the HEAD of `LivingEntity#damage`, *before* the gate:
        //
        //   timeUntilRegen > 10 && !source.isIn(#minecraft:bypasses_cooldown)
        //       && amount <= lastDamageTaken   ->   return false   (nothing applied)
        //
        // Clicking faster than the target's i-frames therefore fires this handler for a hit the game will
        // throw away — and the rider we queue for it lives in `#minecraft:bypasses_cooldown` (it must, or
        // it could never land), so **the elemental share went through while the swing did not**. Free
        // damage on every discarded click; that is the reported "元素伤害无视无敌帧去攻击敌人".
        //
        // ⚠️ **The amount must be the one vanilla will see, i.e. `e.amount` AFTER the cancellation above.**
        // The first revision of this guard tested the *incoming* `original`, and that is why it never fired:
        // with `original=9.13`, `kept=5.84` and `lastDamageTaken=5.84`, the pre-conversion comparison
        // (`9.13 <= 5.84`) was false — so the guard passed — while vanilla then compared the rewritten
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
        // ⚠️ This is the fix for "20 HP takes 4 hits", and the mechanism is worth spelling out because
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
        // lost only the elemental 4.93 per hit (`prev_armor_kept=4.93`) — so a 20 HP mob needed
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
        val reacted = ElementMarks.recordAndReact(attacker, target, element, original * mastery)

        // `FSTLog`, i.e. **off unless `-Dsimpletweaks.debug=true`** (or `SIMPLETWEAKS_DEBUG=1`).
        // This was unconditional for one build while the "damage is lower" report was being chased —
        // see `DEVLOG.md` §12.6 for what it was needed for (it is what produced the `prev=` number that
        // finally located the real bug). Once that was found it goes back behind the switch: one line
        // per converted hit is far too much for normal play.
        //
        // `prev_total` is still worth keeping: it is the end-to-end health the *previous* converted hit
        // removed (physical share after armour + elemental share), which is the only figure that can be
        // compared against what the player sees.
        FSTLog.log("Element") {
            "element=${element.id}, lvl=${conversion.level}, " +
                "fraction=${"%.2f".format(fraction)}, mastery=${"%.2f".format(mastery)}, " +
                "incoming=${incoming.id}, " +
                "original=${"%.2f".format(original)}, kept=${"%.2f".format(e.amount)}, " +
                "planned_elemental=${"%.2f".format(reDealt)}, landed=pending, " +
                "reaction=${reacted ?: "none"}, " +
                "prev_total=${LAST_HIT[target.uuid]?.let { "%.2f".format(it) } ?: "n/a"}, " +
                "outcome=converted"
        }
        LAST_HIT[target.uuid] = healthAtStart - target.health
    }

    /** One elemental hit waiting to be dealt after the current damage call unwinds. */
    private class PendingElement(
        val attacker: LivingEntity,
        val target: LivingEntity,
        val element: ElementType,
        val amount: Float,
        /** The triggering hit's damage after mastery — the reaction table scales from this. */
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
        if (amount <= 0f) return
        PENDING.computeIfAbsent(target.world) { java.util.ArrayDeque() }.add(
            PendingElement(attacker, target, element, amount, amount, target.health),
        )
    }

    /** Queued elemental damage, one deque per world, flushed on the server tick. */
    private val PENDING = java.util.concurrent.ConcurrentHashMap<net.minecraft.world.World, java.util.ArrayDeque<PendingElement>>()

    /**
     * Deals every queued elemental hit and logs the end-to-end total.
     *
     * Runs on `WorldTickEvent` (END), i.e. after every `LivingEntity#damage` of the tick has fully
     * unwound — which is what makes `healthAtStart - health` a trustworthy total for the whole hit.
     */
    @SubscribeEvent
    fun onWorldTick(e: dev.firefly.simpletweaks.compat.event.TickEvent.WorldTickEvent) {
        if (e.phase != dev.firefly.simpletweaks.compat.event.TickEvent.Phase.END) return
        val queue = PENDING[e.world] ?: return
        while (queue.isNotEmpty()) {
            val pending = queue.poll() ?: break
            val target = pending.target
            // Re-check on flush: the target may have died to the physical share of the very hit that
            // queued this, in which case dealing more damage would be a phantom hit on a corpse.
            if (!target.isAlive) continue
            val before = target.health
            val landed = target.damageElemental(pending.attacker, pending.element.damageType, pending.amount)
            val actual = (before - target.health).coerceAtLeast(0f)
            // Debug-gated like the conversion line above — same `[FST-Element]` tag, debug switch.
            FSTLog.log("Element") {
                "element=${pending.element.id}, " +
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
     * The elemental part is **queued** like every other re-deal — dealing damage from inside a
     * `LivingHurtEvent` corrupts `EventSeams`' cache (see `DEVLOG` §12.6).
     */
    private fun handleMobAttack(attacker: LivingEntity, target: LivingEntity, incoming: ElementType, e: LivingHurtEvent) {
        val mob = attacker as? MobEntity ?: return
        val element = MobElements.elementOf(mob) ?: return
        if (incoming == element) return

        val original = e.amount
        if (original <= 0f) return

        val cancelled = original * MobElements.CONVERSION_FRACTION
        val reDealt = cancelled * (1.0f + MobElements.BONUS_SCALE)
        e.amount = (original - cancelled).coerceAtLeast(0f)

        rememberElement(target, element)
        enqueueElemental(attacker, target, element, reDealt)

        FSTLog.log("Element") {
            "mob=${mob.type.name.string}, element=${element.id}, " +
                "amount=${"%.2f".format(original)}->${"%.2f".format(e.amount)}, " +
                "elemental=${"%.2f".format(reDealt)}, outcome=mob-elemental-attack"
        }
    }

    /** Protection, on the post-armour seam. */
    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val element = ElementType.of(e.source)

        // A player's own `xx_protection` and a mob's own element resistance are independent sources that
        // happen to act on the same number; they are combined multiplicatively rather than added, so
        // neither can drive the total past 100 % by itself.
        val level = protectionLevel(e.entityLiving, element)
        val enchantReduction =
            if (level > 0) (PROTECTION_PER_LEVEL * level).coerceAtMost(PROTECTION_CAP) else 0f
        val mob = e.entityLiving as? MobEntity
        val mobReduction = if (mob != null) MobElements.resistanceFor(mob, element) else 0f

        if (enchantReduction <= 0f && mobReduction <= 0f) return

        val before = e.amount
        e.amount = (e.amount * (1.0f - enchantReduction) * (1.0f - mobReduction)).coerceAtLeast(0f)

        FSTLog.log("Element") {
            "protected=${element.id}, lvl=$level, enchantReduction=${"%.2f".format(enchantReduction)}, " +
                "mobResistance=${"%.2f".format(mobReduction)}, " +
                "amount=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, outcome=reduced"
        }
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
    private fun wouldBeDiscardedByIFrames(target: LivingEntity, source: net.minecraft.entity.damage.DamageSource, amount: Float): Boolean {
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
     * previous hit's colour — harmless. Clearing it would need a second tick pass to decide *when*,
     * for no benefit.
     */
    private val LAST_ELEMENT = java.util.concurrent.ConcurrentHashMap<UUID, ElementType>()

    private fun rememberElement(entity: LivingEntity, element: ElementType) {
        LAST_ELEMENT[entity.uuid] = element
    }

    /** Ordinal of the element that last hit [entity], or [ElementType.NONE_ORDINAL]. */
    fun lastElementOrdinal(entity: LivingEntity): Int =
        LAST_ELEMENT[entity.uuid]?.ordinal ?: ElementType.NONE_ORDINAL

    /** Sum of the element's `xx_protection` levels across [entity]'s four armour slots. */
    fun protectionLevel(entity: LivingEntity, element: ElementType): Int {
        var total = 0
        for (slot in ARMOR_SLOTS) {
            total += getElementSpecificEnchantLevel(entity.getEquippedStack(slot), element, protective = true)
        }
        return total
    }

    /**
     * True when this hit's damage type is one of **our own** `element_<id>` types.
     *
     * Keyed on the exact registry key, not on the tag, on purpose: a vanilla `minecraft:in_fire` hit is
     * in the fire tag too, and that one **must** stay convertible (otherwise a converted fire hit would
     * never convert, silently, only for fire).
     */
    fun isOwnElementalDamage(e: LivingHurtEvent): Boolean {
        val key = e.source.typeRegistryEntry.key.orElse(null) ?: return false
        return ElementType.CONVERTIBLE.any { it.damageType == key }
    }

    private val ARMOR_SLOTS = arrayOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )
}
