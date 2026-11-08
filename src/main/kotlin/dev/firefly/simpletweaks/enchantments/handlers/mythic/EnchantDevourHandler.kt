package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound

/**
 * 吞噬 — kills feed the weapon; the stacks are **stored on the item**, not on the player.
 *
 * ## The number
 * `+2 %` final damage per stack, cap `5 × level` stacks (III = 15 stacks = `+30 %`). Dying halves them.
 *
 * ## Why item NBT (author's choice from the two options offered)
 * "The sword ate those kills" is the fantasy: swapping to a different weapon swaps the growth, and the
 * growth survives a server restart because it is part of the stack. A player-NBT version would instead
 * be a permanent account-wide damage stat, which is exactly the unbounded progression this mod avoids.
 *
 * ## Storage
 * `DataComponentTypes.CUSTOM_DATA` under the key [KEY] — the same vanilla component
 * `DebugItemCommand` already reads/writes, so no custom component registration and no NBT schema of our
 * own. `copyNbt()` returns a copy: mutating it and writing it back is required, because
 * `NbtComponent` is immutable.
 *
 * ## Why the bonus is applied in `LivingHurtEvent`
 * The stacks are read from the **weapon that dealt the hit** (`combatStack`) rather than the current main
 * hand, so a projectile that left the bow before a swap still uses the right weapon (`build=cleanup31`
 * established that rule for every damage lookup). The multiplier is applied at `LivingHurtEvent`, i.e.
 * **before** the crit / combo chain, so `+30 %` is a real `+30 %` of the swing and not a seed for every
 * later multiplier.
 *
 * ## Death penalty
 * Halved, not reset: a death costs progress but never erases it, so the enchantment stays usable for a
 * player who dies occasionally. `Math.floorDiv` keeps the arithmetic in integers (no float rounding).
 */
@ModEnchantment(
    id = "devour",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 40,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantDevourHandler : Listenable {

    /** NBT key inside `CUSTOM_DATA`. Namespaced so it cannot collide with another mod's field. */
    const val KEY = "simple_tweaks:devour_stacks"

    /** Final-damage bonus per stack. */
    const val BONUS_PER_STACK = 0.02f

    /** Stacks a full level can hold: `5 × level`. */
    const val STACKS_PER_LEVEL = 5

    /** Level from [stack], or 0 when it does not carry the enchantment. */
    private fun levelOf(stack: ItemStack): Int =
        getItemSpecificEnchantLevel(stack, GeneratedEnchantments.DEVOUR)

    /** Stack count stored on [stack] (0 when absent). */
    fun stacksOf(stack: ItemStack): Int =
        stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.getInt(KEY) ?: 0

    private fun storeStacks(stack: ItemStack, stacks: Int) {
        val compound = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: NbtCompound()
        if (stacks <= 0) compound.remove(KEY) else compound.putInt(KEY, stacks)
        if (compound.isEmpty) {
            stack.remove(DataComponentTypes.CUSTOM_DATA)
        } else {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound))
        }
    }

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        if (player === e.entityLiving) return

        val weapon = player.combatStack(e.source)
        if (levelOf(weapon) <= 0) return
        val stacks = stacksOf(weapon)
        if (stacks <= 0) return

        val before = e.amount
        e.amount *= 1f + BONUS_PER_STACK * stacks

        FSTLog.log("Devour") {
            "attacker=${player.name.string}, target=${e.entityLiving.type.name.string}, " +
                "stacks=$stacks, damage=$before->${e.amount}, outcome=fed"
        }
    }

    @SubscribeEvent
    fun onLivingDeath(e: LivingDeathEvent) {
        if (e.invalid) return
        val victim = e.entityLiving
        val killer = e.source.attacker as? PlayerEntity

        // A player's own death halves what the weapon has eaten.
        if (victim is PlayerEntity) {
            val held = victim.mainHandStack
            val stacks = stacksOf(held)
            if (levelOf(held) > 0 && stacks > 0) {
                val halved = Math.floorDiv(stacks, 2)
                storeStacks(held, halved)
                FSTLog.log("Devour") {
                    "player=${victim.name.string}, stacks=$stacks->$halved, outcome=halved-on-death"
                }
            }
            return
        }

        if (killer == null || killer === victim) return
        // The *killing* weapon, resolved the same way the hit was.
        val weapon = killer.combatStack(e.source)
        val level = levelOf(weapon)
        if (level <= 0) return

        val cap = STACKS_PER_LEVEL * level
        val before = stacksOf(weapon)
        if (before >= cap) {
            FSTLog.log("Devour") {
                "player=${killer.name.string}, stacks=$before, cap=$cap, outcome=capped"
            }
            return
        }
        storeStacks(weapon, before + 1)

        FSTLog.log("Devour") {
            "player=${killer.name.string}, victim=${victim.type.name.string}, lvl=$level, " +
                "stacks=$before->${before + 1}, cap=$cap, outcome=stack-gained"
        }
    }
}
