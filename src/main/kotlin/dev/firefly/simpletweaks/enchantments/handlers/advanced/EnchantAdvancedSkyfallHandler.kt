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
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_skyfall",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 4,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    anvilCost = 16,
    minCostBase = 40,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 93,
)
object EnchantAdvancedSkyfallHandler : Listenable {

    private const val FALL_CAP = 6f

    /** Below this the player is simply standing; vanilla `onGround` alone missed every jump-hit. */
    private const val MIN_FALL = 0.5f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = UpgradeEnchantments.ladderLevelOf(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_SKYFALL)
        if (level <= 0) return

        // Gravity is the weapon: the longer the drop, the harder the landing (capped so a sky-limit
        // jump does not turn into an instant kill).
        val drop = player.fallDistance.coerceIn(0f, FALL_CAP)
        if (drop < MIN_FALL && player.isOnGround) return
        val before = e.amount
        e.amount *= 1f + 0.06f * level * (1f + drop)

        val world = player.world
        if (world is ServerWorld) world.spawnParticles(ParticleTypes.CLOUD, victim.x, victim.y + 0.2, victim.z, 16, 0.35, 0.1, 0.35, 0.05)
        if (world is ServerWorld) world.spawnParticles(ParticleTypes.CRIT, victim.x, victim.y + 0.6, victim.z, 10, 0.3, 0.3, 0.3, 0.2)
        world.playSound(null, victim.blockPos, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.PLAYERS, 0.6f, 1.1f)
        FSTLog.log("AdvancedSkyfallHandler") {
            "player=${player.name.string}, level=$level, drop=$drop, damage=$before->${e.amount}, outcome=plunged"
        }
    }
}
