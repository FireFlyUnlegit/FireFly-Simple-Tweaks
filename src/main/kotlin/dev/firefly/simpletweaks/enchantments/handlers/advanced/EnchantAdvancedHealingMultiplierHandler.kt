package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
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
import dev.firefly.simpletweaks.util.FireBurn
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.util.removeAbsorptionHeadroom
import java.util.Collections
import java.util.UUID
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_healing_multiplier",
    category = EnchantCategory.ADVANCED_LEGENDARY,
    type = EnchantType.CHESTPLATE,
    maxLevel = 4,
    ladderBase = 10,
    weight = EnchantItemWeights.ADVANCED_LEGENDARY,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR,
    slots = [EnchantSlot.CHEST],
    order = 85,
)
object EnchantAdvancedHealingMultiplierHandler : Listenable {

    private const val TTL_TICKS = 200L
    private val banked: MutableMap<UUID, Float> = Collections.synchronizedMap(HashMap())
    private val expiry: MutableMap<UUID, Long> = Collections.synchronizedMap(HashMap())


    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val own = getItemSpecificEnchantLevel(player.chestplate, GeneratedEnchantments.ADVANCED_HEALING_MULTIPLIER)
        if (own <= 0) return
        // The upgrade also consumes its base, so its strength starts one level above the base cap.
        val level = UpgradeEnchantments.ladderLevelOf(player.chestplate, GeneratedEnchantments.ADVANCED_HEALING_MULTIPLIER)
        val before = e.amount
        // Bank at most two health bars, and only for ten seconds: the old code piled every hit's
        // share into absorption with a monotonic ceiling, so the golden hearts never drained.
        val cap = player.maxHealth * 2f
        val room = (cap - (banked[player.uuid] ?: 0f)).coerceAtLeast(0f)
        val heal = (0.01f * level * e.amount).coerceAtMost(room)
        if (heal > 0f) {
            grantAbsorption(player, absorptionHeadroomId("advanced_healing_multiplier"), heal, cap)
            banked[player.uuid] = (banked[player.uuid] ?: 0f) + heal
            expiry[player.uuid] = player.world.time + TTL_TICKS
        }
        FSTLog.log("AdvancedHealingMultiplierHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        val due = expiry[player.uuid] ?: return
        if (player.world.time < due) return
        expiry.remove(player.uuid)
        val mine = banked.remove(player.uuid) ?: 0f
        if (mine > 0f) {
            player.absorptionAmount = (player.absorptionAmount - mine).coerceAtLeast(0f)
            removeAbsorptionHeadroom(player, absorptionHeadroomId("advanced_healing_multiplier"))
            FSTLog.log("AdvancedHealingMultiplierHandler") { "player=${player.name.string}, released=$mine, outcome=expired" }
        }
    }
}