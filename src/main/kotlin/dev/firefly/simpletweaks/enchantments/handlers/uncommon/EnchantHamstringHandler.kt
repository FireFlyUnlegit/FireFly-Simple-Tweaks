package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity

/**
 * 断筋 — hits sometimes slow the target.
 *
 * ## Why slowness and not freeze
 * `frostbite` already owns "cold": it stacks a freeze that eventually shatters. Slowness is a different
 * lever (movement only, no damage rider) and does not interact with the element system at all, so the
 * two never compete for the same state.
 *
 * ## Why a chance and not "every hit"
 * A guaranteed slow on every swing is a hard disable against melee mobs — they simply never reach the
 * player. `40 % + 10 % × level` keeps it a tempo tool: at III it is 70 %, i.e. most swings but not all.
 *
 * ## Why `isAuxiliaryDamage`
 * The mod's own riders (poison tick, chain arc, shield bash, reactions) are player-attributed, so
 * without the guard one poisoned target would roll this every second.
 */
@ModEnchantment(
    id = "hamstring",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 5,
    minCostBase = 26,
    supportedItems = EnchantSupportedItemTags.SHARP_WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantHamstringHandler : Listenable {

    /** Base chance, before the per-level part. */
    const val BASE_CHANCE = 0.40f

    /** Added per level. III = 70 %. */
    const val CHANCE_PER_LEVEL = 0.10f

    /** Slowness duration in ticks. 3 s: long enough to matter, short enough to re-apply naturally. */
    const val SLOW_TICKS = 60

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (player === target) return

        val level = getItemSpecificEnchantLevel(
            player.combatStack(e.source),
            GeneratedEnchantments.HAMSTRING,
        )
        if (level <= 0) return

        val chance = (BASE_CHANCE + CHANCE_PER_LEVEL * level).coerceAtMost(1f)
        if (target.world.random.nextFloat() >= chance) return

        // Amplifier 0 = Slowness I and no stacking: only the duration is refreshed, so a fast weapon
        // extends the slow instead of turning it into a permanent root.
        target.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, SLOW_TICKS, 0), player)

        FSTLog.log("Hamstring") {
            "attacker=${player.name.string}, target=${target.type.name.string}, lvl=$level, " +
                "chance=$chance, ticks=$SLOW_TICKS, outcome=slowed"
        }
    }
}
