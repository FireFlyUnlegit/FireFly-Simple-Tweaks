package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingFallEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.boots
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.world.World

/**
 * 震荡落地 — fall damage is halved and the other half leaves as a ground shockwave.
 *
 * ## Why the event is cancelled instead of scaled
 * [LivingFallEvent] exposes `distance` / `damageMultiplier` as **read-only** on purpose (its KDoc explains
 * that mutating them needs two `@ModifyVariable`s whose argument capture is ambiguous). So this handler
 * cancels the vanilla fall (which is exactly "return false" in `handleFallDamage`) and then applies its own
 * reduced damage, which is also the only way to know the number the shockwave should be based on.
 *
 * ## Why the shockwave uses elemental damage
 * `damageElemental` sets the auxiliary-damage flag, so the AoE cannot re-enter the on-hit pipeline and
 * multiply every other enchantment on the way down — the same rule that fixed the venom explosion.
 */
@ModEnchantment(
    id = "shockwave_landing",
    category = EnchantCategory.RARE,
    type = EnchantType.BOOTS,
    maxLevel = 3,
    anvilCost = 6,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.FOOT_ARMOR,
    slots = [EnchantSlot.FEET],
)
object EnchantShockwaveLandingHandler : Listenable {

    /** Vanilla starts hurting at 3 blocks of fall. */
    private const val SAFE_DISTANCE = 3.0f
    private const val DAMAGE_KEPT = 0.5f
    private const val PER_LEVEL_DAMAGE = 1.0f

    @SubscribeEvent
    fun onLivingFall(e: LivingFallEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (player.world.isClient) return

        val level = getItemSpecificEnchantLevel(player.boots, GeneratedEnchantments.SHOCKWAVE_LANDING)
        if (level <= 0) return
        if (e.distance <= SAFE_DISTANCE) return

        val vanillaDamage = (e.distance - SAFE_DISTANCE) * e.damageMultiplier
        if (vanillaDamage <= 0f) return

        // Suppress the vanilla fall, then deal the half we keep. Cancelling is how `handleFallDamage`
        // reports "no damage", so this is the same seam `void_protection` uses.
        e.isCanceled = true
        player.damage(player.world.damageSources.fall(), vanillaDamage * DAMAGE_KEPT)

        val world: World = player.world
        val radius = 3.0 + level
        val box = player.boundingBox.expand(radius)
        val shockwave = vanillaDamage * DAMAGE_KEPT * PER_LEVEL_DAMAGE * level / 2f

        var hit = 0
        for (other in world.getEntitiesByClass(LivingEntity::class.java, box) { it !== player && it.isAlive }) {
            other.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, shockwave)
            hit++
        }

        FSTLog.log("ShockwaveLanding") {
            "distance=${"%.1f".format(e.distance)}, level=$level, kept=${"%.2f".format(vanillaDamage * DAMAGE_KEPT)}, " +
                "shockwave=${"%.2f".format(shockwave)}, targets=$hit, outcome=landed"
        }
    }
}
