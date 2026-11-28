package dev.firefly.simpletweaks.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElementMathTest {

    private val eps = 1e-6f

    @Test
    fun `conversion fraction starts at base level and caps`() {
        assertEquals(0f, ElementMath.conversionFraction(0), eps)
        assertEquals(0f, ElementMath.conversionFraction(-3), eps)
        assertEquals(0.015f, ElementMath.conversionFraction(1), eps)
        assertEquals(0.045f, ElementMath.conversionFraction(3), eps)
        assertEquals(0.075f, ElementMath.conversionFraction(5), eps)
        assertEquals(ElementMath.MAX_FRACTION, ElementMath.conversionFraction(9), eps)
        assertEquals(ElementMath.MAX_FRACTION, ElementMath.conversionFraction(200), eps)
    }

    @Test
    fun `a conversion never makes a hit smaller`() {
        for (level in 0..20) {
            for (mastery in listOf(1.0f, 1.5f, 3.0f)) {
                val net = ElementMath.netMultiplier(level, mastery)
                assertTrue(net >= 1f - eps, "level=$level mastery=$mastery net=$net")
            }
        }
    }

    @Test
    fun `net multiplier at the cap is the documented five percent bonus`() {
        assertEquals(1f, ElementMath.netMultiplier(0), eps)
        // (1 - 0.075) + 1.5 * 0.075 = 1.0375
        assertEquals(1.0375f, ElementMath.netMultiplier(5), eps)
    }

    @Test
    fun `mastery multiplier applies per level and caps`() {
        assertEquals(1f, ElementMath.masteryMultiplier(0), eps)
        assertEquals(1f, ElementMath.masteryMultiplier(-1), eps)
        assertEquals(1.1f, ElementMath.masteryMultiplier(1), eps)
        assertEquals(1.0f + ElementMath.MASTERY_CAP, ElementMath.masteryMultiplier(20), eps)
        assertEquals(1.0f + ElementMath.MASTERY_CAP, ElementMath.masteryMultiplier(99), eps)
    }

    @Test
    fun `protection reduction is per level and capped`() {
        assertEquals(0f, ElementMath.protectionReduction(0), eps)
        assertEquals(0.04f, ElementMath.protectionReduction(1), eps)
        assertEquals(0.2f, ElementMath.protectionReduction(5), eps)
        assertEquals(ElementMath.PROTECTION_CAP, ElementMath.protectionReduction(20), eps)
    }

    @Test
    fun `ward resonance and penetration share the same per-level cap shape`() {
        assertEquals(0.03f, ElementMath.wardReduction(1), eps)
        assertEquals(ElementMath.WARD_CAP, ElementMath.wardReduction(3), eps)
        assertEquals(ElementMath.WARD_CAP, ElementMath.wardReduction(9), eps)

        assertEquals(1f, ElementMath.resonanceMultiplier(0), eps)
        assertEquals(1.1f, ElementMath.resonanceMultiplier(1), eps)
        assertEquals(1f + ElementMath.RESONANCE_CAP, ElementMath.resonanceMultiplier(3), eps)

        assertEquals(0f, ElementMath.penetration(0), eps)
        assertEquals(0.08f, ElementMath.penetration(1), eps)
        assertEquals(ElementMath.PENETRATION_CAP, ElementMath.penetration(3), eps)
    }

    @Test
    fun `the mob path re-deals the same bonus shape as the player path`() {
        // Player: fixed bonus, mastery scales it. Mob: its own bonus constant, no mastery.
        assertEquals(1.5f * 0.2f, ElementMath.elementalShare(0.2f), eps)
        assertEquals(2f * 0.1f, ElementMath.elementalShare(0.1f, 1.0f), eps)
        assertEquals(0f, ElementMath.elementalShare(0f), eps)
        assertEquals(1.1f, ElementMath.netMultiplierFor(0.2f), eps)
        // 0.9 kept + (1 + 1.0) * 0.1 re-dealt
        assertEquals(1.1f, ElementMath.netMultiplierFor(0.1f, 1.0f), eps)
        assertEquals(1f, ElementMath.netMultiplierFor(0f), eps)
    }

    @Test
    fun `supremacy reduction and haste read the same summed level`() {
        assertEquals(0f, ElementMath.supremacyReduction(0), eps)
        assertEquals(0.045f, ElementMath.supremacyReduction(1), eps)
        assertEquals(ElementMath.SUPREMACY_CAP, ElementMath.supremacyReduction(20), eps)
        assertEquals(1f, ElementMath.supremacyHaste(0), eps)
        assertEquals(0.95f, ElementMath.supremacyHaste(1), eps)
        assertEquals(0.5f, ElementMath.supremacyHaste(10), eps)
        assertEquals(0f, ElementMath.supremacyHaste(20), eps)
        assertEquals(0f, ElementMath.supremacyHaste(40), eps)
    }

    @Test
    fun `mitigations combine multiplicatively`() {
        // Two independent 50 % sources leave 25 %, not zero.
        assertEquals(0.75f, ElementMath.mitigatedShare(0.5f, 0.5f, 0f, 0f, 0f), eps)
        assertEquals(0.8f, ElementMath.mitigatedShare(0.8f, 0f, 0f, 0f, 0f), eps)
        // Nothing mitigates -> nothing is removed.
        assertEquals(0f, ElementMath.mitigatedShare(0f, 0f, 0f, 0f, 0f), eps)
    }

    @Test
    fun `penetration multiplies the combined mitigation and cannot invert it`() {
        // 80 % protection, 24 % penetration -> 0.8 * 0.76
        assertEquals(0.608f, ElementMath.mitigatedShare(0.8f, 0f, 0f, 0f, 0.24f), eps)
        // Full penetration removes everything that was mitigated, never more.
        assertEquals(0f, ElementMath.mitigatedShare(0.8f, 0.5f, 0.09f, 0.9f, 1f), eps)
    }

    @Test
    fun `the combined share stays inside zero and one`() {
        val shares = listOf(0f, 0.5f, 0.8f, 0.9f, 1f)
        for (enchant in shares) {
            for (mob in shares) {
                for (ward in shares) {
                    for (supremacy in shares) {
                        for (penetration in shares) {
                            val share = ElementMath.mitigatedShare(enchant, mob, ward, supremacy, penetration)
                            assertTrue(
                                share in 0f..1f,
                                "enchant=$enchant mob=$mob ward=$ward supremacy=$supremacy " +
                                    "penetration=$penetration share=$share"
                            )
                        }
                    }
                }
            }
        }
    }
}
