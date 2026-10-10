package dev.firefly.simpletweaks.enchantments.handlers.mystery

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
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeInstance
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.nbt.NbtCompound
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.world.World
import java.util.WeakHashMap
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/mystery/EnchantHeavenlyPunishmentHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                       | 1.21.1                                                                    |
 * |--------------------------------------------------------------|---------------------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`                     | `compat.event.LivingDamageEvent`                                          |
 * | `net.minecraftforge...LivingEvent.LivingUpdateEvent`         | `compat.event.LivingEvent.LivingUpdateEvent`                              |
 * | `net.minecraftforge...EventPriority`                         | `compat.event.EventPriority` (the annotation keeps `priority`)             |
 * | `e.invalid`                                                  | same extension name, now `compat.invalid`                                 |
 * | `EntityLivingBase` / `EntityPlayer`                          | `LivingEntity` / `PlayerEntity`                                           |
 * | `source.trueSource`                                          | `source.attacker` (Yarn `method_5529`)                                    |
 * | `world.isRemote`                                             | `WorldSide.isClient(world)` (field_9236 is a FIELD, see MIGRATION)        |
 * | `world.totalWorldTime`                                       | `world.time` (`World.getTime()`, `method_8510`)                           |
 * | `attacker.heldItemMainhand`                                  | `attacker.mainHandStack` (`method_6047`)                                  |
 * | `attacker.uniqueID` / `target.uniqueID`                      | `.uuid` (inherited from `EntityLike`, `method_5667`)                      |
 * | `EnchantHeavenlyPunishment` (Enchantment object)             | `GeneratedEnchantments.HEAVENLY_PUNISHMENT` (RegistryKey)                    |
 * | `entity.motionX = motionY = motionZ = 0.0`                   | `entity.setVelocity(0.0, 0.0, 0.0)` (`method_18800`; 1.21 has one `Vec3d` velocity, not three fields) |
 * | `SharedMonsterAttributes.ATTACK_SPEED`                       | `EntityAttributes.GENERIC_ATTACK_SPEED` (1.21.1 kept the `GENERIC_` prefix) |
 * | `AttributeModifier(uuid, name, amount, 0)`                   | `EntityAttributeModifier(id, amount, Operation.ADD_VALUE)` (1.12.2 operation 0 = `ADD_VALUE`) |
 * | `applyModifier(...)` + `setSaved(false)`                     | **`addTemporaryModifier(...)`** — the per-modifier flag 1.21 *does* have. `setSaved(false)` meant "do not write this modifier into the entity's `Attributes` NBT"; `EntityAttributeInstance`'s persistent/temporary split is the exact counterpart, so the temporary call is the faithful mapping. Originally mapped to `addPersistentModifier` on the false premise that no such flag exists — see DEVIATION 3 |
 * | `removeModifier(UUID)`                                       | `removeModifier(Identifier)`                                              |
 * | `EntityLightningBolt(world, x, y, z, true)`                  | `EntityType.LIGHTNING_BOLT.create(world)` (`method_5883`; 1.21's `class_1538` has **no** `(World, double, double, double, boolean)` constructor) + `setCosmetic(true)` + `refreshPositionAndAngles(...)`. The 1.12.2 trailing `true` was already "effect-only", so `cosmetic` is the faithful flag |
 * | `world.addWeatherEffect(bolt)`                               | `World.spawnEntity(bolt)` (`ModifiableWorld.method_8649`; `addWeatherEffect` no longer exists in 1.21) |
 * | `EnumParticleTypes.CRIT_MAGIC` / `.END_ROD`                  | `ParticleTypes.ENCHANTED_HIT` (1.13 flattening rename of `CRIT_MAGIC`) / `ParticleTypes.END_ROD` |
 * | `WorldServer.spawnParticle(type, x,y,z, count, dx,dy,dz, speed)` | `ServerWorld.spawnParticles(effect, x,y,z, count, dx,dy,dz, speed)` (`method_14199`) |
 * | `SoundEvents.ENTITY_LIGHTNING_THUNDER`                       | `SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER` (renamed in 1.14)             |
 *
 * ⚠️ **DEVIATION 1 — per-entity NBT has no 1.21 counterpart (please review).**
 * The 1.12.2 handler kept all five weak/freeze fields in `Entity#getEntityData()`, Forge's
 * *persistent* per-entity `NBTTagCompound`. 1.21.1 exposes no such bag (`Entity.getDataTracker()`
 * is the registered-`TrackedData` tracker, not arbitrary NBT), so — exactly like
 * `EnchantCombatMasterHandler`, the established pattern for this port — the five NBT keys and every
 * `getLong/putLong/getFloat/putFloat/getString/putString/remove` call are **unchanged**, and only the
 * backing store moved: a `WeakHashMap<Entity, NbtCompound>`. Consequence: the weaken/freeze state now
 * lives and dies with the entity **instance** instead of being written to disk with it. Both effects
 * are sub-second (10–60 ticks), so the practical impact is limited to chunk unload/reload.
 * Replacing [entityData] with `AttachmentRegistry.createPersistent(...)` would restore persistence.
 *
 * ⚠️ **DEVIATION 2 — `getAttributeInstance` is `@Nullable` in 1.21** (1.12.2's `getEntityAttribute`
 * was not). `removeAttackSpeedSlow` therefore uses `?.removeModifier(...)`; the original would have
 * thrown on a living entity without an attack-speed attribute, which cannot happen in practice.
 * This matches the `?.` already used by `EnchantComboHandler` / `EnchantExtraArmorHandler`.
 *
 * ⚠️ **DEVIATION 3 (CORRECTED) — `setSaved(false)` is honoured, not dropped.**
 *
 * The original note here claimed that 1.21 "tracks `saved` at the `AttributeContainer`/`TrackedData`
 * level rather than per modifier and exposes no equivalent flag". **That is wrong twice over.** The
 * flag is per modifier — `EntityAttributeInstance` has both `addPersistentModifier` (serialised) and
 * `addTemporaryModifier` (not serialised) — and this file's own sibling,
 * [EnchantCelestialBlessingHandler], maps the *identical* 1.12.2 `setSaved(false)` pattern onto
 * `addTemporaryModifier`.
 *
 * The premise mattered, because it let a leak through. 1.12.2 had **two independent safeguards**: the
 * modifier was not written to disk, *and* the deadline that removes it lived in Forge's **persistent**
 * per-entity NBT. This port gives up the second deliberately (DEVIATION 1 — the deadline moved to the
 * in-memory [entityData]); keeping the modifier persistent then gives up the first as well, and the
 * failure is silent and permanent:
 *
 * <pre>
 *   victim's entityData entry is gone (chunk unload/reload, world restart, player relog)
 *     -> weakUntil reads 0
 *       -> the sweep in [onLivingUpdate] can never fire
 *         -> the -100 attack-speed modifier stays in the save file
 *           -> the victim never attacks again (mobs accumulate this across the world)
 * </pre>
 *
 * With `addTemporaryModifier` a lost deadline leaves nothing behind: the debuff ends at the latest on
 * reload, which is exactly the property `setSaved(false)` gave 1.12.2. The modifier is still
 * re-applied on every frozen tick ([onLivingUpdate]) and removed by [removeAttackSpeedSlow]; only its
 * ability to outlive the deadline is gone.
 */
@ModEnchantment(
    id = "heavenly_punishment",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.SWORD,
    maxLevel = 4,
    weight = 1,
    anvilCost = 14,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 5.0,
    order = 51,
    jsonEmit = false,
)
object EnchantHeavenlyPunishmentHandler : Listenable {

    private const val NBT_WEAK_UNTIL   = "st_hp_weak_until"
    private const val NBT_WEAK_AMOUNT  = "st_hp_weak_amount"
    private const val NBT_WEAK_OWNER   = "st_hp_weak_owner"
    private const val NBT_FREEZE_UNTIL = "st_hp_freeze_until"
    private const val NBT_LAST_FREEZE  = "st_hp_last_freeze"

    /** Stand-in for Forge's per-entity `Entity#getEntityData()` bag (see DEVIATION 1). */
    private val entityData = WeakHashMap<Entity, NbtCompound>()

    private fun Entity.data(): NbtCompound = entityData.getOrPut(this) { NbtCompound() }

    // 1.12.2 `UUID.nameUUIDFromBytes("simple_tweaks_heavenly_punishment_atk_speed")`; 1.21 attribute
    // modifiers are keyed by Identifier, so the UUID + its display name collapse into one id.
    private val ATK_SPEED_ID: Identifier =
        Identifier.of("simple_tweaks", "heavenly_punishment_atk_speed")

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(event: LivingDamageEvent) {
        if (event.invalid) return

        val target = event.entityLiving ?: return
        val world: World = target.world ?: return
        if (WorldSide.isClient(world)) return

        val source = event.source ?: return
        val attacker = source.attacker
        val now = world.time

        if (attacker is LivingEntity && attacker !== target) {
            val level = getItemSpecificEnchantLevel(
                attacker.mainHandStack,
                GeneratedEnchantments.HEAVENLY_PUNISHMENT
            )
            if (level > 0) {
                event.amount *= 1.0f * level + 1f

                val data: NbtCompound = target.data()
                data.putLong(NBT_WEAK_UNTIL, now + level * 10)
                data.putFloat(NBT_WEAK_AMOUNT, 0.225f * level)
                data.putString(NBT_WEAK_OWNER, attacker.uuid.toString())

                val lastFreeze = data.getLong(NBT_LAST_FREEZE)
                if (now - lastFreeze >= 20) {
                    data.putLong(NBT_FREEZE_UNTIL, now + 10)
                    data.putLong(NBT_LAST_FREEZE, now)
                }

                spawnLightningVisual(world, target)
                STLog.log("HeavenlyPunishment") {
                    "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$level, " +
                        "damage=${event.amount}, weakUntil=${now + level * 10}, weakAmount=${0.225f * level}, " +
                        "freezeUntil=${data.getLong(NBT_FREEZE_UNTIL)}, outcome=punished"
                }
            }
        }

        if (attacker is LivingEntity) {
            val data: NbtCompound = attacker.data()
            val until = data.getLong(NBT_WEAK_UNTIL)
            if (until > 0L && until > now) {
                val owner = data.getString(NBT_WEAK_OWNER)
                if (owner.isNotEmpty() && owner == target.uuid.toString()) {
                    val reduce = data.getFloat(NBT_WEAK_AMOUNT)
                    if (reduce > 0f) {
                        event.amount *= (1f - reduce)
                        STLog.log("HeavenlyPunishment") {
                            "attacker=${attacker.name.string}, target=${target.name.string}, " +
                                "weakAmount=$reduce, until=${until}, damage=${event.amount}, outcome=weakness-applied"
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(event: LivingEvent.LivingUpdateEvent) {
        val entity = event.entityLiving ?: return
        if (WorldSide.isClient(entity.world)) return

        val data: NbtCompound = entity.data()
        val now = entity.world.time

        val freezeUntil = data.getLong(NBT_FREEZE_UNTIL)
        if (freezeUntil > 0L) {
            if (freezeUntil > now) {
                val wasFrozen = freezeUntil > now + 1
                // 1.12.2 zeroed motionX/motionY/motionZ; 1.21 has a single velocity vector.
                entity.setVelocity(0.0, 0.0, 0.0)
                applyAttackSpeedSlow(entity)
                if (!wasFrozen) {
                    STLog.log("HeavenlyPunishment") {
                        "entity=${entity.name.string}, freezeUntil=$freezeUntil, now=$now, " +
                            "outcome=freeze-started"
                    }
                }
            } else {
                data.remove(NBT_FREEZE_UNTIL)
                STLog.log("HeavenlyPunishment") {
                    "entity=${entity.name.string}, freezeUntil=$freezeUntil, now=$now, outcome=freeze-expired"
                }
            }
        }

        val weakUntil = data.getLong(NBT_WEAK_UNTIL)
        if (weakUntil > 0L && weakUntil <= now) {
            removeAttackSpeedSlow(entity)
            data.remove(NBT_WEAK_UNTIL)
            data.remove(NBT_WEAK_AMOUNT)
            data.remove(NBT_WEAK_OWNER)
            STLog.log("HeavenlyPunishment") {
                "entity=${entity.name.string}, weakUntil=$weakUntil, now=$now, outcome=weakness-expired"
            }
        }
    }

    private fun applyAttackSpeedSlow(entity: LivingEntity) {
        val attr: EntityAttributeInstance? = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)
        if (attr == null) return
        attr.removeModifier(ATK_SPEED_ID)
        val modifier = EntityAttributeModifier(
            ATK_SPEED_ID,
            -100.0,
            EntityAttributeModifier.Operation.ADD_VALUE
        )
        // TEMPORARY, not persistent: this is 1.12.2's `setSaved(false)`. See DEVIATION 3 -- persisting
        // it is what turned a 10-tick suppression into a permanent attack lockout after any reload,
        // because the deadline that removes it lives in memory ([entityData]) and is gone by then.
        attr.addTemporaryModifier(modifier)
    }
    private fun removeAttackSpeedSlow(entity: LivingEntity) {
        val attr: EntityAttributeInstance? = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)
        attr?.removeModifier(ATK_SPEED_ID)
    }

    private fun spawnLightningVisual(world: World, target: LivingEntity) {
        // 1.12.2 `EntityLightningBolt(world, x, y, z, true)`: that trailing `true` was already
        // "effect only", so the cosmetic flag below is the faithful equivalent. 1.21's
        // `LightningEntity` kept only the `(EntityType, World)` constructor, hence `create` + move.
        val bolt = EntityType.LIGHTNING_BOLT.create(world)
        if (bolt != null) {
            bolt.setCosmetic(true)
            bolt.refreshPositionAndAngles(target.x, target.y, target.z, 0f, 0f)
            world.spawnEntity(bolt)
        }

        if (world is ServerWorld) {
            world.spawnParticles(
                ParticleTypes.ENCHANTED_HIT,
                target.x, target.y + target.height / 2.0, target.z,
                20, 0.5, 0.5, 0.5, 0.15
            )
            world.spawnParticles(
                ParticleTypes.END_ROD,
                target.x, target.y + target.height / 2.0, target.z,
                10, 0.3, 0.3, 0.3, 0.05
            )
        }

        world.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
            SoundCategory.PLAYERS,
            0.8f, 1.2f
        )
    }
}
