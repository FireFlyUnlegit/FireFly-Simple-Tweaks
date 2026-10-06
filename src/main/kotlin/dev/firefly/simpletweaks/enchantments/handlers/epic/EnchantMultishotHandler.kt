package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.interfaces.SimpleTweaksArrow
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ArrowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import kotlin.math.cos
import kotlin.math.sin

/**
 * `multishot`: one bow shot fires `1 + level` arrows in a deterministic fan.
 *
 * <h2>How it works in 1.21</h2>
 * [ArrowLooseEvent] cancels vanilla's shot and this handler builds the arrows itself, exactly as
 * 1.12.2 did. The alternative — making the enchantment declarative with
 * `minecraft:projectile_count` + `minecraft:projectile_spread` — **would** also reproduce the count
 * and the fan (see `docs/phase6-client-notes.md` §21: vanilla's `RangedWeaponItem#shootAll` computes
 * a deterministic per-arrow yaw offset), but the author chose the faithful implementation, so the
 * `charge < 5` gate and the exact 5-degree step are reproduced here rather than delegated.
 *
 * <h2>Arrow construction uses vanilla's factory, not a hand-rolled entity</h2>
 * 1.12.2 did `new EntityTippedArrow(world, player)` plus `setPotionEffect(ammo)` when the ammo was a
 * tipped arrow. In 1.21 the equivalent is `ArrowItem#createArrow(World, ItemStack ammo, LivingEntity,
 * ItemStack weapon)`, which every arrow item implements and which reads the potion contents out of the
 * ammo stack itself. Using it means spectral, tipped and modded arrows all behave, instead of this
 * handler special-casing one of them.
 *
 * <h2>Why the second handler exists</h2>
 * Targets have invulnerability frames, so a volley of `1 + level` arrows would otherwise land exactly
 * one hit. 1.12.2 marked its arrows and zeroed the target's hurt-resistance timer; this port does the
 * same through [SimpleTweaksArrow]. The field is `Entity#timeUntilRegen` in 1.21 (verified from the
 * jar — it lives on `Entity`, not `LivingEntity`, and is `public`, so no accessor mixin is needed).
 *
 * <p>Note the port's `LivingHurtEvent` fires at the **head** of `LivingEntity#damage`, i.e. *before*
 * the invulnerability check reads `timeUntilRegen`, which is exactly where zeroing it has an effect.
 * That is verified, not assumed: the guard is at {@code LivingEntity#damage} offsets 228..237
 * ({@code if (timeUntilRegen > 10 && !isIn(BYPASSES_COOLDOWN))} → the "already hurt" branch), and
 * `EntityDamageMixin` injects at HEAD, so a zeroed field takes the full-damage branch.
 *
 * <p><b>Status: verified working (2026-10-06, `build=infpower3`).</b> This was reported as "the volley
 * only counts one hit", and the guard position above means the zeroing is provably effective *if it
 * runs* — so the failure had to be either (a) [onHurt] never being reached, or (b)
 * `simpletweaks$isMultishotArrow()` returning false. Two temporary per-arrow probes answered both in
 * one session: of 76 arrow hits, **75 reported `flagged=true`** (the single `false` was a plain arrow
 * and serves as the negative control), and from the second arrow on `regenBefore=20 → regenAfter=0`
 * with a *different* damage amount per arrow (13/10/10/11) — every arrow of the volley settles
 * separately. The probes have since been removed; the per-volley `outcome=volley-spawned` line stays,
 * matching how the other handlers use `STLog`.
 *
 * <p>Note this cannot be delegated to a damage-type tag: `#minecraft:bypasses_cooldown` is referenced
 * by `LivingEntity#damage` but **does not exist in 1.21.1's vanilla data** (the tag resolves empty),
 * and tagging `minecraft:arrow` would make every arrow in the game ignore i-frames.
 */
object EnchantMultishotHandler : Listenable {

    @SubscribeEvent
    fun onArrowLoose(e: ArrowLooseEvent) {
        val p = e.entityPlayer
        val world = p.world
        if (world.isClient) return

        val bow = e.bow
        val lvl = getItemSpecificEnchantLevel(bow, ModEnchantmentKeys.MULTISHOT)
        if (lvl <= 0) return
        if (e.charge < 5) return

        val ammo = findAmmo(p) ?: return

        // Vanilla's shot is replaced entirely from here on.
        e.isCanceled = true

        val wanted = 1 + lvl
        val actual = minOf(wanted, ammo.count)
        if (actual <= 0) return

        val charge = e.charge / 20.0f
        val speed = charge * 3.0f

        val creative = p.abilities.creativeMode
        val infinity = getItemSpecificEnchantLevel(bow, Enchantments.INFINITY) > 0
        val noPickup = !creative && infinity

        val arrowItem = ammo.item as? ArrowItem ?: Items.ARROW as ArrowItem

        for (i in 0 until actual) {
            val arrow = arrowItem.createArrow(world, ammo, p, bow)

            // 1.12.2's damage formula, unchanged.
            arrow.damage = (charge * 2.0f).toDouble() +
                world.random.nextGaussian() * 0.25 +
                world.difficulty.id * 0.11
            if (charge >= 1.0f) {
                arrow.isCritical = true
            }

            // 1.12.2's fan: each arrow yawed by 5 degrees about the volley's centre.
            val yaw = p.yaw + (i - (actual - 1) / 2f) * 5.0f
            val pitchRad = Math.toRadians(p.pitch.toDouble())
            val yawRad = Math.toRadians(yaw.toDouble())
            val dx = -sin(yawRad) * cos(pitchRad)
            val dy = -sin(pitchRad)
            val dz = cos(yawRad) * cos(pitchRad)

            arrow.setVelocity(dx, dy, dz, speed, 1.0f)
            arrow.setPosition(p.x, p.eyeY - 0.1, p.z)

            (arrow as SimpleTweaksArrow).`simpletweaks$setMultishotArrow`(true)
            if (noPickup) {
                arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED
            }

            world.spawnEntity(arrow)
        }

        // Vanilla's own ammo handling never ran, because the shot was cancelled.
        if (!creative) {
            if (!infinity || ammo.isOf(Items.TIPPED_ARROW)) {
                ammo.decrement(actual)
            }
            bow.damage(1, p, LivingEntity.getSlotForHand(p.activeHand))
        }

        world.playSound(
            null,
            p.x, p.y, p.z,
            SoundEvents.ENTITY_ARROW_SHOOT,
            SoundCategory.PLAYERS,
            1.0f,
            1.0f / (world.random.nextFloat() * 0.4f + 1.2f) + charge * 0.5f,
        )

        STLog.log("Multishot") {
            "player=${p.name.string}, lvl=$lvl, charge=${e.charge}, ammo=${ammo.count}, " +
                "arrows=$actual, wanted=$wanted, infinity=$infinity, creative=$creative, " +
                "outcome=volley-spawned"
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onHurt(e: LivingHurtEvent) {
        val arrow = e.source.source as? PersistentProjectileEntity ?: return
        if (!(arrow as SimpleTweaksArrow).`simpletweaks$isMultishotArrow`()) return
        // 1.12.2: `e.entityLiving.hurtResistantTime = 0`.
        e.entityLiving.timeUntilRegen = 0
    }

    /** 1.12.2 `findAmmo`: offhand, then main hand, then the rest of the inventory. */
    private fun findAmmo(p: PlayerEntity): ItemStack? {
        val off = p.offHandStack
        if (isArrow(off)) return off
        val main = p.mainHandStack
        if (isArrow(main)) return main
        for (stack in p.inventory.main) {
            if (isArrow(stack)) return stack
        }
        return null
    }

    private fun isArrow(stack: ItemStack): Boolean =
        !stack.isEmpty && (
            stack.isOf(Items.ARROW) ||
                stack.isOf(Items.SPECTRAL_ARROW) ||
                stack.isOf(Items.TIPPED_ARROW)
            )
}
