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
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 迅捷 (deft) advanced form — attack speed, plus the knockback resistance deft never had.
 *
 * `base` is deft's own maximum (`3 x 8 %`), so an advanced I is already better than any deft III, and
 * the ladder keeps growing from there.
 */
@ModEnchantment(
    id = "advanced_deft",
    category = EnchantCategory.ADVANCED_COMMON,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_COMMON,
    anvilCost = 6,
    minCostBase = 30,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 70,
)
object EnchantAdvancedDeftHandler : Listenable {

    /** Attack speed deft III already gives. */
    const val BASE_ATTACK_SPEED = 0.24f

    const val ATTACK_SPEED_PER_LEVEL = 0.10f

    /** New rider: `+5 %` knockback resistance per level (`ADD_VALUE`, the attribute is `0..1`). */
    const val KNOCKBACK_PER_LEVEL = 0.05

    private val SPEED_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "advanced_deft_speed")
    private val KNOCKBACK_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "advanced_deft_knockback")

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.ADVANCED_DEFT)
        val expectedSpeed =
            if (level <= 0) 0.0 else (BASE_ATTACK_SPEED + level * ATTACK_SPEED_PER_LEVEL).toDouble()
        val expectedKnockback = if (level <= 0) 0.0 else level * KNOCKBACK_PER_LEVEL

        val changed = apply(
            player,
            EntityAttributes.GENERIC_ATTACK_SPEED,
            SPEED_ID,
            expectedSpeed,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
        )
        val knockbackChanged = apply(
            player,
            EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
            KNOCKBACK_ID,
            expectedKnockback,
            EntityAttributeModifier.Operation.ADD_VALUE,
        )
        if (!changed && !knockbackChanged) return

        player.syncAttributes()
        FSTLog.log("AdvancedDeft") {
            "player=${player.name.string}, lvl=$level, speed=$expectedSpeed, " +
                "knockback=$expectedKnockback, outcome=applied"
        }
    }

    /** Returns whether the modifier was actually replaced, so the tick can skip the resync. */
    private fun apply(
        player: PlayerEntity,
        attribute: RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>,
        id: Identifier,
        value: Double,
        operation: EntityAttributeModifier.Operation,
    ): Boolean {
        val instance = player.getAttributeInstance(attribute) ?: return false
        val current = instance.getModifier(id)
        if (value <= 0.0) {
            if (current == null) return false
            instance.removeModifier(id)
            return true
        }
        if (current != null && current.value == value && current.operation == operation) return false
        instance.removeModifier(id)
        instance.addPersistentModifier(EntityAttributeModifier(id, value, operation))
        return true
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        clear(e.original)
        clear(e.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) = clear(e.player)

    private fun clear(player: PlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)?.removeModifier(SPEED_ID)
        player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)
            ?.removeModifier(KNOCKBACK_ID)
    }
}
