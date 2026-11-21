package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.entity.EquipmentSlot
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import kotlin.random.Random.Default.nextFloat
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantItemFixerHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                        | 1.21.1                                                        |
 * |---------------------------------------------------------------|---------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`                              | `compat.event.TickEvent`                                      |
 * | `EntityLivingBase.displayedItem` (util)                       | `util/PlayerUtils.kt`'s `displayedItem` (now on `LivingEntity`) |
 * | `ItemStack.itemDamage` (read + write)                          | `ItemStack.damage` (`getDamage` method_7934 / `setDamage` method_7939) |
 * | `p.heldItemMainhand` / `p.heldItemOffhand` inside `displayedItem` | `mainHandStack` / `offHandStack`                           |
 * | `EnchantItemFixer` (Enchantment object)                       | `GeneratedEnchantments.ITEM_FIXER` (RegistryKey)                 |
 *
 * Note: `ItemStack.getDamage()`/`setDamage(int)` still exist in 1.21.1 (they were only replaced by
 * the `minecraft:damage` component in 1.21.2+), so the direct read/write of the damage value — the
 * whole point of this handler — ports unchanged. `nextFloat() <= 0.02 * level` mixes Float and
 * Double exactly as the 1.12.2 source did.
 */
@ModEnchantment(
    id = "item_fixer",
    category = EnchantCategory.RARE,
    type = EnchantType.BREAKABLE,
    maxLevel = 8,
    weight = EnchantItemWeights.RARE,
    anvilCost = 6,
    minCostBase = 20,
    minCostPerLevel = 5,
    supportedItems = EnchantSupportedItemTags.DURABILITY,
    slots = [EnchantSlot.ANY],
    order = 22,
)
object EnchantItemFixerHandler : Listenable {

    /** `LivingEntity.displayedItem` without the per-tick list + filter allocations. */
    private val DISPLAYED = arrayOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
    )

    @SubscribeEvent
    fun onPlayerTick(e:TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        for (slot in DISPLAYED) {
            val stack = p.getEquippedStack(slot)
            if (stack.isEmpty) continue
            val level = getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ITEM_FIXER)
            if (level > 0) {
                val roll = nextFloat()
                if (roll <= 0.02 * level && stack.damage > 0) {
                    val damageBefore = stack.damage
                    stack.damage -= (1 + level / 2).coerceAtMost(stack.damage)
                    FSTLog.log("ItemFixer") {
                        "player=${p.name.string}, item=${stack.item}, lvl=$level, roll=$roll, " +
                            "chance=${0.02 * level}, damage=$damageBefore->${stack.damage}"
                    }
                }
            }
        }
    }
}
