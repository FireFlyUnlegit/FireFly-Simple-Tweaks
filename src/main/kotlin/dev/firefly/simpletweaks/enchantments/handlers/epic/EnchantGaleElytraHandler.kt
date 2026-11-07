package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import java.util.WeakHashMap

/**
 * 疾风鞘翅 — faster gliding, plus one mid-air **dash** on a cooldown.
 *
 * ## Why velocity and not an attribute
 * Elytra flight is pure velocity: vanilla computes it in `LivingEntity#travel` from the look direction and
 * never reads `generic.movement_speed`. So the only honest lever is adding to the velocity while
 * `isFallFlying`, which is what this does — a small per-tick push along the current heading.
 *
 * ## The dash
 * Crouching already means "dive" while gliding, so it doubles as the dash input: while gliding and
 * crouching, every [DASH_COOLDOWN] ticks the player gets a burst along the look vector and the elytra pays
 * durability. Server-side only — velocity is tracked data, so the client receives it.
 *
 * ## Limits
 * - the per-tick push is capped so a level III elytra does not turn into a creative jetpack;
 * - the dash needs durability to spend, so it cannot be used with a nearly broken elytra.
 */
@ModEnchantment(
    id = "gale_elytra",
    category = EnchantCategory.EPIC,
    type = EnchantType.CHESTPLATE,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = EnchantSupportedItemTags.ELYTRA,
    slots = [EnchantSlot.CHEST],
)
object EnchantGaleElytraHandler : Listenable {

    private const val GLIDE_PUSH_PER_LEVEL = 0.012
    private const val GLIDE_PUSH_CAP = 0.05
    private const val DASH_COOLDOWN = 200
    private const val DASH_STRENGTH = 1.15
    private const val DASH_DURABILITY_PER_LEVEL = 3

    private val lastDash = WeakHashMap<PlayerEntity, Long>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        if (!player.isFallFlying) return

        val elytra = player.chestplate
        val level = getItemSpecificEnchantLevel(elytra, GeneratedEnchantments.GALE_ELYTRA)
        if (level <= 0) return

        // Steady push along the heading: this is the "faster glide" half.
        val look = player.getRotationVector()
        val push = (GLIDE_PUSH_PER_LEVEL * level).coerceAtMost(GLIDE_PUSH_CAP)
        player.addVelocity(look.x * push, look.y * push * 0.35, look.z * push)

        // Dash: crouch while gliding.
        if (!player.isSneaking) return
        val now = player.world.time
        val last = lastDash[player]
        if (last != null && now - last < DASH_COOLDOWN) return

        val cost = DASH_DURABILITY_PER_LEVEL * level
        if (elytra.maxDamage - elytra.damage <= cost) return

        lastDash[player] = now
        player.addVelocity(look.x * DASH_STRENGTH, look.y * DASH_STRENGTH * 0.5, look.z * DASH_STRENGTH)
        elytra.damage(cost, player, EquipmentSlot.CHEST)

        FSTLog.log("GaleElytra") {
            "player=${player.name.string}, level=$level, push=${"%.3f".format(push)}, " +
                "durabilityCost=$cost, outcome=dashed"
        }
    }
}
