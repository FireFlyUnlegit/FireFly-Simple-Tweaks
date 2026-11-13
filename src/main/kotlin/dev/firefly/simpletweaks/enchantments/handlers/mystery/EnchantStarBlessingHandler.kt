package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * 星之祝福: a share of every reaction you trigger becomes **星曜伤害**, which always crits and splashes to
 * nearby enemies. The author's reference is Genshin's star reactions: crit, and it spreads.
 */
@ModEnchantment(
    id = "star_blessing",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 20,
    minCostBase = 55,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantStarBlessingHandler : Listenable {

    /** Share of the reaction re-expressed as star damage: 25 % / 35 % / 45 %. */
    const val SHARE_BASE = 0.15f
    const val SHARE_PER_LEVEL = 0.10f

    /** Star instances always crit; the multiplier is fixed. */
    const val CRIT_MULTIPLIER = 1.5f

    /** Splash: radius and share, so the star is the area option while the moon is the stacking one. */
    const val SPLASH_RADIUS = 4.0
    const val SPLASH_SHARE = 0.5f

    fun shareFor(level: Int): Float = SHARE_BASE + SHARE_PER_LEVEL * level.coerceIn(0, 3)

    /**
     * Only **frost-driven** reactions can go stellar (Genshin's 星烁反应 are the 冰 family: 星超导/星扩散).
     * The moon owns the water family; the two must not overlap.
     */
    private val STELLAR_REACTIONS = setOf("melt", "vanish", "overload", "eclipse", "freeze", "brittle")

    override fun init() {
        ReactionSeam.register { attacker, target, element, outcome -> onReaction(attacker, target, element, outcome) }
    }

    private fun onReaction(attacker: LivingEntity?, target: LivingEntity, element: dev.firefly.simpletweaks.util.elements.ElementType, outcome: dev.firefly.simpletweaks.util.elements.ReactionOutcome) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return
        if (outcome.key == null || outcome.key !in STELLAR_REACTIONS) return
        val flatDamage = outcome.flatDamage
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.STAR_BLESSING)
        if (level <= 0 || flatDamage <= 0f) return

        val star = flatDamage * shareFor(level) * CRIT_MULTIPLIER
        target.damageElemental(player, ModDamageTypes.STAR, star)

        var splashed = 0
        val splash = star * SPLASH_SHARE
        for (other in player.world.getOtherEntities(player, target.boundingBox.expand(SPLASH_RADIUS)) { it is LivingEntity }) {
            val living = other as LivingEntity
            if (living == target || living == player || !living.isAlive) continue
            living.damageElemental(player, ModDamageTypes.STAR, splash)
            splashed++
        }

        FSTLog.log("StarBlessing") {
            "element=${element.id}, lvl=$level, reaction=${"%.2f".format(flatDamage)}, " +
                "star=${"%.2f".format(star)}, splashed=$splashed, outcome=star-conversion"
        }
    }
}
