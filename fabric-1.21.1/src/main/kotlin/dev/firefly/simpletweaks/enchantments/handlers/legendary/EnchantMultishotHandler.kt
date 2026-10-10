package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantFastBowHandler
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
 * [dev.firefly.simpletweaks.compat.event.ArrowLooseEvent] cancels vanilla's shot and this handler builds the arrows itself, exactly as
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
 * same through [dev.firefly.simpletweaks.interfaces.SimpleTweaksArrow]. The field is `Entity#timeUntilRegen` in 1.21 (verified from the
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
@ModEnchantment(
    id = "multishot",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.BOW,
    maxLevel = 5,
    weight = EnchantItemWeights.LEGENDARY,
    anvilCost = 6,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/bow",
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 28,
)
object EnchantMultishotHandler : Listenable {

    @SubscribeEvent
    fun onArrowLoose(e: ArrowLooseEvent) {
        val p = e.entityPlayer
        val world = p.world
        if (world.isClient) return

        val bow = e.bow
        val lvl = getItemSpecificEnchantLevel(bow, GeneratedEnchantments.MULTISHOT)
        if (lvl <= 0) return
        // Anti-tap gate, relaxed by fast_bow: a real draw ends `fast_bow level` ticks sooner, so its
        // level is subtracted from the 5-tick floor. Clamped at 1 tick -- at 0 the gate would pass on
        // a zero-tick release and spawn a zero-velocity volley.
        val fastBowLvl = EnchantFastBowHandler.fastBowLevel(bow)
        val minCharge = (5 - fastBowLvl).coerceAtLeast(1)
        if (e.charge < minCharge) return

        val ammo = findAmmo(p) ?: return

        // Vanilla's shot is replaced entirely from here on.
        e.isCanceled = true

        // The volley is `1 + level`, full stop: it is NOT capped by how many arrows are left, and a
        // release costs exactly ONE arrow -- vanilla's own price.
        //
        // The previous `minOf(1 + lvl, ammo.count)` had two problems. A nearly-empty quiver silently
        // shrank the volley to as many arrows as were left, so "1 arrow left" fired a 1-arrow
        // "volley" that is indistinguishable from the enchantment doing nothing. And because the
        // consumption below was `decrement(actual)`, one release charged `1 + level` arrows -- the
        // enchantment got worse the fewer arrows you had, which is backwards.
        // `findAmmo` above already guarantees a stack exists to draw the arrows from and to pay with.
        val arrows = 1 + lvl

        // fast_bow normally reaches the shot's power through the `@ModifyArg` on `getPullProgress`
        // in BowItemArrowLooseMixin -- but that injector sits at offset 44, *after* the HEAD inject,
        // and cancelling here returns from `onStoppedUsing` before it ever runs. So a volley built by
        // this handler has to apply the boost itself, or fast_bow is silently dropped whenever both
        // enchantments sit on the same bow. The symptom is distinctive: the *client* never cancels
        // (see the `world.isClient` return above), so the draw animation still speeds up while the
        // arrows come out at unboosted power.
        //
        // Recomputed from the stack rather than read off `ChargeBoost.value`: this declaration carries
        // `order = 28` while fast_bow leaves `order` at its default `Int.MAX_VALUE`, so fast_bow's
        // listener runs *after* this one and the channel would still hold its just-reset `1.0f`.
        // `chargeBoost` is fast_bow's own single source of truth -- the client animation mixin calls
        // the very same function -- so the volley cannot drift from the animation.
        val boost = EnchantFastBowHandler.chargeBoost(bow)
        // Same rounding as that `@ModifyArg`, and the same clamp its contract promises: a boost may
        // bring a partial draw up to full-draw power, never above it. Without the clamp a boosted
        // long hold would overcharge (and `charge >= 1.0f` below would still be the critical flag).
        val drawnTicks = if (boost == 1.0f) e.charge else Math.round(e.charge * boost)
        val charge = (drawnTicks / 20.0f).coerceAtMost(1.0f)
        val speed = charge * 3.0f

        val creative = p.abilities.creativeMode
        val infinity = getItemSpecificEnchantLevel(bow, Enchantments.INFINITY) > 0
        val noPickup = !creative && infinity

        val arrowItem = ammo.item as? ArrowItem ?: Items.ARROW as ArrowItem

        for (i in 0 until arrows) {
            val arrow = arrowItem.createArrow(world, ammo, p, bow)

            // 1.12.2's damage formula, unchanged.
            arrow.damage = (charge * 2.0f).toDouble() +
                world.random.nextGaussian() * 0.25 +
                world.difficulty.id * 0.11
            if (charge >= 1.0f) {
                arrow.isCritical = true
            }

            // 1.12.2's fan: each arrow yawed by 5 degrees about the volley's centre.
            val yaw = p.yaw + (i - (arrows - 1) / 2f) * 5.0f
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

        // Vanilla's own ammo handling never ran, because the shot was cancelled. One arrow, not one
        // per projectile -- see `arrows` above.
        if (!creative) {
            if (!infinity || ammo.isOf(Items.TIPPED_ARROW)) {
                ammo.decrement(1)
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
            "player=${p.name.string}, lvl=$lvl, charge=${e.charge}, minCharge=$minCharge, " +
                "fastBow=$fastBowLvl, boost=$boost, drawn=$drawnTicks, speed=$speed, " +
                "ammo=${ammo.count}, arrows=$arrows, " +
                "infinity=$infinity, creative=$creative, outcome=volley-spawned"
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