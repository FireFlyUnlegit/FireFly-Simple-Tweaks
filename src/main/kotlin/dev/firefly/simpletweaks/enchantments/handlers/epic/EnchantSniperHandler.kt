package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.ArrowDamageSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import kotlin.math.min

/**
 * `sniper` — the farther the arrow has flown, the harder it hits (`build=cleanup27`).
 *
 * <h2>Formula</h2>
 * ```
 * distance = min(arrow.distanceTraveled, 40.0)          // blocks travelled, capped
 * multiplier = 1 + 0.005 × level × distance             // +0.5% per block per level
 * ```
 * | level | 0 blocks | 10 blocks | 20 blocks | 30 blocks | 40+ blocks |
 * |---|---|---|---|---|---|
 * | I   | ×1.00 | ×1.05 | ×1.10 | ×1.15 | ×1.20 |
 * | III | ×1.00 | ×1.15 | ×1.30 | ×1.45 | ×1.60 |
 * | V   | ×1.00 | ×1.25 | ×1.50 | ×1.75 | ×2.00 |
 *
 * Both constants are private, named and in one place ([MAX_BLOCKS], [PER_BLOCK_PER_LEVEL]) so the
 * balance curve can be changed without hunting through call sites. The cap exists on purpose: a
 * linear-per-block bonus would make a 200-block sky shot an instant kill.
 *
 * <h2>Where the damage is applied — and why not here</h2>
 * This handler deliberately **does not** listen to `LivingHurtEvent` itself. It registers an
 * [ArrowDamageSeam.Modifier], because a handler that patched the projectile's hit method would be
 * silently skipped whenever `piercing_arrow` cancels vanilla's `onEntityHit` and re-applies the damage
 * through `runPlayerAttack`. See [ArrowDamageSeam]'s KDoc for the two paths and why the damage event
 * is the only common point.
 *
 * <h2>Distance source</h2>
 * `Entity#distanceTraveled` is a public float that vanilla itself accumulates while the entity moves,
 * so it is exactly "how far this arrow has flown" — no bookkeeping of our own, and it keeps working
 * through a piercing volley (`arrow` object survives) and for every subsequent target.
 *
 * <h2>Why it still works on an off-hand bow</h2>
 * The seam reads the stack that fired the arrow (`PersistentProjectileEntity#getWeaponStack`), not the
 * shooter's main hand — the `build=cleanup27` fix. A bow in the off-hand therefore gets the same
 * treatment, and switching items mid-flight cannot drop the bonus.
 */
@ModEnchantment(
    id = "sniper",
    category = EnchantCategory.EPIC,
    type = EnchantType.BOW,
    maxLevel = 5,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 30,
    minCostPerLevel = 12,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantSniperHandler : Listenable {

    /** Distance (in blocks) beyond which the bonus stops growing. */
    private const val MAX_BLOCKS = 40.0f

    /** Damage bonus per block of flight, per enchantment level (`0.005` = +0.5%). */
    private const val PER_BLOCK_PER_LEVEL = 0.005f

    override fun init() {
        // The seam owns the event; this object owns the formula. Keeping the two apart is what lets a
        // second distance/damage enchantment compose with this one instead of fighting it.
        ArrowDamageSeam.register { bow, arrow -> multiplier(bow, arrow) }
    }

    /** Damage multiplier for [arrow] fired from [bow]; `1.0` when the bow lacks `sniper`. */
    fun multiplier(bow: ItemStack, arrow: PersistentProjectileEntity): Float {
        val level = getItemSpecificEnchantLevel(bow, GeneratedEnchantments.SNIPER)
        if (level <= 0) return 1.0f

        val distance = min(arrow.distanceTraveled, MAX_BLOCKS)
        return 1.0f + PER_BLOCK_PER_LEVEL * level * distance
    }
}
