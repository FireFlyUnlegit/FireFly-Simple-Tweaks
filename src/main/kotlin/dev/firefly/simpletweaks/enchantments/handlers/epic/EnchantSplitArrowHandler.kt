package dev.firefly.simpletweaks.enchantments.handlers.epic

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
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantTrackingArrowHandler
import dev.firefly.simpletweaks.util.firingWeapon
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.Vec3d
import java.util.Collections
import java.util.WeakHashMap

@ModEnchantment(
    id = "split_arrow",
    category = EnchantCategory.EPIC,
    type = EnchantType.BOW,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 10,
    minCostBase = 30,
    minCostPerLevel = 12,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 91,
)
object EnchantSplitArrowHandler : Listenable {

    // Damage each child keeps: the arrow splits, the total does not double.
    private val FACTORS = floatArrayOf(0.60f, 0.75f, 0.90f)

    // How far a child looks for the mob it should peel off after.
    private const val ALT_RANGE = 24.0

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
        // One child peels off after a second mob instead of trailing the arrow it came from.
        val alt = world
            .getNonSpectatingEntities(LivingEntity::class.java, arrow.boundingBox.expand(ALT_RANGE))
            .filter { it !== shooter && it !== target && it.isAlive && !it.isTeammate(shooter) }
            .minByOrNull { it.squaredDistanceTo(arrow) }
        val hunterLevel = maxOf(
            getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.TRACKING_ARROW),
            level,
        )
        repeat(2) { index ->
            val child = ArrowEntity(world, shooter, ItemStack(Items.ARROW), weapon)
            child.setPosition(arrow.x, arrow.y, arrow.z)
            child.setDamage(arrow.damage * factor.toDouble())
            child.isCritical = arrow.isCritical
            // The children are a refund of this shot, not free ammo.
            child.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED
            if (index == 0 && alt != null) {
                val toAlt = Vec3d(
                    alt.x - child.x,
                    alt.y + alt.height / 2.0 - child.y,
                    alt.z - child.z,
                ).normalize()
                child.setVelocity(toAlt.x, toAlt.y, toAlt.z, arrow.velocity.length().toFloat(), 0.0f)
                EnchantTrackingArrowHandler.attach(child, hunterLevel, target.id)
            } else {
                val spread = Math.toRadians(if (index == 0) 18.0 else -18.0).toFloat()
                val velocity = arrow.velocity.rotateY(spread)
                child.setVelocity(velocity.x, velocity.y, velocity.z, 1.0f, 0.0f)
            }
            child.velocityModified = true
            split[child] = true
            world.spawnEntity(child)
        }

        FSTLog.log("SplitArrow") {
            "player=${shooter.name.string}, lvl=$level, factor=$factor, damage=${arrow.damage}, " +
                "hunter=${alt?.name?.string ?: "none"}, outcome=split"
        }
    }
}
