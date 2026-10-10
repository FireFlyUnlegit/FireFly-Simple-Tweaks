package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import java.util.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantTrueDamageHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                     | 1.21.1                                                          |
 * |--------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`     | `compat.event.LivingHurtEvent`                                  |
 * | `net.minecraftforge...LivingDamageEvent`   | `compat.event.LivingDamageEvent`                                |
 * | `net.minecraftforge...gameevent.PlayerEvent` | `compat.event.PlayerEvent`                                    |
 * | `net.minecraftforge...EventPriority`       | `compat.event.EventPriority` (annotation keeps `priority = HIGHEST`) |
 * | `attacker.uniqueID`                        | `attacker.uuid` (`EntityLike.getUuid`, method_5667)             |
 * | `attacker.heldItemMainhand`                | `attacker.mainHandStack` (method_6047)                          |
 * | `EnchantTrueDamage` (Enchantment)          | `GeneratedEnchantments.TRUE_DAMAGE` (RegistryKey)                  |
 *
 * ⚠️ The only line that could not be copied token-for-token is the 1.12.2
 * `if (e.isCanceled && damagePool[e.attacker?.uniqueID] != null) damagePool[e.attacker?.uniqueID?: return] = 0f`
 * — `e.attacker` is this project's *Kotlin* extension returning `LivingEntity?`, so `?.uuid` typed as
 * `UUID?` cannot be used as a `MutableMap<UUID, Float>` key without a null check. The id is therefore
 * hoisted into a local and null-checked first; the control flow (`isCanceled` + entry present → zero
 * the entry) is identical.
 */
@ModEnchantment(
    id = "true_damage",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 7,
    weight = 4,
    anvilCost = 8,
    minCostBase = 30,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.35,
    order = 29,
)
object EnchantTrueDamageHandler : Listenable {
    val damagePool = mutableMapOf<UUID, Float>()
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        val attacker = e.attacker?: return
        val aid = attacker.uuid
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.TRUE_DAMAGE)
        if (lvl > 0) {
            val addition = e.amount * 0.025f * (lvl + 1).coerceAtMost(8)
            val damageBefore = e.amount
            e.amount -= addition
            damagePool[aid] = (damagePool[aid]?: 0f) + addition
            STLog.log("TrueDamage") {
                "attacker=${attacker.name.string}, target=${e.entityLiving.name.string}, lvl=$lvl, " +
                    "reduced=$addition, damage=$damageBefore->${e.amount}, pooled=${damagePool[aid]}"
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onLivingDamage(e: LivingDamageEvent) {
        val canceledAid = e.attacker?.uuid
        if (e.isCanceled && canceledAid != null && damagePool[canceledAid] != null) damagePool[canceledAid] = 0f
        if (e.invalid) return
        val attacker = e.attacker?: return
        val aid = attacker.uuid
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.TRUE_DAMAGE)
        if (lvl > 0 && damagePool[aid] != null) {
            val pooled = damagePool[aid]?: 0f
            val damageBefore = e.amount
            e.amount += damagePool[aid]?: 0f
            damagePool.remove(aid)
            STLog.log("TrueDamage") {
                "attacker=${attacker.name.string}, target=${e.entityLiving.name.string}, lvl=$lvl, " +
                    "pooled=$pooled, damage=$damageBefore->${e.amount}, outcome=payout"
            }
        }
    }
    @SubscribeEvent
    fun onPlayerLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val eid = e.player.uuid
        damagePool.remove(eid)
    }
}
