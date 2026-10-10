package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantHealingBladeHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                     | 1.21.1                                                             |
 * |------------------------------------------------------------|--------------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`                   | `compat.event.LivingDamageEvent`                                   |
 * | `net.minecraftforge...gameevent.TickEvent`                 | `compat.event.TickEvent`                                           |
 * | `net.minecraftforge...gameevent.PlayerEvent`               | `compat.event.PlayerEvent` (both Forge player events are merged)    |
 * | `net.minecraftforge...entity.player.PlayerEvent.Clone`     | `compat.event.PlayerEvent.Clone`                                    |
 * | `net.minecraftforge...EventPriority`                       | `compat.event.EventPriority`                                       |
 * | `e.attacker` / `e.target` / `e.invalid`                    | same extension names, now `compat.attacker` / `compat.target` / `compat.invalid` |
 * | `attacker.heldItemMainhand`                                | `attacker.mainHandStack` (`method_6047`)                           |
 * | `attacker.uniqueID`                                        | `attacker.uuid` (`EntityLike.getUuid`, method_5667)                |
 * | `attacker.health` / `attacker.maxHealth`                   | unchanged (`method_6032` / `method_6063`)                          |
 * | `attacker.absorptionAmount += added`                       | unchanged synthetic property (`method_6067` / `method_6073`)       |
 * | `attacker.heal(healing)`                                   | unchanged (`method_6025`)                                          |
 * | `p.world.isRemote`                                         | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)              |
 * | `Clone.entityPlayer` (the NEW player)                      | `Clone.player` (the port keeps Forge's direction: player = new)     |
 * | `EnchantHealingBlade` (Enchantment object)                 | `GeneratedEnchantments.HEALING_BLADE` (RegistryKey)                    |
 *
 * The three state maps (`absorptionCounter`, `absorptionAliveTimer`, `healingPool`), the
 * `20 * level` absorption expiry, the `0.05f`-per-hit pool growth / per-tick decay and the
 * `e.amount += healing` self-feedback are all preserved verbatim.
 */
@ModEnchantment(
    id = "healing_blade",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.SWORD,
    maxLevel = 8,
    weight = 1,
    anvilCost = 12,
    minCostBase = 33,
    minCostPerLevel = 8,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 1.0,
    order = 43,
)
object EnchantHealingBladeHandler : Listenable {

    private val absorptionCounter = mutableMapOf<UUID, Float>()
    private val absorptionAliveTimer = mutableMapOf<UUID, Int>()
    private val healingPool = mutableMapOf<UUID, Float>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val id = attacker.uuid

        val level = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.HEALING_BLADE)
        if (level <= 0) return

        val dmg = e.amount
        val healing = dmg * (0.1f * level) + (e.target.health * 0.0125f * level).coerceAtMost(attacker.health) + (healingPool[id] ?: 0f)
        if (healing <= 0f) return

        healingPool[id] = (healingPool[id] ?: 0f) + healing * 0.05f

        val diff = attacker.maxHealth - attacker.health
        if (healing <= diff) {
            attacker.heal(healing)
        } else {
            attacker.heal(diff)
            val maxAbsorption = attacker.maxHealth * (0.2f + level * 0.1f)
            val capacity = maxAbsorption - (absorptionCounter[id] ?: 0f)
            if (capacity > 0f) {
                val added = (healing - diff).coerceAtMost(capacity)
                attacker.absorptionAmount += added
                absorptionCounter[id] = (absorptionCounter[id] ?: 0f) + added
                absorptionAliveTimer[id] = 20 * level
            }
        }

        e.amount += healing
        STLog.log("HealingBlade") {
            "attacker=${attacker.name.string}, target=${e.target.name.string}, lvl=$level, damage=$dmg, " +
                "healing=$healing, health=${attacker.health}/${attacker.maxHealth}, " +
                "absorption=${attacker.absorptionAmount}, healedAmount=${healing.coerceAtMost(diff)}, " +
                "pool=${healingPool[id]}, outgoingDamage=${e.amount}"
        }
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val id = p.uuid

        val pool = healingPool[id]
        if (pool != null) {
            val next = pool - pool * 0.05f
            if (next > 0.01f) healingPool[id] = next else healingPool.remove(id)
        }

        val tracked = absorptionCounter[id] ?: return
        val current = p.absorptionAmount

        if (current < tracked) {
            if (current <= 0f) {
                absorptionCounter.remove(id)
                absorptionAliveTimer.remove(id)
            } else {
                absorptionCounter[id] = current
            }
            return
        }

        val timer = absorptionAliveTimer[id] ?: run {
            absorptionCounter.remove(id)
            return
        }

        val remaining = timer - 1
        if (remaining <= 0) {
            p.absorptionAmount = (current - tracked).coerceAtLeast(0f)
            absorptionCounter.remove(id)
            absorptionAliveTimer.remove(id)
        } else {
            absorptionAliveTimer[id] = remaining
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uuid
        absorptionCounter.remove(id)
        absorptionAliveTimer.remove(id)
        healingPool.remove(id)
        clearContribution(e.player)
    }


    @SubscribeEvent
    fun onLogin(e: PlayerEvent.PlayerLoggedInEvent) {
        clearContribution(e.player)
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        clearContribution(e.original)
        clearContribution(e.player)
    }

    private fun clearContribution(p: PlayerEntity) {
        val id = p.uuid
        val tracked = absorptionCounter[id] ?: 0f
        if (tracked > 0f) {
            p.absorptionAmount = (p.absorptionAmount - tracked).coerceAtLeast(0f)
        }
        absorptionCounter.remove(id)
        absorptionAliveTimer.remove(id)
        healingPool.remove(id)
    }
}
