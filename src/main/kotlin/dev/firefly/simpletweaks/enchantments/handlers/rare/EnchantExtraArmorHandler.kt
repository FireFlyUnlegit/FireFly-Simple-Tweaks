package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import java.util.*

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantExtraArmorHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                     | 1.21.1                                                            |
 * |------------------------------------------------------------|-------------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`                           | `compat.event.TickEvent`                                          |
 * | `p.world.isRemote`                                         | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)              |
 * | `p.getEntityAttribute(SharedMonsterAttributes.ARMOR)`      | `p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR)` (method_6093, returns nullable) |
 * | `IAttributeInstance.getModifier(UUID)`                     | `EntityAttributeInstance.getModifier(Identifier)`                 |
 * | `applyModifier(AttributeModifier(uuid, name, amount, 0))`  | `addPersistentModifier(EntityAttributeModifier(id, amount, Operation.ADD_VALUE))` |
 * | `removeModifier(UUID)`                                     | `removeModifier(Identifier)`                                      |
 * | `net.minecraftforge...PlayerEvent.Clone.isWasDeath`        | `compat.event.PlayerEvent.Clone.wasDeath`                         |
 * | `Clone.entityPlayer` (the NEW player)                      | `Clone.player` (the port keeps Forge's direction: player = new, original = old) |
 * | `p.syncAttributes()`                                       | `util/PlayerUtils.kt` extension (now `EntityAttributesS2CPacket`)  |
 * | `EnchantExtraArmor` (Enchantment object)                   | `ModEnchantmentKeys.EXTRA_ARMOR` (RegistryKey)                     |
 *
 * ⚠️ **Two forced mappings**, both caused by 1.21 removing UUID-keyed attribute modifiers:
 *  1. `UUID.nameUUIDFromBytes(...)` + the human-readable name `"Extra Armor"` collapse into a single
 *     `Identifier` (`simple_tweaks:extra_armor_armor` / `simple_tweaks:extra_armor_toughness`). The
 *     identity of the modifier is preserved (same two stable keys), the display name is gone because
 *     1.21 has nowhere to put it.
 *  2. Forge's `applyModifier` is mapped to `addPersistentModifier`. 1.21 splits the old call into
 *     `addTemporaryModifier` (not written to NBT, e.g. potion effects) and `addPersistentModifier`
 *     (written to NBT). 1.12.2's `applyModifier` *was* serialised with the entity, so the persistent
 *     variant is the faithful one; using the temporary one would silently drop the bonus on reload.
 */
object EnchantExtraArmorHandler : Listenable {

    private val ARMOR_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "extra_armor_armor")
    private val TOUGHNESS_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "extra_armor_toughness")

    private val appliedLevel = WeakHashMap<PlayerEntity, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val armorAttr = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR) ?: return
        val toughnessAttr = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS) ?: return

        val total = p.getArmorEnchantLevel(ModEnchantmentKeys.EXTRA_ARMOR)
        val hasModifier = armorAttr.getModifier(ARMOR_ID) != null

        if (total <= 0 && !hasModifier) {
            appliedLevel.remove(p)
            return
        }
        if (total > 0 && hasModifier && appliedLevel[p] == total) return

        armorAttr.removeModifier(ARMOR_ID)
        toughnessAttr.removeModifier(TOUGHNESS_ID)
        val armor = total * 2.0
        val armorToughness = total * 1.6
        val percentageArmor = armor * 0.01 * total
        val percentageToughness = armorToughness * 0.01 * total

        if (total > 0) {
            armorAttr.addPersistentModifier(
                EntityAttributeModifier(ARMOR_ID, percentageArmor + armor, EntityAttributeModifier.Operation.ADD_VALUE)
            )
            toughnessAttr.addPersistentModifier(
                EntityAttributeModifier(TOUGHNESS_ID, percentageToughness + armorToughness, EntityAttributeModifier.Operation.ADD_VALUE)
            )
            appliedLevel[p] = total
        } else {
            appliedLevel.remove(p)
        }
        p.syncAttributes()
        STLog.log("ExtraArmor") {
            "player=${p.name.string}, totalLvl=$total, hadModifier=$hasModifier, " +
                "armor=$armor+$percentageArmor, toughness=$armorToughness+$percentageToughness, " +
                "outcome=${if (total > 0) "applied" else "removed"}"
        }
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (!event.wasDeath) return
        clearModifiers(event.original)
        clearModifiers(event.player)
        appliedLevel.remove(event.original)
        appliedLevel.remove(event.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        clearModifiers(e.player)
        appliedLevel.remove(e.player)
    }

    private fun clearModifiers(p: PlayerEntity) {
        // 1.21 annotates `getAttributeInstance` as @Nullable (1.12.2's getEntityAttribute was not);
        // `?.` only avoids an NPE that could not happen for these two vanilla attributes anyway.
        p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR)?.removeModifier(ARMOR_ID)
        p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)?.removeModifier(TOUGHNESS_ID)
    }
}
