package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
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
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * 盾击 — a successful shield block knocks the melee attacker back and hurts it.
 *
 * ## The gap this fills
 * The mod had **no shield enchantment at all** (`echo_shield` is MYTHIC reflection), and vanilla shields
 * cannot be enchanted with anything but Unbreaking/Mending. This is the "protect and punish" slot.
 *
 * ## Why `LivingDamageEvent`, not `LivingHurtEvent`
 * At `LivingHurtEvent` the hit has not been reduced yet, so "was it blocked?" and "how much got
 * through?" are both unknown. `LivingDamageEvent` fires after armour / shield / enchantment reduction,
 * immediately before health is subtracted — which is exactly the moment a block has *succeeded*, and
 * `player.isBlocking` is still true there (vanilla clears the block later, when the shield is disabled).
 *
 * ## Why the damage is `element_physical`
 * The eight `element_*` types are in `#minecraft:bypasses_armor` and are flagged auxiliary, so
 * `enqueueElemental` gives true damage that cannot re-enter the on-hit stack (`AGENTS.md`: riders must
 * never recursively fire the handler list). It also keeps this enchantment out of the crit / combo
 * multipliers — bash damage is `2 + 2 × level`, never a scaled copy of the swing.
 *
 * ## Scope
 * Melee only: `source.source === attacker` excludes arrows and thrown projectiles (the archer should
 * not be punched for a blocked arrow), and `isAuxiliaryDamage` keeps our own riders from bashing.
 */
@ModEnchantment(
    id = "shield_bash",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.HELD,
    maxLevel = 3,
    anvilCost = 5,
    minCostBase = 26,
    supportedItems = EnchantSupportedItemTags.SHIELD,
    slots = [EnchantSlot.OFFHAND],
)
object EnchantShieldBashHandler : Listenable {

    /** Flat damage per level, plus [BASH_BASE]. III = 8. */
    const val BASH_PER_LEVEL = 2f
    const val BASH_BASE = 2f

    /** Knockback strength; vanilla's own shield-disable push uses a comparable magnitude. */
    const val KNOCKBACK = 0.6

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        if (!player.isBlocking) return

        val attacker = e.source.attacker as? LivingEntity ?: return
        if (attacker === player) return
        // Melee only: the *source* entity must be the attacker itself, not an arrow it fired.
        if (e.source.source !== attacker) return

        val shield = player.activeItem
        if (shield.isEmpty) return
        val level = getItemSpecificEnchantLevel(shield, GeneratedEnchantments.SHIELD_BASH)
        if (level <= 0) return

        // Push the attacker away from the player. `takeKnockback` takes (strength, x, z) and normalises
        // (x, z) itself, so the vector only needs the right direction.
        attacker.takeKnockback(KNOCKBACK, player.x - attacker.x, player.z - attacker.z)

        val damage = BASH_BASE + BASH_PER_LEVEL * level
        ElementCombat.enqueueElemental(player, attacker, ElementType.PHYSICAL, damage)

        FSTLog.log("ShieldBash") {
            "player=${player.name.string}, attacker=${attacker.type.name.string}, lvl=$level, " +
                "blocked=${e.amount}, bash=$damage, outcome=bashed"
        }
    }
}
