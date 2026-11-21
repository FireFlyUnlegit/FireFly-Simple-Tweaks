package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
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
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeInstance
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_anti_knockback",
    category = EnchantCategory.ADVANCED_COMMON,
    type = EnchantType.ARMOR,
    maxLevel = 2,
    ladderBase = 2,
    weight = EnchantItemWeights.ADVANCED_COMMON,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 86,
)
object EnchantAdvancedAntiKnockbackHandler : Listenable {


    /** Knockback resistance added per stack of the ladder; vanilla clamps the attribute at 1.0. */
    private const val RESISTANCE_PER_LEVEL = 0.15

    private val RESISTANCE_ID = Identifier.of(SimpleTweaks.MOD_ID, "advanced_anti_knockback")

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        val level = wornLevel(player)
        if (level <= 0) return
        val before = e.amount
        val source = e.source.attacker as? LivingEntity
        source?.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1))
        e.amount *= (1f - 0.02f * level).coerceIn(0.25f, 1f)
        FSTLog.log("AdvancedAntiKnockbackHandler") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied"
        }
    }

    /**
     * The name promises knockback immunity, so the ladder actually moves the vanilla attribute instead of
     * only shaving damage. Recomputed on a tick because the modifier has to follow armour swaps.
     */
    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        val level = wornLevel(player)
        val instance = player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) ?: return
        val wanted = (RESISTANCE_PER_LEVEL * level).coerceIn(0.0, 1.0)
        apply(instance, wanted)
    }

    private fun wornLevel(player: PlayerEntity): Int {
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_ANTI_KNOCKBACK)
        }
        return level
    }

    private fun apply(instance: EntityAttributeInstance, wanted: Double) {
        val existing = instance.getModifier(RESISTANCE_ID)
        if (wanted <= 0.0) {
            if (existing != null) instance.removeModifier(RESISTANCE_ID)
            return
        }
        if (existing != null && existing.value == wanted) return
        if (existing != null) instance.removeModifier(RESISTANCE_ID)
        instance.addPersistentModifier(
            EntityAttributeModifier(RESISTANCE_ID, wanted, EntityAttributeModifier.Operation.ADD_VALUE),
        )
    }
}
