package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 重装护甲 — the advanced step above `extra_armor`, which it replaces at level VIII.
 *
 * A piece carrying `greater_armor I` counts as nine levels on the ladder (the eight of the Extra Armor
 * it consumed plus its own one). Both modifiers are persistent, like every other armour enchantment.
 */
@ModEnchantment(
    id = "greater_armor",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 8,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    anvilCost = 10,
    minCostBase = 25,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 66,
)
object EnchantGreaterArmorHandler : Listenable {

    private val ARMOR_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "greater_armor_armor")
    private val TOUGHNESS_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "greater_armor_toughness")
    private val RULE = UpgradeEnchantments.rules().first { it.upgrade == GeneratedEnchantments.GREATER_ARMOR }

    /** Armour [total] ladder levels are worth, before the second-order term. */
    @JvmStatic
    fun armorFor(total: Int): Double = total * 3.5

    /** Toughness [total] ladder levels are worth, before the second-order term. */
    @JvmStatic
    fun toughnessFor(total: Int): Double = total * 2.8

    /** The `×(1 + 0.015t)` term. */
    @JvmStatic
    fun scaleFor(total: Int): Double = 1.0 + 0.015 * total

    @JvmStatic
    fun armorValue(total: Int): Double = armorFor(total) * scaleFor(total)

    @JvmStatic
    fun toughnessValue(total: Int): Double = toughnessFor(total) * scaleFor(total)

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val armorAttr = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR) ?: return
        val toughnessAttr = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS) ?: return

        val total = ladderTotal(p)
        val hasModifier = armorAttr.getModifier(ARMOR_ID) != null

        if (total <= 0 && !hasModifier) return
        if (total > 0 && hasModifier && armorAttr.getModifier(ARMOR_ID)?.value == armorValue(total)) return

        armorAttr.removeModifier(ARMOR_ID)
        toughnessAttr.removeModifier(TOUGHNESS_ID)
        if (total > 0) {
            armorAttr.addPersistentModifier(
                EntityAttributeModifier(ARMOR_ID, armorValue(total), EntityAttributeModifier.Operation.ADD_VALUE)
            )
            toughnessAttr.addPersistentModifier(
                EntityAttributeModifier(TOUGHNESS_ID, toughnessValue(total), EntityAttributeModifier.Operation.ADD_VALUE)
            )
        }
        p.syncAttributes()
        FSTLog.log("GreaterArmor") {
            "player=${p.name.string}, ladderLvl=$total, armor=${armorValue(total)}, " +
                "toughness=${toughnessValue(total)}, outcome=${if (total > 0) "applied" else "removed"}"
        }
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        clearModifiers(event.original)
        clearModifiers(event.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        clearModifiers(e.player)
    }

    private fun clearModifiers(p: PlayerEntity) {
        p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR)?.removeModifier(ARMOR_ID)
        p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)?.removeModifier(TOUGHNESS_ID)
    }

    private fun ladderTotal(p: PlayerEntity): Int {
        var total = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            total += UpgradeEnchantments.ladderLevel(p.getEquippedStack(slot), RULE)
        }
        return total
    }
}
