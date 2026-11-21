package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import net.minecraft.entity.LivingEntity
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.elements.ElementMarks
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.FireBurn
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_multitude",
    category = EnchantCategory.ADVANCED_MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_MYSTERY,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 100,
)
object EnchantAdvancedMultitudeHandler : Listenable {

    private const val BASE_LEVEL = 2
    private const val BASE_RADIUS = 6.0
    private const val RADIUS_PER_LEVEL = 2.0

    /** How deep the current copy chain already is; `0` outside a copy. */
    private val depth = ThreadLocal.withInitial { 0 }

    override fun init() {
        ReactionSeam.register { attacker, target, element, outcome -> chain(attacker, target, element, outcome.flatDamage) }
    }

    private fun chain(
        attacker: LivingEntity?,
        target: LivingEntity,
        element: ElementType,
        flatDamage: Float,
    ) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.ADVANCED_MULTITUDE)
        if (level <= 0) return
        val current = depth.get()
        // `maxLevel` copies: layer 0 is the original reaction, layers 1..level are the copies.
        if (current >= level) return
        if (depth.get() >= level) return
        val radius = BASE_RADIUS + RADIUS_PER_LEVEL * level
        depth.set(current + 1)
        try {
            val others = player.world.getOtherEntities(player, target.boundingBox.expand(radius)) { candidate ->
                candidate is LivingEntity && candidate !== target && candidate !== player &&
                    candidate.isAlive && candidate !is PlayerEntity
            }
            for (other in others) {
                val living = other as? LivingEntity ?: continue
                ElementAuras.attach(living, element, ElementAuras.ULTRA)
                ElementMarks.recordAndReact(
                    player, living, element, flatDamage,
                    units = ElementAuras.ULTRA, forceAttach = true,
                )
            }
            if (others.isNotEmpty()) {
                FSTLog.log("AdvancedMultitude") {
                    "player=${player.name.string}, level=$level, layer=${current + 1}, copied=${others.size}, outcome=chained"
                }
            }
        } finally {
            depth.set(current)
        }
    }
}