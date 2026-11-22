package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.firingWeapon
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import java.util.Collections
import java.util.WeakHashMap

@ModEnchantment(
    id = "split_arrow",
    category = EnchantCategory.RARE,
    type = EnchantType.BOW,
    maxLevel = 3,
    weight = EnchantItemWeights.RARE,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 91,
)
object EnchantSplitArrowHandler : Listenable {

    // Damage each child keeps: the arrow splits, the total does not double.
    private val FACTORS = floatArrayOf(0.60f, 0.75f, 0.90f)

    // One split per arrow. Identity keys: an arrow that already split must not split again when a
    // child hits something.
    private val split: MutableMap<PersistentProjectileEntity, Boolean> =
        Collections.synchronizedMap(WeakHashMap())

    @JvmStatic
    fun onHit(arrow: PersistentProjectileEntity, target: Entity?) {
        if (arrow.world.isClient) return
        if (split.containsKey(arrow)) return
        if (target == null) return
        val shooter = arrow.owner as? LivingEntity ?: return
        val weapon = arrow.firingWeapon(shooter)
        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.SPLIT_ARROW)
        if (level <= 0) return
        if (arrow.velocity.lengthSquared() < 0.01) return

        split[arrow] = true
        val factor = FACTORS[(level - 1).coerceIn(0, FACTORS.size - 1)]
        val world = arrow.world
        repeat(2) { index ->
            val child = ArrowEntity(world, shooter, ItemStack(Items.ARROW), weapon)
            child.setPosition(arrow.x, arrow.y, arrow.z)
            child.setDamage(arrow.damage * factor.toDouble())
            child.isCritical = arrow.isCritical
            // The children are a refund of this shot, not free ammo.
            child.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED
            val spread = Math.toRadians(if (index == 0) 18.0 else -18.0).toFloat()
            val velocity = arrow.velocity.rotateY(spread)
            child.setVelocity(velocity.x, velocity.y, velocity.z, 1.0f, 0.0f)
            child.velocityModified = true
            split[child] = true
            world.spawnEntity(child)
        }

        FSTLog.log("SplitArrow") {
            "player=${shooter.name.string}, lvl=$level, factor=$factor, " +
                "damage=${arrow.damage}, outcome=split"
        }
    }
}
