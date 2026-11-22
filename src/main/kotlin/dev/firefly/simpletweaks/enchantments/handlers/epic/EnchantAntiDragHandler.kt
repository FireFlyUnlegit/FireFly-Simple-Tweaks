package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.event.EntityJoinWorldEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.ArrowBallistics
import dev.firefly.simpletweaks.util.firingWeapon
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity

/**
 * `anti_drag`: for a while after release the arrow keeps its speed — no air drag — so it reaches much
 * farther, while gravity still pulls it down. See [ArrowBallistics] for why this owns the numbers.
 */
@ModEnchantment(
    id = "anti_drag",
    category = EnchantCategory.EPIC,
    type = EnchantType.BOW,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 10,
    minCostBase = 30,
    minCostPerLevel = 12,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 94,
)
object EnchantAntiDragHandler : Listenable {

    private const val TICKS_PER_LEVEL = 20

    @SubscribeEvent
    fun onEntityJoin(e: EntityJoinWorldEvent) {
        if (e.world.isClient) return
        val arrow = e.entity as? PersistentProjectileEntity ?: return
        val shooter = arrow.owner as? PlayerEntity ?: return
        val level = getItemSpecificEnchantLevel(arrow.firingWeapon(shooter), GeneratedEnchantments.ANTI_DRAG)
        if (level <= 0) return
        ArrowBallistics.attach(arrow, TICKS_PER_LEVEL * level, 0)
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        ArrowBallistics.tick(e.world)
    }
}
