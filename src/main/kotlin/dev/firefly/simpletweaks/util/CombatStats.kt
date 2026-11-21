package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementCombat
import net.minecraft.entity.player.PlayerEntity

/**
 * Every damage-side multiplier that is not a vanilla attribute, combined for display.
 *
 * The handlers apply these one at a time (Arcane Echo on `LivingDamage`, Finality on
 * `LivingDamage`, Heavenly Punishment as its own lightning hit, the surges inside the element
 * engine), so the numbers only stay honest if they are combined the same way they are applied:
 * as separate factors, never as a sum of percentages.
 *
 * Gear is read from the main hand, matching the element engine's own `masteryOf`/`conversionOf`
 * defaults; a projectile's own weapon is a per-hit question the stats screen cannot answer.
 */
object CombatStats {

    const val FINALITY_PER_LEVEL = 0.01
    const val BULWARK_PER_LEVEL = 0.01
    const val ELEMENTAL_SURGE_PER_LEVEL = 0.01
    const val REACTION_SURGE_PER_LEVEL = 0.01

    /** Heavenly Punishment pays out `level` extra lightning hits per swing, i.e. `×(1 + level)`. */
    @JvmStatic
    fun heavenlyMultiplier(level: Int): Double = 1.0 + level.coerceAtLeast(0)

    data class Snapshot(
        val echoLevel: Int,
        val echoUnits: Int,
        val finality: Int,
        val bulwark: Int,
        val elementalSurge: Int,
        val reactionSurge: Int,
        val heavenly: Int,
        val finalDamage: Double,
        val finalReduction: Double,
        val flatReduction: Double,
        val elemental: Double,
        val mastery: Double,
        val conversion: Double,
        val penetration: Double,
        val reaction: Double,
        val ranged: Double,
        val healing: Double,
        val mining: Double,
        val echoActive: Boolean,
    )

    /** `(1 + a)(1 + b) - 1`: two independent factors shown as one percentage. */
    @JvmStatic
    fun combine(a: Double, b: Double): Double = (1.0 + a) * (1.0 + b) - 1.0

    @JvmStatic
    fun of(player: PlayerEntity): Snapshot {
        val echo = ArcaneEcho.statsOf(player)
        val weapon = player.mainHandStack
        val finality = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.FINALITY)
        val elementalSurge = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.ELEMENTAL_SURGE)
        val reactionSurge = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.REACTION_SURGE)
        val heavenly = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.HEAVENLY_PUNISHMENT)
        val bulwark = EnchantPresence.armorLevelOf(player, GeneratedEnchantments.BULWARK)

        val conversion = ElementCombat.conversionOf(player, weapon)
            ?.let { ElementCombat.conversionFraction(it.level).toDouble() } ?: 0.0
        val mastery = ElementCombat.masteryMultiplier(ElementCombat.masteryLevel(weapon)).toDouble()
        val penetration = ElementCombat.penetrationOf(player, null).toDouble()

        // Heavenly Punishment is a multiplier on the same swing, so it belongs in the same factor.
        val finalDamage = combine(
            combine(echo.finalDamage, FINALITY_PER_LEVEL * finality),
            heavenlyMultiplier(heavenly) - 1.0,
        )
        // `elemental_resonance` amplifies every reaction, alongside the echo and the surge.
        val reaction = combine(
            combine(echo.reaction, REACTION_SURGE_PER_LEVEL * reactionSurge),
            ElementCombat.resonanceMultiplier(player, null).toDouble() - 1.0,
        )
        return Snapshot(
            echoLevel = echo.level,
            echoUnits = echo.units,
            finality = finality,
            bulwark = bulwark,
            elementalSurge = elementalSurge,
            reactionSurge = reactionSurge,
            heavenly = heavenly,
            finalDamage = finalDamage,
            finalReduction = combine(echo.finalReduction, BULWARK_PER_LEVEL * bulwark),
            flatReduction = echo.damageReduction,
            elemental = combine(echo.element, ELEMENTAL_SURGE_PER_LEVEL * elementalSurge),
            mastery = mastery,
            conversion = conversion,
            penetration = penetration,
            reaction = reaction,
            ranged = echo.rangedDamage,
            healing = echo.healing,
            mining = echo.miningSpeed,
            echoActive = echo.active,
        )
    }
}
