package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.isElementDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * **Frostbite**（冰霜） — the ice third of the elemental trio, and the one that carries the
 * "配合火焰掌控" pair-up (`fire_master` is the fire one, `venom` the poison one,
 * `chain_lightning` the lightning one).
 *
 * ## The rule as implemented
 * | step | what happens |
 * |---|---|
 * | hit | +1 stack, Slowness refreshed at `min(stacks - 1, 4)` — one amplifier per stack, as specified |
 * | stack decay | 60 ticks without a hit resets the stacks to 0 (the Slowness instance expires on its own) |
 * | 5 stacks | **freeze**: 40 ticks (2 s) of `velocity = 0` + Slowness 6, stacks reset to 0 |
 * | while frozen | incoming damage `x (1 + 0.2 x level)`, using the level that *froze* the target ("碎冰") |
 *
 * ## Why the freeze is two mechanisms and not one
 * `velocity = 0` every tick is enough for a mob: mob movement is simulated by the server. It is *not*
 * enough for a player, whose position is client-authoritative — the client keeps walking and the
 * server accepts it. So the freeze also applies `SLOWNESS` at amplifier 6, which is the smallest
 * amplifier that provably reaches zero movement: bytecode of `StatusEffects` shows Slowness is a
 * single `GENERIC_MOVEMENT_SPEED` modifier of `-0.15` `ADD_MULTIPLIED_TOTAL` per level, i.e.
 * `1 - 0.15 x (amplifier + 1)` = `-1.05` at amplifier 6, and the attribute clamps at its own floor.
 *
 * `velocityModified = true` is set explicitly because the three-argument `Entity#setVelocity`
 * (verified in bytecode) only forwards to the `Vec3d` overload — it does **not** flag the change for
 * the client, so without it the client keeps predicting the old motion.
 *
 * ## Deliberately not done: blocking jumps
 * A frozen player can still jump straight up (movement speed does not gate the jump impulse).
 * Blocking that needs a negative `generic.jump_strength` modifier whose removal deadline lives in
 * memory — the exact shape that leaked a permanent attack-speed suppression on
 * `heavenly_punishment` before that was fixed. A two-second cosmetic gap is not worth re-opening
 * that class of bug; blocking it properly needs a persistent expiry, not this file.
 *
 * ## Guards
 *  - `isElementDamage()`: without it, the trio's own poison/arc damage would add frost stacks (and
 *    the arc would be able to freeze the target it just chained *to*).
 *  - `attacker === target`: self-inflicted damage must not build stacks.
 */
@ModEnchantment(
    id = "frostbite",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.RARE,
    anvilCost = 6,
    minCostBase = 24,
    minCostPerLevel = 4,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.0,
    order = 53,
)
object EnchantFrostbiteHandler : Listenable {
    private const val STACK_WINDOW_TICKS = 60L

    private const val FREEZE_TICKS = 40

    private const val MAX_STACK_AMPLIFIER = 4

    private const val FROZEN_AMPLIFIER = 6

    private const val SHATTER_PER_LEVEL = 0.2f

    private class FrostState(val ref: WeakReference<LivingEntity>) {
        var stacks: Int = 0

        /** World-time ticket after which the stacks lapse. */
        var stacksUntil: Long = 0L

        /** World-time ticket the freeze ends at; `0` means "not frozen". */
        var freezeUntil: Long = 0L

        /** Level of the wearer that applied the freeze — the "碎冰" bonus uses this, not the hitter's. */
        var freezeLevel: Int = 0
    }

    private val states = mutableMapOf<UUID, FrostState>()

    @SubscribeEvent
    fun onHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isElementDamage()) return

        val target = e.entityLiving ?: return
        val now = target.world.time

        // ---- "碎冰": any damage landing on a frozen target is amplified ------------------------
        // Looked up without creating state: an entity that never met frostbite must not get an entry.
        val existing = states[target.uuid]
        if (existing != null && existing.ref.get() !== target) {
            states.remove(target.uuid)
        } else if (existing != null && existing.freezeUntil > now) {
            val before = e.amount
            e.amount *= 1f + SHATTER_PER_LEVEL * existing.freezeLevel
            FSTLog.log("Frostbite") {
                "target=${target.name.string}, freezeLevel=${existing.freezeLevel}, " +
                    "damage=$before->${e.amount}, outcome=shatter-bonus"
            }
        }

        // ---- build a stack -------------------------------------------------------------------
        val attacker = e.attacker as? PlayerEntity ?: return
        if (attacker === target) return

        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.FROSTBITE)
        if (lvl <= 0) return

        val state = stateOf(target)
        state.stacks += 1
        state.stacksUntil = now + STACK_WINDOW_TICKS

        if (state.stacks >= 10) {
            freeze(target, state, lvl, now)
        } else {
            applyStackSlowness(target, state.stacks)
            spawnStackFx(target, state.stacks)
            FSTLog.log("Frostbite") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "stacks=${state.stacks}/$10, slownessAmplifier=${stackAmplifier(state.stacks)}, " +
                    "outcome=frost-stack"
            }
        }
    }

    /**
     * Per-entity tick: holds a frozen entity in place and retires expired state.
     *
     * `LivingEvent.LivingUpdateEvent` (not `PlayerTickEvent`) because the target of a frost stack is
     * normally a **mob** — a player-tick seam could never reach it, which is the trap the original
     * venom sketch in the task description fell into.
     */
    @SubscribeEvent
    fun onLivingUpdate(e: LivingEvent.LivingUpdateEvent) {
        val entity = e.entityLiving
        if (WorldSide.isClient(entity.world)) return

        val state = states[entity.uuid] ?: return
        if (state.ref.get() !== entity || entity.isRemoved || !entity.isAlive) {
            states.remove(entity.uuid)
            return
        }

        val now = entity.world.time

        if (state.freezeUntil > now) {
            entity.setVelocity(0.0, 0.0, 0.0)
            // The 3-arg overload does not set this flag (bytecode-verified); without it the client
            // keeps predicting the pre-freeze motion.
            entity.velocityModified = true
        } else if (state.freezeUntil > 0L) {
            state.freezeUntil = 0L
            state.freezeLevel = 0
            FSTLog.log("Frostbite") {
                "entity=${entity.name.string}, now=$now, outcome=freeze-expired"
            }
        }

        if (state.stacks > 0 && now > state.stacksUntil) {
            state.stacks = 0
            FSTLog.log("Frostbite") {
                "entity=${entity.name.string}, now=$now, outcome=stacks-lapsed"
            }
        }

        if (state.stacks == 0 && state.freezeUntil == 0L) {
            states.remove(entity.uuid)
        }
    }

    private fun stateOf(target: LivingEntity): FrostState {
        val existing = states[target.uuid]
        if (existing != null && existing.ref.get() === target) return existing
        return FrostState(WeakReference(target)).also { states[target.uuid] = it }
    }

    private fun stackAmplifier(stacks: Int): Int = (stacks - 1).coerceIn(0, MAX_STACK_AMPLIFIER)

    private fun applyStackSlowness(target: LivingEntity, stacks: Int) {
        // A weaker instance cannot replace a stronger one (StatusEffectInstance#upgrade), which is
        // what keeps the freeze's Slowness 6 intact for its full duration.
        target.addStatusEffect(
            StatusEffectInstance(StatusEffects.SLOWNESS, STACK_WINDOW_TICKS.toInt(), stackAmplifier(stacks), false, false, true)
        )
    }

    private fun freeze(target: LivingEntity, state: FrostState, lvl: Int, now: Long) {
        state.stacks = 0
        state.freezeUntil = now + FREEZE_TICKS
        state.freezeLevel = lvl

        target.addStatusEffect(
            StatusEffectInstance(StatusEffects.SLOWNESS, FREEZE_TICKS, FROZEN_AMPLIFIER, false, false, true)
        )
        // Immediate, so the target is already stopped on the tick the freeze starts.
        target.setVelocity(0.0, 0.0, 0.0)
        target.velocityModified = true

        val world = target.world as? ServerWorld
        if (world != null) {
            world.spawnParticles(
                ParticleTypes.SNOWFLAKE,
                target.x, target.y + target.height * 0.5, target.z,
                30, 0.4, 0.6, 0.4, 0.02,
            )
            world.playSound(
                null, target.x, target.y, target.z,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.6f,
            )
        }

        FSTLog.log("Frostbite") {
            "target=${target.name.string}, lvl=$lvl, freezeTicks=$FREEZE_TICKS, freezeUntil=${state.freezeUntil}, " +
                "shatterBonus=${SHATTER_PER_LEVEL * lvl}, outcome=frozen"
        }
    }

    private fun spawnStackFx(target: LivingEntity, stacks: Int) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(
            ParticleTypes.SNOWFLAKE,
            target.x, target.y + target.height * 0.6, target.z,
            2 + stacks, 0.3, 0.4, 0.3, 0.0,
        )
    }
}
