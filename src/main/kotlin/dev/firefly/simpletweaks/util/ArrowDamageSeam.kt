package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The **single** seam for bow enchantments that change an arrow's damage.
 *
 * <h2>Why one seam, and why this event</h2>
 * There are **two** different code paths that make an arrow hurt somebody, and a hook placed in only
 * one of them silently misses the other:
 *
 * | path | who deals the damage |
 * |---|---|
 * | ordinary hit | vanilla `PersistentProjectileEntity#onEntityHit` → `target.damage(source, i)` |
 * | a hit that `piercing_arrow` cancels | `PersistentProjectileEntityPiercingMixin` cancels `onEntityHit` and [dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantPiercingArrowHandler] re-applies the damage through `runPlayerAttack(..., source = arrow.damageSources.arrow(arrow, shooter))` |
 *
 * A damage modifier patched into `onEntityHit` (a `@ModifyVariable` on the computed amount, say) is
 * therefore **dead for every pierced hit** — the method returns before that code runs. Both paths do
 * end in `LivingEntity#damage`, i.e. this project's [LivingHurtEvent] (fired at the head of
 * `Entity#damage` by `EntityDamageMixin`), and both pass a source whose `source` field is the arrow.
 * That makes `LivingHurtEvent` the only place where **every** arrow hit is reachable exactly once.
 *
 * <p>This is the "统一接口" the author asked for: an enchantment does **not** hook the projectile; it
 * registers a [Modifier] and this object applies the product of all registered modifiers.
 *
 * <h2>Registration order (deliberate)</h2>
 * [dev.firefly.simpletweaks.SimpleTweaks.onInitialize] registers this seam **before**
 * `EnchantmentManager.initHandlers()`. Same-priority listeners dispatch in registration order, so
 * within `NORMAL` this seam runs **first**: derived effects that read `e.amount` (e.g. `starfall`'s
 * `1.5 + 0.5 × level` multiplier on the hit that spawned its stars) see the **boosted** arrow damage.
 * Handlers at `LOW` (`hunters_mark`, `echo_shot`) also see it, which is what they should scale from.
 *
 * <h2>What is *not* scaled</h2>
 * Only the arrow's own hit. The follow-up damage some enchantments deal (echo, stars, chain arcs) is
 * separate damage with its own source, and is deliberately not multiplied again — otherwise a
 * distance bonus would compound into every derived effect.
 */
object ArrowDamageSeam : Listenable {

    /**
     * One enchantment's contribution. Implementations must be **pure and cheap**: they run on every
     * arrow hit, on the server thread.
     */
    fun interface Modifier {
        /**
         * @param bow the stack that fired the arrow (vanilla's `weapon` field; see [firingWeapon])
         * @param arrow the arrow that is about to hurt somebody
         * @return damage multiplier, `1.0` for "no contribution"
         */
        fun multiplier(bow: ItemStack, arrow: PersistentProjectileEntity): Float
    }

    private val modifiers = CopyOnWriteArrayList<Modifier>()

    /** Called from an enchantment handler's `init()`; the handler keeps owning its own formula. */
    fun register(modifier: Modifier) {
        modifiers.add(modifier)
    }

    /** Product of every registered modifier — `1.0` when nothing applies. */
    fun multiplier(bow: ItemStack, arrow: PersistentProjectileEntity): Float {
        var total = 1.0f
        for (modifier in modifiers) {
            total *= modifier.multiplier(bow, arrow)
        }
        return total
    }

    @SubscribeEvent(priority = 0)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (modifiers.isEmpty()) return
        // `source.source` is the arrow for both paths — see the class KDoc.
        val arrow = e.source.source as? PersistentProjectileEntity ?: return
        val shooter = arrow.owner as? LivingEntity ?: return

        val bow = arrow.firingWeapon(shooter)
        val multiplier = multiplier(bow, arrow)
        if (multiplier == 1.0f) return

        val before = e.amount
        e.amount *= multiplier

        FSTLog.log("ArrowDamage") {
            "distance=${"%.2f".format(arrow.distanceTraveled)}, multiplier=${"%.3f".format(multiplier)}, " +
                "amount=${"%.3f".format(before)}->${"%.3f".format(e.amount)}, " +
                "bow=${bow.item}, outcome=scaled"
        }
    }
}
