package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.Vec3d
import java.util.Random

/**
 * Catches a flying arrow/trident instead of taking it: ownership moves to the wearer, the projectile
 * flies back and its damage is recalculated from the wearer's attack damage.
 *
 * Runs at HEAD of `onEntityHit` with a higher mixin priority than the piercing injection, because
 * cancelling that method also skips the projectile's stop-in-place step.
 */
@ModEnchantment(
    id = "mirror",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.CHESTPLATE,
    maxLevel = 4,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 30,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR,
    slots = [EnchantSlot.CHEST],
    order = 57,
)
object EnchantMirrorHandler : Listenable {

    private const val CHANCE_PER_LEVEL = 0.25f
    private const val RETURN_SPEED = 1.5

    @JvmStatic
    fun handleHit(arrow: PersistentProjectileEntity, result: EntityHitResult): Boolean {
        val wearer = result.entity as? PlayerEntity ?: return false
        val level = getItemSpecificEnchantLevel(wearer.chestplate, GeneratedEnchantments.MIRROR)
        if (level <= 0) return false

        // A caught projectile belongs to us now and must never land on the way out.
        if (arrow.owner === wearer) return true

        if (!passesRoll(arrow, wearer, level)) return false
        deflect(arrow, wearer)
        return true
    }

    /** Client and server run `onEntityHit` separately and no packet carries the verdict, so the roll is seeded by the pair. */
    private fun passesRoll(arrow: PersistentProjectileEntity, wearer: PlayerEntity, level: Int): Boolean {
        val seed = arrow.uuid.mostSignificantBits xor wearer.uuid.mostSignificantBits
        val roll = Random(seed).nextFloat()
        return roll < CHANCE_PER_LEVEL * level
    }

    private fun deflect(arrow: PersistentProjectileEntity, wearer: PlayerEntity) {
        val incoming = arrow.velocity
        val outgoing = Vec3d(-incoming.x * RETURN_SPEED, incoming.y.coerceAtLeast(0.2), -incoming.z * RETURN_SPEED)

        arrow.owner = wearer
        arrow.setVelocity(outgoing.x, outgoing.y, outgoing.z, 1.0f, 0.0f)
        arrow.velocityModified = true

        if (!arrow.world.isClient) {
            val power = wearer.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
            if (power > arrow.damage) arrow.setDamage(power)
        }

        FSTLog.log("Mirror") {
            "wearer=${wearer.name.string}, arrow=${arrow.uuid}, incoming=$incoming, outgoing=$outgoing, " +
                "damage=${arrow.damage}, side=${if (arrow.world.isClient) "client" else "server"}, outcome=caught"
        }
    }
}
