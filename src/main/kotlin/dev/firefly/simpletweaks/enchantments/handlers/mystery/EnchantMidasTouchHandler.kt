package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.BlockEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ThreadLocalRandom

/**
 * 点金之手 — block drops and mining speed, multiplied.
 *
 * ## What it deliberately does **not** touch
 * The obvious "loot enchantment" would also boost mob drops and experience, but this mod already has both:
 * `scavenger` owns **mob** drops and `experience_stealer` owns experience. Adding a second owner to either
 * would make their numbers impossible to reason about (the same "two owners of one counter" mistake that
 * `AGENTS.md` §4.10 records for enchantment weights). So this one owns exactly one axis: **block drops**,
 * plus mining speed, which nothing except `momentum` touches (and that one is a ramp, not a multiplier).
 *
 * ## Why multiplicative with Fortune / Auto Smelt
 * `HarvestDropsEvent` runs **after** vanilla loot generation, so the list already contains Fortune's output;
 * multiplying the counts keeps Fortune's value intact. `auto_smelt` replaces each stack's item and clears
 * vanilla drops afterwards, so running this before it (lower `order`) means the smelted stacks are not
 * double-counted. That is also why the multiplier is applied to `count` rather than adding extra stacks:
 * extra stacks would let `auto_smelt` smelt copies it never generated.
 */
@ModEnchantment(
    id = "midas_touch",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.TOOL,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 90,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = EnchantSupportedItemTags.MINING,
    slots = [EnchantSlot.MAINHAND],
    order = 20,
)
object EnchantMidasTouchHandler : Listenable {

    private const val DROP_PER_LEVEL = 0.25f
    private const val SPEED_PER_LEVEL = 0.20f
    private const val XP_CHANCE_PER_LEVEL = 0.35f

    @SubscribeEvent
    fun onHarvestDrops(e: BlockEvent.HarvestDropsEvent) {
        val player = e.harvester ?: return
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.MIDAS_TOUCH)
        if (level <= 0) return
        if (e.drops.isEmpty()) return

        val multiplier = 1f + DROP_PER_LEVEL * level
        var before = 0
        var after = 0
        for (stack in e.drops) {
            before += stack.count
            stack.count = (stack.count * multiplier).toInt().coerceAtLeast(stack.count)
            after += stack.count
        }

        // A little experience on top, so mining with it still feels different from plain Fortune.
        val world = e.world
        if (world is ServerWorld) {
            val chance = XP_CHANCE_PER_LEVEL * level
            if (ThreadLocalRandom.current().nextFloat() < chance) {
                ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(e.pos), 1 + level)
            }
        }

        FSTLog.log("MidasTouch") {
            "block=${e.state.block}, level=$level, multiplier=${"%.2f".format(multiplier)}, " +
                "count=$before->$after, outcome=multiplied"
        }
    }

    @SubscribeEvent
    fun onBreakSpeed(e: PlayerEvent.BreakSpeed) {
        val player = e.player
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.MIDAS_TOUCH)
        if (level <= 0) return

        val before = e.newSpeed
        e.newSpeed *= (1f + SPEED_PER_LEVEL * level)
        FSTLog.log("MidasTouch") {
            "level=$level, speed=${"%.3f".format(before)}->${"%.3f".format(e.newSpeed)}, outcome=haste"
        }
    }
}
