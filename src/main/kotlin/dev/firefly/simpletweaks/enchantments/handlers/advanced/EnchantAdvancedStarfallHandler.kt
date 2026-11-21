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
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.UUID
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
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 98,
)
object EnchantAdvancedStarfallHandler : Listenable {

    private val hits = java.util.Collections.synchronizedMap(HashMap<UUID, Int>())


    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = UpgradeEnchantments.ladderLevelOf(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_STARFALL)
        if (level <= 0) return

        // Every arrow leaves a visible star on the target; the second consecutive arrow bursts it. We
        // count hits instead of reading the aura, because the arrow's own elemental conversion can
        // overwrite the LIGHT mark between shots (that was why nobody ever saw the burst).
        val world = victim.world
        if (world is ServerWorld) {
            world.spawnParticles(ParticleTypes.END_ROD, victim.x, victim.y + 0.8, victim.z, 10, 0.25, 0.3, 0.25, 0.02)
        }
        val previous = hits.getOrDefault(victim.uuid, 0)
        // A killing arrow bursts on the spot: most targets in this pack die to one shot, and a mark
        // nobody lives to see is the same as no effect at all.
        val lethal = victim.health - e.amount <= 0f
        if (previous <= 0 && !lethal) {
            if (ElementAuras.peek(victim) == null) ElementAuras.attach(victim, ElementType.LIGHT, ElementAuras.STRONG)
            hits[victim.uuid] = 1
            world.playSound(null, victim.blockPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.6f)
            FSTLog.log("AdvancedStarfallHandler") {
                "player=${player.name.string}, level=$level, outcome=marked"
            }
            return
        }

        hits.remove(victim.uuid)
        val burst = e.amount * 0.05f * level
        val landed = victim.damageElemental(player, ModDamageTypes.STAR, burst)
        if (world is ServerWorld) {
            world.spawnParticles(ParticleTypes.FIREWORK, victim.x, victim.y + 0.6, victim.z, 24, 0.35, 0.45, 0.35, 0.12)
        }
        world.playSound(null, victim.blockPos, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.7f, 1.2f)
        FSTLog.log("AdvancedStarfallHandler") {
            "player=${player.name.string}, level=$level, burst=$burst, landed=$landed, outcome=burst"
        }
    }
}
