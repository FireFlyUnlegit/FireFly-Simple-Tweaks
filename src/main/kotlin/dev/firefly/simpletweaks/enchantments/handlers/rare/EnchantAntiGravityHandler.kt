package dev.firefly.simpletweaks.enchantments.handlers.rare

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
 * `anti_gravity`: for a while after release the arrow ignores gravity and flies level — still slowed by
 * drag — so the drop over distance is gone and the aim point is predictable. See [ArrowBallistics].
 */
@ModEnchantment(
    id = "anti_gravity",
    category = EnchantCategory.RARE,
    type = EnchantType.BOW,
    maxLevel = 3,
    weight = EnchantItemWeights.RARE,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 47,
)
object EnchantAntiGravityHandler : Listenable {

    private const val TICKS_PER_LEVEL = 15

    @SubscribeEvent
    fun onEntityJoin(e: EntityJoinWorldEvent) {
        if (e.world.isClient) return
        val arrow = e.entity as? PersistentProjectileEntity ?: return
        val shooter = arrow.owner as? PlayerEntity ?: return
        val level = getItemSpecificEnchantLevel(arrow.firingWeapon(shooter), GeneratedEnchantments.ANTI_GRAVITY)
        if (level <= 0) return
        ArrowBallistics.attach(arrow, 0, TICKS_PER_LEVEL * level)
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        ArrowBallistics.tick(e.world)
    }
}
