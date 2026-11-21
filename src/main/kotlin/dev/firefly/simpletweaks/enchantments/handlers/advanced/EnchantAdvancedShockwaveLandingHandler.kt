package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
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
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import java.util.UUID

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_shockwave_landing",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.ARMOR,
    maxLevel = 4,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_RARE,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 93,
)
object EnchantAdvancedShockwaveLandingHandler : Listenable {


    /** The shockwave is about landing, so it only answers fall damage. */
    private const val COOLDOWN_TICKS = 40L

    /** Retaliation is capped at a multiple of the damage actually taken. */
    private const val MAX_BLAST_FACTOR = 2.0f

    /** Last blast tick per player — a shared field would let one player silence another's. */
    private val lastBlast = java.util.Collections.synchronizedMap(HashMap<UUID, Long>())

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        val level = wornLevel(player)
        if (level <= 0) return
        val before = e.amount
        e.amount *= (1f - 0.03f * level).coerceIn(0.25f, 1f)
        if (before <= 0f) return
        if (!e.source.isIn(DamageTypeTags.IS_FALL)) return

        val now = player.world.time
        val last = lastBlast[player.uuid] ?: Long.MIN_VALUE
        if (now - last < COOLDOWN_TICKS) return
        lastBlast[player.uuid] = now

        val blast = (before * 0.25f * level).coerceAtMost(before * MAX_BLAST_FACTOR)
        val nearby = player.world.getOtherEntities(player, player.boundingBox.expand(3.0)) { other ->
            other is LivingEntity && other !== player && other.isAlive && other !is PlayerEntity
        }
        for (other in nearby) (other as LivingEntity).damageElemental(player, ModDamageTypes.TRUE_DAMAGE, blast)
        if (nearby.isNotEmpty()) player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, 20, 0))
        FSTLog.log("AdvancedShockwaveLanding") {
            "player=${player.name.string}, level=$level, taken=$before, blast=$blast, hit=${nearby.size}, outcome=blast"
        }
    }

    private fun wornLevel(player: PlayerEntity): Int {
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += UpgradeEnchantments.ladderLevelOf(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_SHOCKWAVE_LANDING)
        }
        return level
    }
}
