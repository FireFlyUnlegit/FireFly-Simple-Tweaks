package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.util.Hand

/**
 * 碎盾 — a mace ignores a raised shield, and hits the shield itself harder than anything else can.
 *
 * ## The rule this breaks
 * Vanilla gives exactly one answer to a shield: an axe disables it. A mace is the slowest, heaviest weapon
 * in the game, so "being blocked" hits it hardest — the fantasy of the enchantment is that a two-handed
 * slam simply goes **through** the shield instead of being turned aside.
 *
 * ## Mechanics
 * On a hit where the victim is blocking, the block is dropped (`clearActiveItem`) **before** the damage
 * is applied by the caller, and the shield takes `4 × level` extra durability. The victim still keeps the
 * shield, so the counter-play is "do not stand still against a mace", not "your item is deleted".
 *
 * ## Why `activeItem` is read first
 * `clearActiveItem()` empties the active stack slot; the durability hit must be applied to the stack that
 * was captured **before** clearing, which is also the trap `shield_breaker` documents.
 */
@ModEnchantment(
    id = "sunder",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = "#minecraft:enchantable/mace",
    slots = [EnchantSlot.MAINHAND],
)
object EnchantSunderHandler : Listenable {

    private const val DURABILITY_PER_LEVEL = 4

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return
        if (!target.isBlocking) return

        val weapon = attacker.combatStack(e.source)
        if (weapon.item != Items.MACE) return

        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.SUNDER)
        if (level <= 0) return

        val shield = target.activeItem
        if (shield.isEmpty) return

        val slot = if (target.activeHand == Hand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND
        target.clearActiveItem()
        shield.damage(DURABILITY_PER_LEVEL * level, target, slot)

        FSTLog.log("Sunder") {
            "target=${target.type.name.string}, level=$level, shield=${shield.item}, " +
                "durability=${shield.maxDamage - shield.damage}, outcome=sundered"
        }
    }
}
