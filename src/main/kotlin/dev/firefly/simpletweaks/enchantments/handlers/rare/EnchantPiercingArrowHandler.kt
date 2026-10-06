package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.event.EntityJoinWorldEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.interfaces.SimpleTweaksArrow
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.MathHelper

/**
 * `piercing_arrow`: arrows pass through entities, and may hit the **same** target several times.
 *
 * <h2>Why this is the hard one</h2>
 * 1.12.2 hooked a single `EntityArrow#onHit(RayTraceResult)` and cancelled it to mean "keep flying";
 * damage was dealt by the mod itself. 1.21 splits hits into `onEntityHit` / `onBlockHit`, and
 * **`onEntityHit` performs the damage *and* stops the arrow in the same body**. A blanket
 * HEAD `@Inject(cancellable)` would therefore produce an arrow that pierces but deals **no damage at
 * all** — a "the name matches but the semantics do not" trap of exactly the kind recorded in
 * `docs/phase6-client-notes.md`.
 *
 * <p>So the seam is HEAD-cancel plus re-implemented damage:
 * <ul>
 *   <li>[handleHit] returns `true` while the arrow may still pierce → the mixin cancels vanilla, the
 *       arrow keeps its velocity, and the damage has already been applied here;</li>
 *   <li>it returns `false` when the pierce budget is spent → vanilla runs normally and stops the arrow.</li>
 * </ul>
 *
 * <h2>The "double damage on the last hit" question</h2>
 * On the final hit this handler damages the target and *then* lets vanilla damage it again. That is
 * 1.12.2's control flow too. In practice the second hit is absorbed by invulnerability frames: this
 * handler calls `runPlayerAttack(forceHit = true)`, which zeroes `timeUntilRegen` **before** damaging,
 * and a landed hit sets it back to 20 — so vanilla's immediate follow-up is rejected. The observable
 * result is one damaging hit plus the arrow stopping, which is the intent.
 *
 * <h2>1.12.2 → 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `IPiercingArrow` synced fields | [SimpleTweaksArrow] synced `TrackedData` |
 * | `entityData["st_pierce_hits"]` packed string | per-arrow `Map<Int, Int>` field |
 * | `DamageSource.causeArrowDamage(arrow, shooter)` | `arrow.getDamageSources().arrow(arrow, shooter)` |
 * | `MathHelper.sqrt(motionX² + …)` | `arrow.getVelocity().length()` |
 * | `target.setFire(5)` | `target.setOnFireFor(5f)` |
 * | `arrow.motionX` speed × `arrow.damage` | unchanged formula, via `getVelocity()`/`getDamage()` |
 *
 * <p>`parseHitCounts` / `serializeHitCounts` have **no counterpart here** on purpose: 1.12.2 packed the
 * per-target counts into an NBT string and re-parsed it on every collision, and the port keeps the same
 * information as a map on the projectile instead (see the state mixin's KDoc).
 */
// Declared with `@ModEnchantment`, so KSP generates `piercing_arrow.json`, its registry key and its
// index metadata. Every value below was copied from the JSON this replaced
// (`anvilCost=6, maxLevel=5, minCost 32+12/level, weight=6`); `piercing_arrow` is a bow enchantment
// with **no** effect components.
@ModEnchantment(
    id = "piercing_arrow",
    category = EnchantCategory.RARE,
    type = EnchantType.BOW,
    maxLevel = 5,
    weight = 6,
    anvilCost = 6,
    minCostBase = 32,
    minCostPerLevel = 12,
    supportedItems = "#minecraft:enchantable/bow",
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantPiercingArrowHandler : Listenable {

    @SubscribeEvent
    fun onEntityJoin(e: EntityJoinWorldEvent) {
        if (e.world.isClient) return
        val arrow = e.entity as? PersistentProjectileEntity ?: return
        val shooter = arrow.owner as? PlayerEntity ?: return
        val lvl = getItemSpecificEnchantLevel(shooter.mainHandStack, GeneratedEnchantments.PIERCING_ARROW)
        if (lvl <= 0) return

        val state = arrow as SimpleTweaksArrow
        state.`simpletweaks$setPiercing`(true)
        state.`simpletweaks$setPierceRemaining`(lvl)
        state.`simpletweaks$setMaxPerTarget`(lvl)
        state.`simpletweaks$getPierceHits`().clear()
    }

    /**
     * Returns `true` when the arrow should keep flying (the caller must cancel vanilla's hit handling).
     *
     * <p>Called from `PersistentProjectileEntityPiercingMixin` on both sides, because the client branch
     * below needs the synced remaining count to predict whether the arrow continues — that is why the
     * piercing state is `TrackedData` rather than a plain field.
     */
    @JvmStatic
    fun handleHit(arrow: PersistentProjectileEntity, result: EntityHitResult): Boolean {
        val state = arrow as? SimpleTweaksArrow ?: return false
        if (!state.`simpletweaks$isPiercing`()) return false

        val target = result.entity as? LivingEntity ?: return false

        if (arrow.world.isClient) {
            return state.`simpletweaks$getPierceRemaining`() > 0
        }

        val shooter = arrow.owner as? PlayerEntity ?: return false

        val counts = state.`simpletweaks$getPierceHits`()
        val current = counts[target.id] ?: 0
        val maxPerTarget = state.`simpletweaks$getMaxPerTarget`()

        // Already hit this target as often as allowed: keep flying, but deal nothing.
        if (current >= maxPerTarget) return true

        val speed = arrow.velocity.length()
        var dmg = MathHelper.ceil(speed * arrow.damage).toFloat()
        if (arrow.isCritical) {
            dmg += arrow.world.random.nextInt((dmg / 2).toInt() + 2).toFloat()
        }

        val source = arrow.damageSources.arrow(arrow, shooter)
        if (arrow.isOnFire) {
            target.setOnFireFor(5f)
        }

        target.runPlayerAttack(
            attacker = shooter,
            rawDamage = dmg,
            forceHit = true,
            source = source,
            triggerEvent = true,
            allowCrit = false,
        )

        counts[target.id] = current + 1

        val remaining = state.`simpletweaks$getPierceRemaining`() - 1
        state.`simpletweaks$setPierceRemaining`(remaining)

        return remaining > 0
    }
}
