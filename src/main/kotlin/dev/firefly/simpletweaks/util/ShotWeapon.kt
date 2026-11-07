package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtOps
import java.util.WeakHashMap

/**
 * "Which weapon does this hit count as?" — the snapshot that stops a fired arrow from inheriting
 * whatever the shooter happens to hold at **impact** time.
 *
 * ## The bug this exists for
 * `build=cleanup29` and earlier resolved every weapon-derived effect from `attacker.mainHandStack` at the
 * moment of the **hit**. For a melee swing that is correct. For an arrow it is not: the shot is fired with
 * the bow, and then the player swaps to a netherite sword while the arrow is still in the air. Every
 * on-hit handler (crit, combo, true damage, conversion, mastery, …) then read the **sword**, and the arrow
 * hit like that sword — the reported "射出去以后如果切回牛逼的主近战武器会导致伤害暴增".
 *
 * ## How the snapshot is stored
 * A `WeakHashMap` keyed by the **projectile entity object**, filled when its owner is set — i.e. inside the
 * shot, while the shooter still holds the bow. Deliberately not item NBT:
 *
 * - arrows are entities, and `PersistentProjectileEntity#getItemStack()` returns a **fresh** arrow stack on
 *   every call, so anything written there would not survive;
 * - entities in 1.21 have plain NBT, not data components, and their `writeCustomDataToNbt` hook is not
 *   reachable from a helper without another mixin;
 * - the value is only ever read by the **server** at damage time, in the same tick-ish window, so a
 *   memory-only map is sufficient and has no sync or persistence burden (`AGENTS.md` §4.11: no client half
 *   to keep in step).
 *
 * A save/reload with an arrow mid-flight drops the snapshot; the arrow then falls back to vanilla's own
 * `weapon` field, and only if that is absent to the current main hand.
 *
 * ## Second layer: vanilla already keeps the firing weapon
 * `javap` on 1.21.1: `PersistentProjectileEntity#getWeaponStack()` returns the `weapon` field, and
 * `DamageSource#getWeaponStack()` returns `source.getWeaponStack()`. That is used as a fallback for
 * projectiles this mod did not see spawn.
 */
object ShotWeapon {

    /** projectile → the weapon stack it was fired with. */
    private val SNAPSHOTS = WeakHashMap<Entity, ItemStack>()

    /** Records [weapon] as the weapon [projectile] was fired with. Never overwrites an existing snapshot. */
    fun record(projectile: PersistentProjectileEntity, weapon: ItemStack) {
        if (projectile.world.isClient) return
        if (weapon.isEmpty) return
        if (SNAPSHOTS.containsKey(projectile)) return
        SNAPSHOTS[projectile] = weapon.copy()

        FSTLog.log("ShotWeapon") {
            "outcome=recorded, entity=${projectile.type.name.string}, item=${weapon.item}, " +
                "enchants=${weapon.get(net.minecraft.component.DataComponentTypes.ENCHANTMENTS)?.enchantments?.size ?: 0}"
        }
    }

    // ------------------------------------------------------------------ entity NBT

    /** Entity-NBT key: the snapshot is written next to the arrow's own data, so it survives a save. */
    const val NBT_KEY = "simple_tweaks:shot_weapon"

    /**
     * Writes the snapshot into the projectile's **own** NBT.
     *
     * Called from `PersistentProjectileEntity#writeCustomDataToNbt` (see
     * `ProjectileShotWeaponMixin`). This is what makes the snapshot survive a chunk save with the arrow
     * still in flight — the memory map alone would lose it, and the arrow would fall back to "whatever is
     * in the shooter's hand now", i.e. exactly the bug this class exists to prevent.
     */
    fun writeTo(projectile: Entity, nbt: NbtCompound) {
        val snapshot = snapshotOf(projectile) ?: return
        val encoded = ItemStack.UNCOUNTED_CODEC.encodeStart(NbtOps.INSTANCE, snapshot).result().orElse(null)
        if (encoded != null) {
            nbt.put(NBT_KEY, encoded)
        }
    }

    /** Reads the snapshot back out of the projectile's NBT; called from `readCustomDataFromNbt`. */
    fun readFrom(projectile: Entity, nbt: NbtCompound) {
        if (!nbt.contains(NBT_KEY)) return
        val element = nbt.get(NBT_KEY) ?: return
        val decoded = ItemStack.UNCOUNTED_CODEC.parse(NbtOps.INSTANCE, element).result().orElse(null)
        if (decoded == null || decoded.isEmpty) return
        SNAPSHOTS[projectile] = decoded

        FSTLog.log("ShotWeapon") {
            "outcome=restored-from-nbt, entity=${projectile.type.name.string}, item=${decoded.item}"
        }
    }

    /** The snapshot recorded for [projectile], or `null`. */
    fun snapshotOf(projectile: Entity): ItemStack? = SNAPSHOTS[projectile]?.takeUnless { it.isEmpty }

    /**
     * The weapon a hit should be evaluated with, or `null` when the hit carries no projectile at all.
     *
     * Order: our snapshot → vanilla's `weapon` field on the projectile.
     */
    fun weaponFor(source: DamageSource?): ItemStack? {
        if (source == null) return null
        val direct = source.source
        if (direct is PersistentProjectileEntity) {
            snapshotOf(direct)?.let { return it }
        }
        val fromVanilla = source.weaponStack
        if (fromVanilla != null && !fromVanilla.isEmpty) return fromVanilla
        return null
    }
}

/**
 * The stack that **combat** should read for this hit.
 *
 * Replacement for `mainHandStack` in every on-hit path: for a melee hit it is the attacker's main hand;
 * for an arrow it is the weapon the arrow was fired with (see [ShotWeapon]).
 *
 * Declared on [LivingEntity] rather than on [PlayerEntity] because most handlers hold the attacker as a
 * `LivingEntity` (`e.source.attacker as? LivingEntity`), and widening it here keeps those call sites free
 * of casts. A non-player attacker simply has no snapshot to look up and falls back to its own main hand.
 */
fun LivingEntity.combatStack(source: DamageSource?): ItemStack {
    val snap = ShotWeapon.weaponFor(source)
    return snap ?: this.mainHandStack
}
