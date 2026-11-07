package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import java.util.*

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantVitalityHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                     | 1.21.1                                                                |
 * |------------------------------------------------------------|-----------------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`                           | `compat.event.TickEvent`                                              |
 * | `net.minecraftforge...PlayerEvent` (entity) + `...gameevent.PlayerEvent` | `compat.event.PlayerEvent` (both merged)                  |
 * | `p.world.isRemote`                                         | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)                  |
 * | `p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)` | `p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)` (⚠️ 1.21.1 still uses the `GENERIC_` prefix) |
 * | `IModifier.amount`                                         | `p.attributes.getModifierValue(EntityAttributes.GENERIC_MAX_HEALTH, MODIFIER_ID)` (`AttributeContainer.getModifierValue`) |
 * | `applyModifier(AttributeModifier(uuid, "Vitality", x, 0))` | `addPersistentModifier(EntityAttributeModifier(id, x, Operation.ADD_VALUE))` |
 * | `EntityEquipmentSlot.entries` + `slotType == Type.ARMOR`    | `EquipmentSlot.entries` + `slot.type == EquipmentSlot.Type.HUMANOID_ARMOR` (1.21 renamed the slot-type constant; `ANIMAL_ARMOR` is the new non-humanoid one) |
 * | `p.getItemStackFromSlot(slot)`                             | `p.getEquippedStack(slot)` (method_6118)                              |
 * | `p.syncAttributes()`                                       | `util/PlayerUtils.kt` extension (now `EntityAttributesS2CPacket`)       |
 * | `EnchantVitality` (Enchantment)                            | `ModEnchantmentKeys.VITALITY` (RegistryKey)                            |
 *
 * ⚠️ Two forced mappings (same shapes as `EnchantExtraArmorHandler`): a UUID-keyed modifier plus its
 * `"Vitality"` display name collapse into the single `Identifier` `simple_tweaks:vitality`, and
 * Forge's `applyModifier` becomes `addPersistentModifier` (1.12.2's `applyModifier` was serialised
 * with the entity; `addTemporaryModifier` would silently lose the bonus on reload).
 *
 * The `4t + 10⌊t/10⌋` stair-step and the `×(1 + 0.01t)` second-order term, including the 1.12.2
 * quirk that the "unchanged?" comparison uses `bonus` while the stored amount is `bonus + percentage`
 * (which makes the modifier get re-applied every tick), are preserved verbatim.
 */
object EnchantVitalityHandler : Listenable {

    private val MODIFIER_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "vitality")

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val attr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) ?: return
        val total = totalLevel(p)
        val hasModifier = attr.getModifier(MODIFIER_ID) != null

        if (total > 0 && hasModifier) {
            val existingAmount = p.attributes.getModifierValue(EntityAttributes.GENERIC_MAX_HEALTH, MODIFIER_ID)
            val expected = total * 4.0 + (total / 10) * 10.0
            if (existingAmount == expected) return
        }
        if (total <= 0 && !hasModifier) return

        attr.removeModifier(MODIFIER_ID)
        if (total > 0) {
            val bonus = total * 4.0 + (total / 10) * 10.0
            val percentage = bonus * 0.01 * total
            attr.addPersistentModifier(EntityAttributeModifier(MODIFIER_ID, bonus + percentage, EntityAttributeModifier.Operation.ADD_VALUE))
            STLog.log("Vitality") {
                "player=${p.name.string}, totalLvl=$total, hadModifier=$hasModifier, bonus=$bonus, " +
                    "percentage=$percentage, modifier=${bonus + percentage}, outcome=applied"
            }
        } else {
            STLog.log("Vitality") {
                "player=${p.name.string}, totalLvl=$total, hadModifier=$hasModifier, outcome=removed"
            }
        }
        p.syncAttributes()

        if (p.health > p.maxHealth) p.health = p.maxHealth
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        clearModifier(event.original)
        clearModifier(event.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        clearModifier(e.player)
    }

    private fun clearModifier(p: PlayerEntity) {
        // 1.21 annotates `getAttributeInstance` as @Nullable (1.12.2's getEntityAttribute was not);
        // `?.` only avoids an NPE that could not happen for max health anyway.
        p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.removeModifier(MODIFIER_ID)
    }

    private fun totalLevel(p: PlayerEntity): Int {
        var total = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            total += getItemSpecificEnchantLevel(p.getEquippedStack(slot), ModEnchantmentKeys.VITALITY)
        }
        return total
    }
}
