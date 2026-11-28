package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.SwordBlocking
import dev.firefly.simpletweaks.util.damagesource.auxDamageInFlight
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.util.Hand
import java.util.WeakHashMap

@ModEnchantment(
    id = "auto_block",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 4,
    weight = EnchantItemWeights.RARE,
    anvilCost = 6,
    minCostBase = 20,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.0,
    order = 106,
)
object EnchantAutoBlockHandler : Listenable {

    private const val CHANCE_PER_LEVEL = 0.10f
    private const val HOLD_TICKS = 1L

    private val autoBlockUntil = WeakHashMap<PlayerEntity, Long>()

    @SubscribeEvent(priority = EventPriority.HIGHEST - 20)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (auxDamageInFlight) return
        if (e.source.isIn(DamageTypeTags.BYPASSES_ARMOR)) return
        val player = e.entityLiving as? PlayerEntity ?: return
        if (player.world.isClient) return
        if (player.isUsingItem) return

        val stack = player.mainHandStack
        if (!SwordBlocking.isBlockingSword(stack)) return
        val level = getItemSpecificEnchantLevel(stack, GeneratedEnchantments.AUTO_BLOCK)
        if (level <= 0) return
        if (player.world.random.nextFloat() >= level * CHANCE_PER_LEVEL) return

        player.setCurrentHand(Hand.MAIN_HAND)
        autoBlockUntil[player] = player.world.time + HOLD_TICKS
        FSTLog.log("AutoBlock") {
            "player=${player.name.string}, level=$level, outcome=triggered"
        }
    }

    @SubscribeEvent
    fun onServerTick(e: TickEvent.ServerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        if (autoBlockUntil.isEmpty()) return

        val iter = autoBlockUntil.iterator()
        while (iter.hasNext()) {
            val (player, until) = iter.next()
            if (player.isRemoved || !player.isAlive) {
                iter.remove()
                continue
            }
            if (player.world.time < until) continue
            if (player.isUsingItem && SwordBlocking.isBlockingSword(player.activeItem)) {
                player.clearActiveItem()
            }
            iter.remove()
        }
    }
}
