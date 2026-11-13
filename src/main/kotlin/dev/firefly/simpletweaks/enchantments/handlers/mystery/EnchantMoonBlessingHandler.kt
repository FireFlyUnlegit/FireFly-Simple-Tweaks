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
import java.util.WeakHashMap

/**
 * 月之祝福: a share of every reaction you trigger is re-expressed as **月曜伤害**, which can crit and is
 * amplified by 月华 stacks.
 */
@ModEnchantment(
    id = "moon_blessing",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 20,
    minCostBase = 55,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantMoonBlessingHandler : Listenable {

    /** Share of the reaction re-expressed as lunar damage: 30 % / 40 % / 50 %. */
    const val SHARE_BASE = 0.20f
    const val SHARE_PER_LEVEL = 0.10f

    /** Crit chance and multiplier of the lunar instance. */
    const val CRIT_BASE = 0.15f
    const val CRIT_PER_LEVEL = 0.10f
    const val CRIT_MULTIPLIER = 1.5f

    /** 月华: one stack per conversion, each +5 % lunar damage. */
    const val STACK_BONUS = 0.05f
    const val MAX_STACKS = 5
    const val STACK_TICKS = 200L

    private class Stacks(var count: Int, var expiresAt: Long)

    private val STACKS = WeakHashMap<PlayerEntity, Stacks>()

    fun shareFor(level: Int): Float = SHARE_BASE + SHARE_PER_LEVEL * level.coerceIn(0, 3)

    fun critChanceFor(level: Int): Float = CRIT_BASE + CRIT_PER_LEVEL * level.coerceIn(0, 3)

    override fun init() {
        ReactionSeam.register { attacker, target, element, outcome -> onReaction(attacker, target, element, outcome.flatDamage) }
    }

    private fun onReaction(attacker: LivingEntity?, target: LivingEntity, element: dev.firefly.simpletweaks.util.elements.ElementType, flatDamage: Float) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.MOON_BLESSING)
        if (level <= 0 || flatDamage <= 0f) return

        val now = player.world.time
        val stacks = STACKS[player]
        val count = if (stacks != null && stacks.expiresAt > now) stacks.count else 0
        val multiplier = 1f + STACK_BONUS * count
        val crit = player.random.nextFloat() < critChanceFor(level)
        val lunar = flatDamage * shareFor(level) * multiplier * (if (crit) CRIT_MULTIPLIER else 1f)

        target.damageElemental(player, ModDamageTypes.MOON, lunar)
        STACKS[player] = Stacks(minOf(count + 1, MAX_STACKS), now + STACK_TICKS)

        FSTLog.log("MoonBlessing") {
            "element=${element.id}, lvl=$level, reaction=${"%.2f".format(flatDamage)}, " +
                "stacks=$count, crit=$crit, lunar=${"%.2f".format(lunar)}, outcome=lunar-conversion"
        }
    }

    @JvmStatic
    fun stacksOf(player: PlayerEntity): Int = STACKS[player]?.count ?: 0

    @JvmStatic
    fun clear(player: PlayerEntity) {
        STACKS.remove(player)
    }
}
