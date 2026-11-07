package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.mobs.MobLevelSync
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import java.util.concurrent.ThreadLocalRandom

/**
 * 战利品 — killing a **levelled** mob can drop extra vanilla materials, scaled by the mob's level.
 *
 * ## Why levelled mobs need a reward
 * `MobLevels` makes every mob tougher with level, and `build=cleanup24` made high-level mobs actually
 * spawn in the world. The only payoff used to be experience, so fighting a Lv40 mob was strictly worse than
 * farming Lv5 ones. This gives the level a material value.
 *
 * ## Deliberate limits
 * - **Vanilla items only.** Mob gear is stripped on death (`build=cleanup24`), and this must not become a
 *   side channel that leaks modded enchantments back to players — so the table never draws from mob
 *   equipment, only from `Items`.
 * - The drop is a **stack dropped at the mob's position**, not added to the killer's inventory, so
 *   `PlayerDropsEvent` / soulbound logic is untouched.
 * - Chance is capped, so a Lv100 mob cannot be a slot machine.
 */
@ModEnchantment(
    id = "scavenger",
    category = EnchantCategory.RARE,
    type = EnchantType.TOOL,
    maxLevel = 3,
    anvilCost = 5,
    minCostBase = 18,
    supportedItems = EnchantSupportedItemTags.MINING,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantScavengerHandler : Listenable {

    private const val CHANCE_PER_LEVEL = 0.10f
    private const val CHANCE_PER_MOB_LEVEL = 0.01f
    private const val CHANCE_CAP = 0.75f

    @SubscribeEvent
    fun onLivingDeath(e: LivingDeathEvent) {
        val mob = e.entityLiving as? MobEntity ?: return
        if (mob.world.isClient) return

        val killer = e.source.attacker as? PlayerEntity ?: return
        val level = getItemSpecificEnchantLevel(killer.mainHandStack, GeneratedEnchantments.SCAVENGER)
        if (level <= 0) return

        val mobLevel = MobLevelSync.levelOf(mob)
        if (mobLevel <= 0) return

        val chance = (CHANCE_PER_LEVEL * level + CHANCE_PER_MOB_LEVEL * mobLevel).coerceAtMost(CHANCE_CAP)
        if (ThreadLocalRandom.current().nextFloat() > chance) return

        val drop = pickDrop(mobLevel)
        mob.dropStack(drop)
        FSTLog.log("Scavenger") {
            "mob=${mob.type.name.string}, mobLevel=$mobLevel, chance=${"%.2f".format(chance)}, " +
                "drop=${drop.item}, count=${drop.count}, outcome=dropped"
        }
    }

    /** Tiered table: the higher the mob level, the more valuable the material. */
    private fun pickDrop(mobLevel: Int): ItemStack {
        val random = ThreadLocalRandom.current()
        return when {
            mobLevel < 20 -> ItemStack(Items.IRON_NUGGET, 1 + random.nextInt(3))
            mobLevel < 40 -> ItemStack(Items.GOLD_NUGGET, 1 + random.nextInt(3))
            mobLevel < 60 -> ItemStack(Items.IRON_INGOT, 1)
            else -> ItemStack(Items.EMERALD, 1)
        }
    }
}
