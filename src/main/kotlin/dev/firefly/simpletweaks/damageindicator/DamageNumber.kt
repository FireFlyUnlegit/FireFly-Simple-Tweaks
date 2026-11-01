package dev.firefly.simpletweaks.damageindicator

import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicator
import dev.firefly.simpletweaks.util.elements.ElementType
import net.minecraft.entity.Entity

/**
 * One floating damage number. Ported 1:1 from 1.12.2 `damageindicator/DamageNumber.kt` (113 lines).
 *
 * <h2>1.12.2 -&gt; 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `entity.posX` / `posY` / `posZ` | `entity.x` / `entity.y` / `entity.z` |
 * | `entity.height` | `entity.height` (`Entity#getHeight`) |
 *
 * Nothing else changed: every formula below (rise easing, fade window, scale decay, colour constants,
 * sign handling, the `%` clamping and the second line) is the original arithmetic.
 * In particular the deliberate quirks are preserved rather than "fixed":
 * <ul>
 *   <li>[getAlpha] starts fading at 60% of `duration` and can in principle divide by zero if
 *       `duration == fadeStart`, i.e. `duration == 0` — the config's lower bound is 10, so it cannot.</li>
 *   <li>`formatFloat` drops the decimals for whole numbers, so `5.0` renders as `5`, not `5.00`.</li>
 * </ul>
 *
 * <p>**One 1.12.2 behaviour was dropped on purpose** (`build=cleanup47`): the `isOverkill`
 * `original(actual)` rendering. `original` came from the damage seam and was therefore a *pre-clamp*
 * figure, so a 150-damage proc on a 48 HP mob displayed `150(48)` and read as "I dealt 150". The number
 * now always shows the health delta the player actually observes; see `PacketDamageIndicator` for the
 * full argument.
 *
 * <p>[fixedX]/[fixedZ], [entityFeetY] and [entityHeight] are computed **once, at construction**, from
 * the entity's position at the moment the packet arrived — that is why a number stays where it was
 * spawned instead of following the mob. This is load-bearing: the randomness is sampled once, not per
 * frame.
 */
data class DamageNumber(
    val entity: Entity,
    var actualDamage: Float,
    val isHeal: Boolean = false,
    val isSelf: Boolean = false,
    val maxHealth: Float = 0f,
    val realMaxHealth: Float = 0f,
    val afterRealHealth: Float = 0f,
    /**
     * Element that caused this number (`ElementType.NONE_ORDINAL` = unknown / physical).
     *
     * Stored as the ordinal the packet carries and resolved in [element] on demand; keeping the enum out
     * of the constructor signature means the client does not have to touch the element registry to
     * build a number.
     */
    val elementOrdinal: Int = ElementType.NONE_ORDINAL,
    /**
     * Which slot of a hit this number is — `PacketDamageIndicator.ROLE_SINGLE` / `ROLE_PHYSICAL` /
     * `ROLE_ELEMENTAL`.
     *
     * It decides **both** the colour context (via [elementOrdinal]) and the **placement** in the XZ
     * region around the entity: a split hit sends two packets, and they must not land on the same spot.
     */
    val role: Int = PacketDamageIndicator.ROLE_SINGLE,
    /**
     * Translation key for a [PacketDamageIndicator.ROLE_LABEL] entry (a reaction name), empty otherwise.
     *
     * The client resolves it in its own language, so this number renders as `感电` / `Conduct` instead of
     * a damage figure — same rise, fade and placement as the numbers, which is the point: a reaction
     * belongs next to the hit, not on the action bar.
     */
    val label: String = "",
) {
    var age: Int = 0

    private companion object {
        /**
         * Radius range for each half of a split hit, in blocks.
         *
         * **Random per number, not fixed**, because a fixed radius made every hit on every mob land in
         * exactly the same pair of spots — after a few hits it reads as two static anchors rather than as
         * damage numbers flying out of the target.
         *
         * The range is what keeps the pair apart: the halves sit in **opposite half-planes** (their
         * angles differ by π), so their separation is `r1 + r2` along the line between them — at least
         * `2 × MIN = 0.36` blocks even when both draw the smallest radius. A single shared random radius
         * would not have that property (it would still work, but the pair would always be symmetric).
         *
         * `cleanup47`: halved (`0.40..0.85` → `0.18..0.42`) on the author's report that the pair flew
         * too far out from the mob it belonged to. The relationship above is what still makes the halves
         * readable at that distance: they never overlap, they just stay close to the hitbox.
         */
        // Widened in `build=cleanup17`: with one number per element the old 0.18–0.42 disc put three
        // numbers within a couple of text widths of each other, so they overlapped ("伤害字符靠的太近").
        const val SPLIT_RADIUS_MIN = 0.38
        const val SPLIT_RADIUS_MAX = 0.78

        /**
         * Rung index of a `ROLE_LABEL` entry: above every numeric row.
         *
         * Labels ("感电" / "融化") are text of one to two blocks; giving them the top rung means they never
         * cover the damage numbers of the same hit.
         */
        const val LABEL_RUNG = 6

        /**
         * Max angular jitter (± this, in radians) added to each half's direction.
         *
         * `0.35 rad ≈ 20°`. Without it the pair is always exactly diametrically opposed, which looks
         * mechanical; with it, each hit still keeps the halves in opposite half-planes — the worst-case
         * angle between them is `π − 0.7 ≈ 2.44 rad` (140°), so they can never swap sides and collide.
         */
        const val SPLIT_ANGLE_JITTER = 0.35

        /**
         * Irrational multiplier that turns an entity id into a well-spread base angle.
         *
         * `id * φ mod 1` is the classic low-discrepancy sequence: consecutive ids land far apart on the
         * circle, so neighbouring mobs do not all get their pair pointing the same way (which `id * 0.1`
         * would do). Deterministic on purpose — the two halves arrive in two separate packets and must
         * independently agree on which **line** the pair lies on; the randomness is layered on top
         * (radius and jitter), where the two sides do not need to agree.
         */
        const val GOLDEN_RATIO = 0.6180339887498949
    }

    /**
     * Angle of this number's offset around the entity centre, in radians.
     *
     * ⚠️ Must be declared **before** [fixedX] / [fixedZ]: they read it while initialising, and in Kotlin
     * a property declared later is still at its default (`0.0`) at that moment. Ordering it after them
     * silently put every split number on the +X axis instead of on a per-entity random one.
     *
     * Derived deterministically from the entity id so the two packets of a split hit — decoded
     * independently on the client — agree on where the radius points. The halves then take opposite ends
     * of it, which is what guarantees the pair is separated without ever transmitting a position.
     */
    private val spreadAngle: Double = ((entity.id * GOLDEN_RATIO) % 1.0) * 2.0 * Math.PI +
        if (role == PacketDamageIndicator.ROLE_ELEMENTAL) {
            // Elements **fan out** instead of stacking. Since `build=cleanup16` a hit emits one number
            // per element (physical / water / lightning / reaction shares), so a fixed `π` offset would
            // pile every elemental number onto the same spot. 0.7 rad ≈ 40° apart, which also keeps them
            // from crossing back onto the physical number's side.
            Math.PI + elementOrdinal * 1.0
        } else {
            0.0
        }

    /**
     * Random per-number direction jitter and radius for a split half.
     *
     * Rolled once at construction (these are `val`), so the position is fixed for the lifetime of the
     * number — the same "do not follow the entity" rule as [fixedX]. The two halves roll **independently**,
     * which is fine: they only have to agree on the base line, and [SPLIT_ANGLE_JITTER] keeps even the
     * worst case on opposite sides.
     */
    private val spreadJitter: Double = (Math.random() - 0.5) * 2.0 * SPLIT_ANGLE_JITTER
    private val spreadRadius: Double =
        SPLIT_RADIUS_MIN + Math.random() * (SPLIT_RADIUS_MAX - SPLIT_RADIUS_MIN)

    /**
     * Fixed XZ placement: the entity's **centre** plus an offset inside a small disc, sampled **once** at
     * construction.
     *
     * These are `val` on purpose — the number must **not** follow the entity (that is the point of the
     * feature). Only Y moves afterwards, and only along the rise.
     *
     * `ROLE_SINGLE` keeps the pre-split behaviour exactly (`±0.3` uniform on each axis), so this feature
     * is invisible while the split option is off. `ROLE_PHYSICAL` / `ROLE_ELEMENTAL` sit at [SPLIT_RADIUS]
     * on opposite ends of [spreadAngle].
     */
    val fixedX: Double = entity.x + if (role == PacketDamageIndicator.ROLE_SINGLE) {
        Math.random() * 0.6 - 0.3
    } else {
        Math.cos(spreadAngle + spreadJitter) * spreadRadius
    }
    val fixedZ: Double = entity.z + if (role == PacketDamageIndicator.ROLE_SINGLE) {
        Math.random() * 0.6 - 0.3
    } else {
        Math.sin(spreadAngle + spreadJitter) * spreadRadius
    }
    /**
     * The entity's feet Y and its height, sampled once at construction.
     *
     * These are cached **separately** rather than as a single `startY` sum because
     * [DamageIndicatorConfig.yStartFactor] multiplies the height term only, not the absolute world
     * height. See [getCurrentY].
     */
    private val entityFeetY: Double = entity.y
    private val entityHeight: Double = entity.height.toDouble()

    private val MAX_RISE: Double = if (isSelf) 1.2 else 1.05

    fun isExpired(): Boolean = age >= DamageIndicatorConfig.duration

    /**
     * The number's current world Y: the spawn anchor plus the eased rise.
     *
     * The anchor is `feetY + height * yStartFactor`, i.e. [DamageIndicatorConfig.yStartFactor] is a
     * multiplier on the **entity's height** and `1.0` puts the number exactly on top of the hitbox.
     *
     * 1.12.2 instead computed `(posY + height) * factor`, multiplying the *absolute world Y*: at
     * y=70 that same `factor = 2.0` spawned the number near y=143, further from the camera than
     * `maxDistance`, so [DamageIndicatorRenderer] skipped it and the indicator silently vanished —
     * with no vanilla fallback, because `cancelVanillaDamageIndicator` defaults to true. Multiplying
     * the height term keeps every value in `0.5..2.0` inside render range while being **bit-identical
     * to 1.12.2 at the default `1.0`**.
     *
     * [entityFeetY] and [entityHeight] are cached, not re-read from [entity], so the number still
     * does not follow the mob.
     */
    fun getCurrentY(): Double {
        val riseDuration = DamageIndicatorConfig.riseDuration.toDouble()
        val riseProgress = (age.toDouble() / riseDuration).coerceAtMost(1.0)
        val eased = 1.0 - (1.0 - riseProgress) * (1.0 - riseProgress)
        val anchorY = entityFeetY + entityHeight * DamageIndicatorConfig.yStartFactor
        return anchorY + eased * MAX_RISE + stagger
    }

    /**
     * Vertical rung for split numbers, fixed for the number's lifetime.
     *
     * With one number per element (physical / water / lightning / reaction shares) the XZ disc alone
     * cannot keep three or four numbers apart: the glyphs are several pixels tall and each number is
     * scaled by its value, so they covered each other ("伤害字符靠的太近，互相遮挡"). Each element gets
     * its own rung (`0.20` blocks), the physical number sits one rung below the first elemental, and a
     * small random wobble stops repeated hits from stacking on exactly the same rungs.
     *
     * `ROLE_SINGLE` stays at 0 so the pre-split behaviour is untouched.
     */
    private val stagger: Double = if (role == PacketDamageIndicator.ROLE_SINGLE) {
        0.0
    } else if (role == PacketDamageIndicator.ROLE_LABEL) {
        // Labels sit **above** the numeric rows of the same hit: they name the reaction the numbers below
        // came from, so the reading order is "感电" then the damage it caused.
        LABEL_RUNG * 0.20
    } else {
        val rung =
            if (role == PacketDamageIndicator.ROLE_PHYSICAL) -1 else elementOrdinal.coerceAtLeast(0)
        rung * 0.20 + (Math.random() - 0.5) * 0.06
    }

    fun getAlpha(): Float {
        val duration = DamageIndicatorConfig.duration
        val fadeStart = duration * 0.6f
        return if (age < fadeStart) 1.0f else 1.0f - (age - fadeStart) / (duration - fadeStart)
    }

    fun getScale(): Float {
        val base = DamageIndicatorConfig.scale.toFloat()
        val selfScale = if (isSelf) 1.3f else 1.0f
        return base * selfScale * (1.0f - 0.15f * (age / DamageIndicatorConfig.duration.toFloat()))
    }

    /**
     * The element behind this number, or `null` when unknown / physical.
     *
     * Resolved lazily: the constructor only stores the ordinal so that constructing a number never
     * touches the element enum (and so a stale ordinal degrades to `null` rather than throwing).
     */
    private val element: ElementType? get() = ElementType.ofOrdinal(elementOrdinal)

    /**
     * Colour of the main line.
     *
     * Heal always wins (`0x00FF00`) — a heal's "element" is meaningless, and the existing green is the
     * only signal that distinguishes healing from damage at a glance.
     *
     * For damage, the element's colour is used when there is one. `PHYSICAL` is the indicator's original
     * red, so an ordinary sword hit is byte-identical to the pre-`cleanup30` behaviour; the eight
     * elemental colours only appear once a hit actually is elemental.
     */
    fun getColor(): Int {
        if (isHeal) return 0x00FF00
        return element?.color ?: 0xFF3333
    }

    private fun formatFloat(value: Float): String {
        return if (value % 1.0f == 0.0f) {
            "${value.toInt()}"
        } else {
            String.format("%.2f", value)
        }
    }

    private fun getSign(): String {
        return if (DamageIndicatorConfig.symbol) {
            if (isHeal) "+" else "-"
        } else {
            ""
        }
    }

    /**
     * The big number: **always** the health actually removed (or restored).
     *
     * `cleanup47` deleted the 1.12.2 "overkill" variant, which rendered `original(actual)` where
     * `original` was a pre-clamp figure from the damage seam. That figure could exceed the target's
     * remaining health by a factor of three (150.1 recorded against a 48 HP mob), so the display told
     * the player they had dealt 150 while 48 came off the bar. There is now exactly one source for the
     * value — the end-of-tick health difference — and it is by construction something the player just
     * watched happen.
     */
    fun getText(): String {
        // A label is not a number: it renders the reaction's translated name, followed by the extra
        // damage that reaction dealt (`感电 (-28.30)`), which is what the author asked for — the name
        // alone left the reader guessing whether the reaction did anything at all.
        if (role == PacketDamageIndicator.ROLE_LABEL) {
            val name = net.minecraft.text.Text.translatable(label).string
            return if (actualDamage > 0f) "$name (-${formatFloat(actualDamage)})" else name
        }
        val percentageMode = DamageIndicatorConfig.percentageMode
        val sign = getSign()

        return if (percentageMode) {
            val percent = (actualDamage / realMaxHealth * 100).coerceAtMost(100f)
            "$sign${formatFloat(percent)}%"
        } else {
            "$sign${formatFloat(actualDamage)}"
        }
    }

    /** `false` for the elemental half of a split hit and for labels, which show only one line. */
    fun hasSecondLine(): Boolean =
        role != PacketDamageIndicator.ROLE_ELEMENTAL && role != PacketDamageIndicator.ROLE_LABEL

    fun getSecondLineText(): String {        val percentageMode = DamageIndicatorConfig.percentageMode
        val healthAfter = afterRealHealth.coerceAtLeast(0f)
        val maxHp = realMaxHealth.coerceAtLeast(1f)

        return if (percentageMode) {
            val percent = (healthAfter / maxHp * 100).coerceAtMost(100f)
            "${formatFloat(percent)}%"
        } else {
            "${formatFloat(healthAfter)}/${formatFloat(maxHp)}"
        }
    }

    /**
     * Colour of the second line (health after / max).
     *
     * A **lightened** variant of the main line's colour rather than its own palette, so the two lines
     * read as one number with a subordinate detail. `+25 %` toward white per channel keeps the hue while
     * guaranteeing the second line is never harder to read than the first.
     */
    fun getSecondLineColor(): Int {
        if (isHeal) return 0x66FF66
        val base = element?.color ?: 0xFF6666
        return lighten(base, 0.25f)
    }

    /** Moves each RGB channel of [color] [amount] of the way toward white. */
    private fun lighten(color: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val c = (color shr shift) and 0xFF
            return (c + (255 - c) * amount).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
