package dev.firefly.simpletweaks.enchantments.handlers.legendary

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
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 重装护甲 — the legendary step above `extra_armor`, which is EPIC and capped at 8.
 *
 * `t` is the total level across the four armour slots: `3.5t` armour and `2.8t` toughness, both scaled by
 * `×(1 + 0.015t)`. At `t = 32` that is `+165.8` armour and `+132.6` toughness against Extra Armor's
 * `+84.5 / +67.6`. Both modifiers are persistent, like every other armour enchantment here, so the
 * bonus survives a reload.
 *
 * The fast path compares the values already on the entity rather than remembering the level in a map.
 */
@ModEnchantment(
    id = "greater_armor",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 8,
    weight = EnchantItemWeights.LEGENDARY,
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

    /** Armour this many levels is worth, before the second-order term. */
    @JvmStatic
    fun armorFor(total: Int): Double = total * 3.5

    /** Toughness this many levels is worth, before the second-order term. */
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

        val total = p.getArmorEnchantLevel(GeneratedEnchantments.GREATER_ARMOR)
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
            "player=${p.name.string}, totalLvl=$total, hadModifier=$hasModifier, " +
                "armor=${armorValue(total)}, toughness=${toughnessValue(total)}, " +
                "outcome=${if (total > 0) "applied" else "removed"}"
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
}
