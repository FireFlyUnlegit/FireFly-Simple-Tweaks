package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import java.util.concurrent.ThreadLocalRandom
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
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.elements.ElementMarks
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_heavenly_punishment",
    category = EnchantCategory.ADVANCED_MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    ladderBase = 4,
    weight = EnchantItemWeights.ADVANCED_MYSTERY,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 99,
)
object EnchantAdvancedHeavenlyPunishmentHandler : Listenable {

    private val PARTNERS = arrayOf(ElementType.WATER, ElementType.FROST, ElementType.NATURE)

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = UpgradeEnchantments.ladderLevelOf(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_HEAVENLY_PUNISHMENT)
        if (level <= 0) return
        val before = e.amount
        val bonus = before * 0.04f * level
        e.amount += bonus
        // The storm picks *which* reaction happens, never *whether*: lightning is always the trigger and
        // the partner is drawn per hit. The partner is only a carrier for the reaction, so the victim's
        // own aura is put back afterwards -- no random mark is left behind.
        if (bonus > 0f) {
            val previous = ElementAuras.peek(victim)
            val partner = PARTNERS[ThreadLocalRandom.current().nextInt(PARTNERS.size)]
            ElementAuras.attach(victim, partner, ElementAuras.ULTRA)
            val outcome = ElementMarks.recordAndReact(
                player, victim, ElementType.LIGHTNING, bonus,
                units = ElementAuras.ULTRA, forceAttach = true, source = e.source,
            )
            if (previous != null) ElementAuras.attach(victim, previous.first, previous.second) else ElementAuras.clear(victim)
            FSTLog.log("AdvancedHeavenlyPunishmentHandler") {
                "player=${player.name.string}, level=$level, partner=$partner, reaction=${outcome.key}, outcome=forced-reaction"
            }
        }
        victim.addStatusEffect(StatusEffectInstance(StatusEffects.GLOWING, 100, 0))
        FSTLog.log("AdvancedHeavenlyPunishmentHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }
}