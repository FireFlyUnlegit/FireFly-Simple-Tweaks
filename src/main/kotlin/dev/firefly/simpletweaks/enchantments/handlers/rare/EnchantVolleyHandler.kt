package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantFastBowHandler
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ArrowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.MathHelper
import java.util.Collections
import java.util.UUID

@ModEnchantment(
    id = "volley",
    category = EnchantCategory.RARE,
    type = EnchantType.BOW,
    maxLevel = 3,
    weight = EnchantItemWeights.RARE,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 92,
)
object EnchantVolleyHandler : Listenable {

    // Ticks between the arrows of one volley: 3 keeps them visually distinct without feeling like a
    // burst of unrelated shots.
    private const val SPACING = 3L

    // Shorter draws leave the bow half-charged and cannot sustain a volley.
    private const val MIN_CHARGE = 5

    private class Pending(
        var rounds: Int,
        var nextAt: Long,
        val bow: ItemStack,
        val ammo: ItemStack,
        val speed: Float,
        val critical: Boolean,
    )

    private val pending: MutableMap<UUID, Pending> = Collections.synchronizedMap(HashMap())

    // multishot (order 28) cancels this event before we are dispatched; the queue must still arm.
    @SubscribeEvent(receiveCanceled = true)
    fun onArrowLoose(e: ArrowLooseEvent) {
        val player = e.entityPlayer
        if (WorldSide.isClient(player.world)) return
        val level = getItemSpecificEnchantLevel(e.bow, GeneratedEnchantments.VOLLEY)
        if (level <= 0) {
            val ids = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(e.bow).enchantments
                .joinToString(",") { it.key.map { k -> k.value.path }.orElse("?") }
            FSTLog.log("Volley") { "item=${e.bow.item}, lvl=0, enchants=[$ids], outcome=no-enchant" }
            return
        }
        // Gated on the effective charge: `fast_bow` keeps vanilla's tick count short while scaling the
        // power in `getPullProgress`, so a full draw of a fast_bow V bow reaches the server as charge=4.
        val boost = EnchantFastBowHandler.chargeBoost(e.bow)
        val drawnTicks = Math.round(e.charge * boost)
        if (drawnTicks < MIN_CHARGE) {
            FSTLog.log("Volley") {
                "item=${e.bow.item}, lvl=$level, charge=${e.charge}, boost=$boost, outcome=too-shallow"
            }
            return
        }
        val found = findAmmo(player)
        if (found == null && !player.abilities.creativeMode) {
            FSTLog.log("Volley") { "item=${e.bow.item}, lvl=$level, outcome=no-ammo" }
            return
        }
        // Creative fires arrows with an empty quiver; the follow-up rounds need a template stack.
        val ammo = found ?: ItemStack(Items.ARROW)
        val charge = (drawnTicks / 20f).coerceAtMost(1f)
        pending[player.uuid] = Pending(
            rounds = level,
            nextAt = player.world.time + SPACING,
            bow = e.bow.copy(),
            ammo = ammo.copy(),
            speed = charge * 3.0f,
            critical = charge >= 1.0f,
        )
        FSTLog.log("Volley") {
            "player=${player.name.string}, lvl=$level, charge=${e.charge}, outcome=queued"
        }
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        val state = pending[player.uuid] ?: return
        if (player.world.time < state.nextAt) return

        state.rounds--
        state.nextAt = player.world.time + SPACING
        val left = state.rounds
        spawn(player, state)
        if (left <= 0) pending.remove(player.uuid)

        FSTLog.log("Volley") {
            "player=${player.name.string}, left=$left, speed=${state.speed}, outcome=shot"
        }
    }

    private fun spawn(player: PlayerEntity, state: Pending) {
        val world = player.world
        val arrowItem = state.ammo.item as? ArrowItem ?: Items.ARROW as ArrowItem
        val arrow = arrowItem.createArrow(world, state.ammo, player, state.bow)
        arrow.damage = state.speed * 2.0f + world.random.nextGaussian() * 0.25
        arrow.isCritical = state.critical

        val pitchRad = Math.toRadians(player.pitch.toDouble())
        val yawRad = Math.toRadians(player.yaw.toDouble())
        val dx = -MathHelper.sin(yawRad.toFloat()) * MathHelper.cos(pitchRad.toFloat())
        val dy = -MathHelper.sin(pitchRad.toFloat())
        val dz = MathHelper.cos(yawRad.toFloat()) * MathHelper.cos(pitchRad.toFloat())
        arrow.setVelocity(dx.toDouble(), dy.toDouble(), dz.toDouble(), state.speed, 1.0f)
        arrow.setPosition(player.x, player.eyeY - 0.1, player.z)
        arrow.pickupType = net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED
        world.spawnEntity(arrow)
    }

    private fun findAmmo(player: PlayerEntity): ItemStack? {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (stack.item is ArrowItem) return stack
        }
        return null
    }
}
