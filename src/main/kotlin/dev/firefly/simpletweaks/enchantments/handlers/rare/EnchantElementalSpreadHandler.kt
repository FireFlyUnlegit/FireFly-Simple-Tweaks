package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * 元素扩散 — when a reaction fires, the **trigger** element is attached to nearby enemies.
 *
 * ## What it does *not* do
 * It only attaches an aura; it never deals damage and never triggers a second reaction. That is deliberate:
 * chaining reactions from one hit would make group fights a damage amplifier independent of the weapon, and
 * it would need an auxiliary-flag path of its own. Attaching is enough to make the next hit on a neighbour
 * react, which is the interesting part.
 *
 * ## Seam
 * `ReactionSeam` (fired by `ElementMarks` after a reaction resolves). Registration happens in [init], so it
 * is idempotent per process — the listener is a single object identity.
 */
@ModEnchantment(
    id = "elemental_spread",
    category = EnchantCategory.RARE,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 6,
    minCostBase = 22,
    supportedItems = EnchantSupportedItemTags.CONVERSION_WEAPONS,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantElementalSpreadHandler : Listenable {

    private const val BASE_RADIUS = 2.0
    private const val RADIUS_PER_LEVEL = 0.5
    private const val UNITS_PER_LEVEL = 0.5f

    override fun init() {
        ReactionSeam.register { attacker, target, element, _ -> spread(attacker, target, element) }
    }

    private fun spread(attacker: LivingEntity?, target: LivingEntity, element: ElementType) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return

        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.ELEMENTAL_SPREAD)
        if (level <= 0) return

        val radius = BASE_RADIUS + RADIUS_PER_LEVEL * level
        val units = UNITS_PER_LEVEL * level
        val box = target.boundingBox.expand(radius)

        var hit = 0
        for (other in target.world.getEntitiesByClass(LivingEntity::class.java, box) { it !== target && it.isAlive }) {
            ElementAuras.attach(other, element, units)
            hit++
        }
        FSTLog.log("Spread") {
            "element=${element.id}, units=${"%.2f".format(units)}, radius=${"%.2f".format(radius)}, " +
                "targets=$hit, outcome=spread"
        }
    }
}
