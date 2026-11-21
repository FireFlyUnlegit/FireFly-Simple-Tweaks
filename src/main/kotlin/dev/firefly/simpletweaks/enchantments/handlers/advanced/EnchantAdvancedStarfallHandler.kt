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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_starfall",
    category = EnchantCategory.ADVANCED_MYTHIC,
    type = EnchantType.BOW,
    maxLevel = 5,
    ladderBase = 5,
    weight = EnchantItemWeights.ADVANCED_MYTHIC,
    damagePerLevel = 0.0,
    anvilCost = 16,
    minCostBase = 40,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND],
    order = 98,
)
object EnchantAdvancedStarfallHandler : Listenable {

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_STARFALL)
        if (level <= 0) return

        // Every arrow nails a star to the target; a later arrow on a lit target makes it burst. Anything
        // else on the target is left alone — overwriting a foreign aura silently broke other setups.
        val aura = ElementAuras.peek(victim)
        val lit = aura?.first == ElementType.LIGHT
        if (!lit && aura != null) return
        if (!lit) {
            ElementAuras.attach(victim, ElementType.LIGHT, ElementAuras.STRONG)
            val world = victim.world
            if (world is ServerWorld) world.spawnParticles(ParticleTypes.END_ROD, victim.x, victim.y + 0.6, victim.z, 12, 0.25, 0.3, 0.25, 0.02)
            world.playSound(null, victim.blockPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.6f)
            FSTLog.log("AdvancedStarfallHandler") {
                "player=${player.name.string}, level=$level, outcome=marked"
            }
            return
        }

        val burst = e.amount * 0.05f * level
        val landed = victim.damageElemental(player, ModDamageTypes.STAR, burst)
        val world = victim.world
        if (world is ServerWorld) world.spawnParticles(ParticleTypes.FIREWORK, victim.x, victim.y + 0.5, victim.z, 24, 0.35, 0.45, 0.35, 0.12)
        world.playSound(null, victim.blockPos, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.7f, 1.2f)
        FSTLog.log("AdvancedStarfallHandler") {
            "player=${player.name.string}, level=$level, burst=$burst, landed=$landed, outcome=burst"
        }
    }
}
