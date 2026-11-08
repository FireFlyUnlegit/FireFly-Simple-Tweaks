package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap

/**
 * 反制 — after being hit in melee, the next swing deals extra damage.
 *
 * ## Why "next swing" and not a damage-over-time buff
 * The fantasy is a riposte: you eat one hit and answer immediately. A timed buff would be a passive
 * damage increase that happens to be refreshed by being hit; consuming the charge on the next hit keeps
 * it a decision ("trade now, or hold it").
 *
 * ## Why `WeakHashMap` and a tick deadline
 * The state must not outlive the player (`AGENTS.md` §4.11's family of leaks), and the deadline is what
 * makes "3 seconds after being hit" true regardless of how many ticks the player was idle. `WeakHashMap`
 * is keyed by the entity, so a disconnected player's entry disappears with the object — no logout hook
 * needed, and none of the UUID-map growth the audit flagged elsewhere.
 *
 * ## Why it is applied *last* in the handler order
 * `order` is omitted, i.e. `Int.MAX_VALUE`: this multiplier runs after the crit / combo / true-damage
 * chain, so `+15 % × level` is a readable fraction of the **final** hit rather than a seed that every
 * later multiplier amplifies. That is the same lesson as `execute`'s health-pool nuke.
 */
@ModEnchantment(
    id = "riposte",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.WEAPON,
    maxLevel = 4,
    anvilCost = 5,
    minCostBase = 26,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantRiposteHandler : Listenable {

    /** Damage multiplier added per level. IV = +60 %. */
    const val BONUS_PER_LEVEL = 0.15f

    /** How long the charge stays available after being hit, in ticks (3 s). */
    const val WINDOW_TICKS = 60L

    /** Player → tick at which its riposte charge expires. */
    private val CHARGED_UNTIL = WeakHashMap<PlayerEntity, Long>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val target = e.entityLiving
        val attacker = e.attacker

        // Arm the charge: a player was hit in melee (a projectile's source is not the attacker itself).
        if (target is PlayerEntity && attacker != null && attacker !== target &&
            e.source.source === attacker && target.isAlive
        ) {
            if (hasRiposte(target)) {
                CHARGED_UNTIL[target] = target.world.time + WINDOW_TICKS
                FSTLog.log("Riposte") {
                    "player=${target.name.string}, by=${attacker.type.name.string}, outcome=charged"
                }
            }
            return
        }

        // Spend the charge: this player is the one attacking.
        val player = attacker as? PlayerEntity ?: return
        if (e.entityLiving === player) return
        val expiry = CHARGED_UNTIL[player] ?: return
        val now = player.world.time
        if (now > expiry) {
            CHARGED_UNTIL.remove(player)
            FSTLog.log("Riposte") {
                "player=${player.name.string}, since=${now - expiry}, outcome=expired"
            }
            return
        }

        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.RIPOSTE)
        if (level <= 0) return

        val before = e.amount
        e.amount *= 1f + BONUS_PER_LEVEL * level
        CHARGED_UNTIL.remove(player)

        FSTLog.log("Riposte") {
            "player=${player.name.string}, target=${e.entityLiving.type.name.string}, lvl=$level, " +
                "damage=$before->${e.amount}, outcome=riposted"
        }
    }

    private fun hasRiposte(player: PlayerEntity): Boolean =
        getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.RIPOSTE) > 0

    /** Exposed for the game test, which cannot reach the private map. */
    @JvmStatic
    fun isCharged(player: LivingEntity): Boolean = CHARGED_UNTIL.containsKey(player)
}
