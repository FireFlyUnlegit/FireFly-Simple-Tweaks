package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import java.util.WeakHashMap
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/common/EnchantCombatMasterHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                       | 1.21.1                                                            |
 * |----------------------------------------------|-------------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`     | `compat.event.LivingDamageEvent`                                   |
 * | `net.minecraftforge...LivingEvent`           | `compat.event.LivingEvent`                                         |
 * | `net.minecraftforge...EventPriority`         | `compat.event.EventPriority` (annotation still carries `priority`) |
 * | `EntityLivingBase` / `EntityPlayer`          | `LivingEntity` / `PlayerEntity`                                    |
 * | `e.entityLiving` / `e.source.trueSource`     | same field / `source.attacker` (method_5529)                       |
 * | `world.isRemote`                             | `WorldSide.isClient(world)` (field_9236 is a FIELD, see MIGRATION) |
 * | `world.totalWorldTime`                       | `world.time` (Yarn `World.getTime()`, method_8510)                 |
 * | `target.uniqueID`                            | `target.uuid` (inherited from `EntityLike`, method_5667)           |
 * | `EnchantCombatMaster` (object)               | `GeneratedEnchantments.COMBAT_MASTER` (RegistryKey)                   |
 * | NBT `getInteger/setInteger/getString/...`    | `getInt/putInt/getString/putString/getLong/putLong/remove`         |
 *
 * ⚠️ **DEVIATION — please review by hand (the only one in this file).**
 * The 1.12.2 code kept the combo state in `Entity#getEntityData()`, a Forge-only *persistent*
 * per-entity `NBTTagCompound` bag that 1.21.1 has no counterpart for: `Entity.getDataTracker()`
 * (method_18358) is the synced tracker built from registered `TrackedData` entries, not arbitrary
 * NBT, and vanilla exposes no per-entity custom-NBT accessor at all (Fabric's real equivalent is a
 * codec-backed Attachment). To keep this a mechanical port, the NBT keys and every `getInt/putInt/...`
 * call below are unchanged — only the *backing store* moved into this file, so the state now lives
 * and dies with the entity **instance** instead of being written to disk with it. Replacing
 * [entityData] with `AttachmentRegistry.createPersistent(...)` restores the original persistence.
 */
@ModEnchantment(
    id = "combat_master",
    category = EnchantCategory.COMMON,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 10,
    anvilCost = 2,
    minCostBase = 8,
    minCostPerLevel = 8,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.2,
    order = 3,
)
object EnchantCombatMasterHandler : Listenable {
    private const val NBT_COMBO_TARGET   = "st_combo_target"
    private const val NBT_COMBO_STACKS   = "st_combo_stacks"   
    private const val NBT_COMBO_LAST_HIT = "st_combo_last_hit" 
    private const val MAX_STACKS = 200

    /** Stand-in for Forge's per-entity `Entity#getEntityData()` bag (see the class comment). */
    private val entityData = WeakHashMap<Entity, NbtCompound>()

    private fun Entity.data(): NbtCompound = entityData.getOrPut(this) { NbtCompound() }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(event: LivingDamageEvent) {
        if (event.invalid) return

        val target = event.entityLiving ?: return
        val world = target.world ?: return
        if (WorldSide.isClient(world)) return

        val source = event.source ?: return
        val attacker = source.attacker
        val now = world.time

        if (target is PlayerEntity) {
            val data = target.data()
            if (data.getInt(NBT_COMBO_STACKS) > 0) {
                val stacksLost = data.getInt(NBT_COMBO_STACKS)
                resetCombo(data)
                STLog.log("CombatMaster") {
                    "comboBroken on=${target.name.string}, stacksLost=$stacksLost, now=0"
                }
            }
        }

        if (attacker is LivingEntity && attacker !== target) {
            val level = getItemSpecificEnchantLevel(
                attacker.mainHandStack,
                GeneratedEnchantments.COMBAT_MASTER
            )
            if (level > 0) {
                val data = attacker.data()
                val prevTarget = data.getString(NBT_COMBO_TARGET)
                val targetUUID = target.uuid.toString()

                val stacks = if (prevTarget == targetUUID) {
                    (data.getInt(NBT_COMBO_STACKS) + 1).coerceAtMost(MAX_STACKS)
                } else {
                    1
                }
                data.putString(NBT_COMBO_TARGET, targetUUID)
                data.putInt(NBT_COMBO_STACKS, stacks)
                data.putLong(NBT_COMBO_LAST_HIT, now)

                val bonus = (stacks * 0.02f * level)
                    .coerceAtMost(0.2f * level)
                event.amount *= (1f + bonus)
                STLog.log("CombatMaster") {
                    "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$level, stacks=$stacks, " +
                        "cap=${0.2f * level}, bonus=$bonus, damage=${event.amount}"
                }
            }
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(event: LivingEvent.LivingUpdateEvent) {
        val entity = event.entityLiving ?: return
        if (entity !is PlayerEntity) return
        if (WorldSide.isClient(entity.world)) return

        val data = entity.data()
        if (data.getInt(NBT_COMBO_STACKS) <= 0) return

        val lastHit = data.getLong(NBT_COMBO_LAST_HIT)
        if (lastHit <= 0L) return

        val now = entity.world.time
        if (now - lastHit > 100) {
            resetCombo(data)
        }
    }

    private fun resetCombo(data: NbtCompound) {
        data.putInt(NBT_COMBO_STACKS, 0)
        data.remove(NBT_COMBO_TARGET)
        data.remove(NBT_COMBO_LAST_HIT)
    }
}
