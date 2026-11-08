package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot

/**
 * 水下作业 — mining underwater without the vanilla penalty.
 *
 * ## The number is ×(1 + level), not "+25 % per level"
 * Vanilla does not subtract a percentage underwater: `PlayerEntity#getBlockBreakingSpeed` **divides by
 * 5** when the player is submerged and has no Aqua Affinity. Proportional bonuses therefore never
 * cancel it — `+25 %` at IV would leave the player at 40 % of normal speed while *looking* maxed out.
 * Multiplying by `1 + level` makes IV exactly the vanilla helmet behaviour (×5) and gives the lower
 * levels a readable fraction of it (I = ×2).
 *
 * ## Why `BreakSpeed` at the end of the method
 * The seam is injected at `RETURN`, i.e. **after** vanilla has already applied its own penalty, so the
 * multiplier is undoing a value that is really there rather than guessing at the formula.
 *
 * ## Scope
 * Only while submerged. A mining-speed bonus on dry land would overlap `momentum` / `tunneling`, and
 * this enchantment's identity is "the water helmet".
 */
@ModEnchantment(
    id = "aqua_worker",
    category = EnchantCategory.COMMON,
    type = EnchantType.HELMET,
    maxLevel = 4,
    anvilCost = 3,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.HEAD_ARMOR,
    slots = [EnchantSlot.HEAD],
)
object EnchantAquaWorkerHandler : Listenable {

    @SubscribeEvent
    fun onBreakSpeed(e: PlayerEvent.BreakSpeed) {
        val player = e.player
        if (!player.isSubmergedInWater) return

        val level = getItemSpecificEnchantLevel(
            player.getEquippedStack(EquipmentSlot.HEAD),
            GeneratedEnchantments.AQUA_WORKER,
        )
        if (level <= 0) return

        val before = e.newSpeed
        e.newSpeed = before * (1f + level)

        FSTLog.log("AquaWorker") {
            "player=${player.name.string}, lvl=$level, speed=$before->${e.newSpeed}, " +
                "outcome=underwater-speed"
        }
    }
}
