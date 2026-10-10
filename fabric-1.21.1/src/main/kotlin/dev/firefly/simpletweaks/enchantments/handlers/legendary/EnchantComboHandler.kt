package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import java.util.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantComboHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                      | 1.21.1                                                                 |
 * |-------------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`                      | `compat.event.LivingHurtEvent`                                          |
 * | `net.minecraftforge...PlayerEvent` (entity) + `...gameevent.PlayerEvent` | `compat.event.PlayerEvent` (both merged; the alias is gone) |
 * | `net.minecraftforge...TickEvent`                            | `compat.event.TickEvent`                                                |
 * | `net.minecraftforge...EventPriority`                        | `compat.event.EventPriority` (the annotation keeps `priority`)           |
 * | `e.attacker` / `e.target` / `e.invalid`                     | same extension names, now `compat.attacker` / `compat.target` / `compat.invalid` |
 * | `attacker.heldItemMainhand`                                 | `attacker.mainHandStack` (`method_6047`)                                |
 * | `target.hurtResistantTime` (write)                          | `target.timeUntilRegen` (`Entity`'s public field `field_6019`)           |
 * | `p.world.isRemote`                                          | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)                    |
 * | `p.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)`| `p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)` (`method_6093`, nullable) |
 * | `AttributeModifier(uuid, "Combo Attack Speed", x, 2)`       | `EntityAttributeModifier(id, x, Operation.ADD_MULTIPLIED_TOTAL)` (1.21 operation 2 = `ADD_MULTIPLIED_TOTAL`) |
 * | `attr.applyModifier(...)`                                   | `attr.addPersistentModifier(...)` (1.12.2's `applyModifier` was serialised with the entity; the temporary variant would be lost on reload) |
 * | `p.connection.sendPacket(SPacketEntityProperties(...))`     | `p.syncAttributes()` (`util/PlayerUtils.kt`, now `EntityAttributesS2CPacket`) |
 * | `EnchantCombo` (Enchantment object)                         | `GeneratedEnchantments.COMBO` (RegistryKey)                                |
 *
 * ⚠️ Two forced mappings, both inherited from the project-wide attribute-modifier pattern used by
 * `EnchantExtraArmorHandler` / `EnchantVitalityHandler`: the UUID (from
 * `UUID.nameUUIDFromBytes("simple_tweaks_combo_attack_speed")`) plus its `"Combo Attack Speed"`
 * display name collapse into the single `Identifier` `simple_tweaks:combo_attack_speed` (1.21 has
 * nowhere to keep the display name), and `applyModifier` becomes `addPersistentModifier`.
 *
 * ⚠️ **Flagged for the batch report — the attack-speed half is now duplicated in code.**
 * The phase-2.5 analysis (`docs/phase2.5-tiers-legendary-mystery-unique.md`, "PARTIAL") decided that
 * the `+0.35 x L` attack-speed bonus should be expressed declaratively in the combo enchantment JSON
 * via `minecraft:attributes` (`minecraft:generic.attack_speed`, `add_multiplied_total`, 0.35 per
 * level, `mainhand` slot), leaving only the i-frame compression as code. The generated JSON
 * (`data/simple_tweaks/enchantment/combo.json`) currently contains **only** the `minecraft:damage`
 * entry — the `minecraft:attributes` block was never written. This handler is therefore ported 1:1
 * (both halves, as instructed): the tick half below is the only thing granting the attack speed
 * today. If the JSON is ever given that `minecraft:attributes` block, the bonus will be applied
 * twice and this tick half must be deleted.
 */
@ModEnchantment(
    id = "combo",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.SWORD,
    maxLevel = 10,
    weight = 2,
    anvilCost = 10,
    minCostBase = 20,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.15,
    order = 32,
)
object EnchantComboHandler : Listenable {

    private const val ATTACK_SPEED_PER_LEVEL = 0.35

    private val ATTACK_SPEED_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "combo_attack_speed")
    private val appliedLevel = WeakHashMap<PlayerEntity, Int>()

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val target = e.target

        val level = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.COMBO)
        if (level <= 0) return

        val multiplier = (1f - level * 0.1f).coerceAtLeast(0f)
        target.timeUntilRegen =
            (target.timeUntilRegen * multiplier).toInt().coerceAtLeast(0)
        STLog.log("Combo") {
            "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$level, " +
                "multiplier=$multiplier, timeUntilRegen=${target.timeUntilRegen}"
        }
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val attr = p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED) ?: return
        val level = getItemSpecificEnchantLevel(p.mainHandStack, GeneratedEnchantments.COMBO)
        val hasModifier = attr.getModifier(ATTACK_SPEED_ID) != null

        if (level <= 0 && !hasModifier) {
            appliedLevel.remove(p)
            return
        }
        if (level > 0 && hasModifier && appliedLevel[p] == level) return

        attr.removeModifier(ATTACK_SPEED_ID)
        if (level > 0) {
            // 1.12.2 operation 2 == 1.21 `ADD_MULTIPLIED_TOTAL`; the "only while held" semantics that
            // 1.12.2 got from re-checking the main hand every tick are covered by the JSON's
            // `mainhand` slot once that JSON lands (see the class KDoc).
            attr.addPersistentModifier(
                EntityAttributeModifier(
                    ATTACK_SPEED_ID,
                    ATTACK_SPEED_PER_LEVEL * level,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                )
            )
            appliedLevel[p] = level
        } else {
            appliedLevel.remove(p)
        }

        p.syncAttributes()
        STLog.log("Combo") {
            "player=${p.name.string}, lvl=$level, hadModifier=$hasModifier, " +
                "modifier=${ATTACK_SPEED_PER_LEVEL * level}, outcome=${if (level > 0) "applied" else "removed"}"
        }
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        clearModifier(event.original)
        clearModifier(event.player)
        appliedLevel.remove(event.original)
        appliedLevel.remove(event.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        clearModifier(e.player)
        appliedLevel.remove(e.player)
    }

    private fun clearModifier(p: PlayerEntity) {
        // 1.21 annotates `getAttributeInstance` as @Nullable (1.12.2's getEntityAttribute was not);
        // `?.` only avoids an NPE that could not happen for attack speed anyway.
        p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)?.removeModifier(ATTACK_SPEED_ID)
    }
}
