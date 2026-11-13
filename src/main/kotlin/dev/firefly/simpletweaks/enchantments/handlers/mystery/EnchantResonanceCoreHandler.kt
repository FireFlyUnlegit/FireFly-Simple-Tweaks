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
import dev.firefly.simpletweaks.util.elements.ElementDamageSeam
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap

/** 共鸣核心: reactions stack 共鸣 on the target; each stack +4 % elemental damage, full stacks crit and reset. */
@ModEnchantment(
    id = "resonance_core",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 18,
    minCostBase = 50,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantResonanceCoreHandler : Listenable {

    const val STACKS_PER_LEVEL = 1
    const val BASE_STACKS = 2
    const val BONUS_PER_STACK = 0.04f
    const val STACK_TICKS = 200L
    const val CRIT_MULTIPLIER = 2f
    const val MAX_LEVEL = 3

    private class Resonance(val stacks: Int, val expiresAt: Long)

    private val STACKS = WeakHashMap<LivingEntity, Resonance>()

    fun capFor(level: Int): Int = BASE_STACKS + STACKS_PER_LEVEL * level.coerceIn(0, MAX_LEVEL)

    fun multiplierFor(stacks: Int): Float =
        1f + BONUS_PER_STACK * stacks.coerceIn(0, capFor(MAX_LEVEL))

    override fun init() {
        ReactionSeam.register { attacker, target, _, _ -> addStack(attacker, target) }
        ElementDamageSeam.register { attacker, target, _, amount -> amplify(attacker, target, amount) }
    }

    private fun addStack(attacker: LivingEntity?, target: LivingEntity) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.RESONANCE_CORE)
        if (level <= 0) return

        val cap = capFor(level)
        val now = target.world.time
        val current = STACKS[target]
        val stacks = if (current != null && current.expiresAt > now) current.stacks else 0
        if (stacks >= cap) return

        STACKS[target] = Resonance(stacks + 1, now + STACK_TICKS)
        FSTLog.log("ResonanceCore") {
            "player=${player.name.string}, target=${target.type.name.string}, lvl=$level, " +
                "stacks=${stacks + 1}/$cap, outcome=stack-gained"
        }
    }

    private fun amplify(attacker: LivingEntity?, target: LivingEntity, amount: Float): Float {
        val player = attacker as? PlayerEntity ?: return amount
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.RESONANCE_CORE)
        if (level <= 0) return amount
        val resonance = STACKS[target] ?: return amount
        val now = target.world.time
        if (resonance.expiresAt <= now) {
            STACKS.remove(target)
            FSTLog.log("ResonanceCore") {
                "target=${target.type.name.string}, stacks=${resonance.stacks}, outcome=expired"
            }
            return amount
        }

        // Cap follows the current weapon, so swapping to a lower-level copy cannot keep a 5-stack crit.
        val cap = capFor(level)
        val effective = resonance.stacks.coerceAtMost(cap)
        val multiplier = multiplierFor(effective)
        if (effective < cap) {
            FSTLog.log("ResonanceCore") {
                "target=${target.type.name.string}, stacks=$effective, " +
                    "multiplier=$multiplier, outcome=amplified"
            }
            return amount * multiplier
        }

        STACKS.remove(target)
        FSTLog.log("ResonanceCore") {
            "target=${target.type.name.string}, stacks=$effective, " +
                "multiplier=$multiplier, crit=$CRIT_MULTIPLIER, outcome=critical-and-consumed"
        }
        return amount * multiplier * CRIT_MULTIPLIER
    }

    @JvmStatic
    fun stacksOf(target: LivingEntity): Int = STACKS[target]?.stacks ?: 0
}
