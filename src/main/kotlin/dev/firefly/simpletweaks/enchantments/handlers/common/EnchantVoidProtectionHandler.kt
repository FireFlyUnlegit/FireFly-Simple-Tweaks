package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingFallEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.tp
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import java.util.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/common/EnchantVoidProtectionHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                         | 1.21.1                                                            |
 * |------------------------------------------------|-------------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`               | `compat.event.TickEvent`                                          |
 * | `net.minecraftforge...LivingFallEvent`         | `compat.event.LivingFallEvent`                                    |
 * | `p.inventory.armorInventory[0]`                | `p.getEquippedStack(EquipmentSlot.FEET)` (method_6118)            |
 * | `p.posY`                                       | `p.y` (`Entity.getY()`)                                           |
 * | `p.uniqueID`                                   | `p.uuid` (`EntityLike`, method_5667)                              |
 * | `p.onGround`                                   | `p.isOnGround` (`Entity.isOnGround()`, method_24828)              |
 * | `entity.world.isRemote`                        | `WorldSide.isClient(entity.world)`                               |
 * | `event.entity` (LivingEvent#getEntity)         | `event.entityLiving` (the port event's own field)                 |
 * | `TextComponentString("...")`                   | `Text.literal("...")`                                             |
 * | `p.sendMessage(Text)`                          | `p.sendMessage(Text, false)` — 1.21 `PlayerEntity` only declares the 2-arg `sendMessage(Text, boolean)` (method_7353); `false` = chat, not action bar |
 * | `EnchantVoidProtection` (Enchantment object)   | `GeneratedEnchantments.VOID_PROTECTION` (RegistryKey)                |
 *
 * ⚠️ **DEVIATION — please review by hand (the only one in this file).**
 * `EnchantmentHelper.getEnchantments(stack)` no longer returns a mutable `Map` and
 * `EnchantmentHelper.setEnchantments(map, stack)` does not exist in 1.21.1 at all (verified in the
 * cached mappings: `EnchantmentHelper` only has `getLevel`/`getEnchantments`). The enchantments now
 * live in the immutable `minecraft:enchantments` component, so the "drop one level" step is expressed
 * by rebuilding that component with `ItemEnchantmentsComponent.Builder` — the same single-level
 * decrement (and removal at level 1), only written differently. Note `Builder` has no
 * `remove(RegistryEntry)`; removal is `remove(Predicate)` (`method_57548`), hence the
 * `matchesKey` predicate (`method_40225`) instead of a map `remove`.
 *
 * `p.tp(y = 256.0)` is the `util/PlayerUtils.kt` extension (1.12.2's `setPositionAndUpdate`) — see the
 * batch report: its 1.21.1 port does not exist yet, so the call is kept 1:1.
 */
@ModEnchantment(
    id = "void_protection",
    category = EnchantCategory.COMMON,
    type = EnchantType.BOOTS,
    maxLevel = 3,
    weight = 10,
    anvilCost = 2,
    minCostBase = 30,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/foot_armor",
    slots = [EnchantSlot.FEET],
    order = 5,
)
object EnchantVoidProtectionHandler : Listenable {
    val waitForCancel = mutableMapOf<UUID, Boolean>()
    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        val stack = p.getEquippedStack(EquipmentSlot.FEET)
        val level = getItemSpecificEnchantLevel(stack, GeneratedEnchantments.VOID_PROTECTION)
        val uuid = p.uuid
        if (level > 0) {
            if (p.y <= -64 && p.fallDistance > 32.0) {
                val enchants = EnchantmentHelper.getEnchantments(stack)
                val enchantsBuilder = ItemEnchantmentsComponent.Builder(enchants)
                if (level - 1 > 0) {
                    enchantsBuilder.set(
                        enchants.enchantments.first { it.matchesKey(GeneratedEnchantments.VOID_PROTECTION) },
                        level - 1
                    )
                } else {
                    enchantsBuilder.remove { it.matchesKey(GeneratedEnchantments.VOID_PROTECTION) }
                }
                p.tp(y=256.0)
                p.sendMessage(Text.literal("Triggered Void Protection!"), false)
                stack.set(DataComponentTypes.ENCHANTMENTS, enchantsBuilder.build())
                waitForCancel[uuid] = true
                STLog.log("VoidProtection") {
                    "player=${p.name.string}, lvl=$level, y=${p.y}, fallDistance=${p.fallDistance}, " +
                        "newLvl=${level - 1}, outcome=teleported-to-y256"
                }
            }
        }
        if (p.isOnGround && waitForCancel[uuid] == true) {
            waitForCancel[uuid] = false
            STLog.log("VoidProtection") {
                "player=${p.name.string}, fallDistance=${p.fallDistance}, outcome=landed-fall-cancelled"
            }
        }
    }
    @SubscribeEvent
    fun onLivingFall(event: LivingFallEvent) {
        val entity = event.entityLiving
        if (entity !is PlayerEntity) return
        if (WorldSide.isClient(entity.world)) return

        val stack = entity.getEquippedStack(EquipmentSlot.FEET)
        if (stack.isEmpty) return

        val level = getItemSpecificEnchantLevel(stack, GeneratedEnchantments.VOID_PROTECTION)
        if (level <= 0) return

        if (waitForCancel[entity.uuid] == true) {
            val fallDistance = event.distance
            event.isCanceled = true
            entity.fallDistance = 0f
            waitForCancel[entity.uuid] = false
            STLog.log("VoidProtection") {
                "player=${entity.name.string}, lvl=$level, fallDistance=$fallDistance, outcome=fall-cancelled"
            }
        }
    }

}
