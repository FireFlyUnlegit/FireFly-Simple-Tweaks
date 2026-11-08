package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 疾行 — movement speed while the boots are worn.
 *
 * ## Why an attribute modifier and not a velocity push
 * Movement speed already has an attribute (`generic.movement_speed`), and vanilla computes walking
 * speed from it every tick. A per-tick velocity nudge would fight the client's own movement
 * prediction (rubber-banding); the modifier is the only lever the server owns cleanly.
 *
 * ## `ADD_MULTIPLIED_BASE` + a fixed id
 * The same reasoning as `MobLevels`' stat modifiers: multiplying the **base** makes the bonus a stable
 * fraction instead of compounding with every other speed source, and one fixed id makes re-application
 * an overwrite rather than a stack.
 *
 * ## The tick handler is idempotent by value, not by flag
 * `build=cleanup63` showed the cost of getting this wrong: comparing a stored value against the
 * *requested* value can never be equal when the modifier carries a derived term, so the modifier was
 * removed and re-added (with a full attribute sync) every tick. Here the stored magnitude is compared
 * against the expected one, so a steady state does nothing at all.
 *
 * ⚠️ `saved` mapping (`AGENTS.md` §4.2): **persistent**, because what keeps the bonus alive is the
 * equipped item — persistent state — not an in-memory timer. A reload keeps the modifier, and the next
 * tick removes it if the boots are gone.
 */
@ModEnchantment(
    id = "fleetfoot",
    category = EnchantCategory.COMMON,
    type = EnchantType.BOOTS,
    maxLevel = 3,
    anvilCost = 4,
    minCostBase = 22,
    supportedItems = EnchantSupportedItemTags.FOOT_ARMOR,
    slots = [EnchantSlot.FEET],
)
object EnchantFleetfootHandler : Listenable {

    /** Multiplier added to base movement speed per level. III = +18 %. */
    const val SPEED_PER_LEVEL = 0.06f

    private val MODIFIER_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "fleetfoot")

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val level = getItemSpecificEnchantLevel(
            player.getEquippedStack(EquipmentSlot.FEET),
            GeneratedEnchantments.FLEETFOOT,
        )
        val attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) ?: return
        val expected = if (level <= 0) 0.0 else level * SPEED_PER_LEVEL.toDouble()
        val current = attr.getModifier(MODIFIER_ID)?.value

        if (expected == 0.0) {
            if (current == null) return
            attr.removeModifier(MODIFIER_ID)
            player.syncAttributes()
            FSTLog.log("Fleetfoot") {
                "player=${player.name.string}, lvl=0, outcome=removed"
            }
            return
        }
        if (current != null && current == expected) return

        attr.removeModifier(MODIFIER_ID)
        attr.addPersistentModifier(
            EntityAttributeModifier(MODIFIER_ID, expected, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        )
        player.syncAttributes()
        FSTLog.log("Fleetfoot") {
            "player=${player.name.string}, lvl=$level, modifier=$expected, outcome=applied"
        }
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        clear(e.original)
        clear(e.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) = clear(e.player)

    private fun clear(player: PlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
    }
}
