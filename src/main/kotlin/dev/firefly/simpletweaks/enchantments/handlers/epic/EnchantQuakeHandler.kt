package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items

/**
 * 震心 — a mace hit **interrupts** whatever the target was doing and locks its items for a moment.
 *
 * ## Why this is not another damage enchantment
 * The mace is slow; its real cost is that you eat a full swing of enemy actions while winding up. Making
 * the hit cancel a charge (bow draw, shield raise, potion drink, food, trident throw) is the payoff that
 * suits a heavy weapon: you traded speed for the ability to stop **their** action.
 *
 * ## Mechanics
 * If the victim is using an item, the active stack is cleared and that stack gets a
 * `20 tick`-long cooldown (vanilla's `ItemCooldownManager` is synced for the holder, so the player sees the
 * greyed-out slot too — `AGENTS.md` §4.11 is about state that is *not* synced, this one is).
 *
 * ## Guard
 * Only maces, and only on a real target (`attacker !== target`).
 */
@ModEnchantment(
    id = "quake",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = "#minecraft:enchantable/mace",
    slots = [EnchantSlot.MAINHAND],
)
object EnchantQuakeHandler : Listenable {

    /** Cooldown applied to the interrupted item, in ticks (1 s). */
    private const val LOCK_TICKS = 20

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        val weapon = attacker.combatStack(e.source)
        if (weapon.item != Items.MACE) return

        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.QUAKE)
        if (level <= 0) return

        if (!target.isUsingItem) return
        val using = target.activeItem
        if (using.isEmpty) return

        target.clearActiveItem()
        // `ItemCooldownManager` lives on PlayerEntity (mobs do not track item cooldowns), and it is synced
        // to its holder, so the greyed-out slot is visible to the victim — unlike the §4.11 cases.
        (target as? PlayerEntity)?.itemCooldownManager?.set(using.item, LOCK_TICKS)

        FSTLog.log("Quake") {
            "target=${target.type.name.string}, level=$level, interrupted=${using.item}, " +
                "lockTicks=$LOCK_TICKS, outcome=interrupted"
        }
    }
}
