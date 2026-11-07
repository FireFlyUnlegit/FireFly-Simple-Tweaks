package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import java.util.WeakHashMap

/**
 * 虚空坍缩 — stack 「坍缩」 on a target, then collapse it for **percentage-of-max-health true damage**.
 *
 * ## Why this is MYSTERY tier
 * The two existing MYSTERY enchantments are defined by magnitude, not by cleverness: `heavenly_punishment`
 * re-deals `damage × level` as lightning, `celestial_blessing` heals `18% × level` of the damage it deals and
 * shreds the target's max health. This one follows the same rule with a different axis:
 *
 * - **`min(maxHealth × (5% + 5% × level), accumulated × 2)`** — at III that is up to **20% of the target's
 *   maximum health**, which is the only damage in the mod that does not care how thick the target's armour
 *   is. That matters specifically because of the armour curve: a Lv46+ mob carries enough armour to turn its
 *   flat reduction into a wall, and a percentage hit walks straight through it.
 * - **AoE**: every other enemy within `3 + level` blocks takes 50% of that.
 * - **Per-stack amplification**: each stack makes the target take `12% × level` more from *you*, so the
 *   stacks are not just a counter — they are the ramp.
 *
 * ## Why the `min(…, accumulated × 2)` clamp exists
 * Pure percentage would mean "20 hits, any boss dies", which trivialises every high-health mob the level
 * system produces. Clamping by `accumulated × 2` ties the payoff to damage you actually dealt, so the
 * enchantment is strong against tanky targets and not an exploit against a damage-sponge boss.
 *
 * ## Pace: a 1 s safety window, not a cooldown
 * The real gate is the 5-stack requirement, and a collapse clears the stacks itself. The window only
 * prevents two collapses from resolving inside the same damage tick.
 *
 * ## Deliberate pacing limit (not a downside)
 * One collapse per target per `10 s`. Without it, a 5-hit collapse loop on a high-attack-speed build would
 * print the same 20% every second. The stacks themselves still fall off after 6 s of not hitting that target,
 * so the limit only affects sustained single-target fights.
 */
@ModEnchantment(
    id = "void_collapse",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 90,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 3.0,
    order = 52,
)
object EnchantVoidCollapseHandler : Listenable {

    private const val NBT_TARGET = "fst_vc_target"
    private const val NBT_STACKS = "fst_vc_stacks"
    private const val NBT_LAST_HIT = "fst_vc_last_hit"
    private const val NBT_LAST_COLLAPSE = "fst_vc_last_collapse"
    private const val NBT_ACCUM = "fst_vc_accum"

    /** Stacks needed before it collapses. */
    private const val MAX_STACKS = 5

    /** Stacks die after 6 s without hitting the same target. */
    private const val STACK_TIMEOUT = 120L

    /**
     * One collapse per target per **1 s**.
     *
     * This is not a pacing limit any more — the 5-stack requirement is the real gate (and stacks are
     * cleared by the collapse itself, so a second one needs five fresh hits). The window only exists to
     * stop two collapses landing inside the same damage resolution, which AoE/multi-hit sources could
     * otherwise cause.
     */
    private const val COLLAPSE_COOLDOWN = 20L

    /** Per-stack damage amplification: 12% × level. */
    private const val AMP_PER_STACK = 0.12f

    private val data = WeakHashMap<PlayerEntity, NbtCompound>()

    private fun stats(player: PlayerEntity): NbtCompound =
        data.getOrPut(player) { NbtCompound() }

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        val level = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.VOID_COLLAPSE)
        if (level <= 0) return

        val now = target.world.time
        val nbt = stats(attacker)
        val targetId = target.uuid.toString()

        // Stacks are per target: switching targets throws the progress away, which is what keeps this a
        // single-target finisher rather than a passive damage buff in a crowd.
        // The streak (stacks **and** accumulated damage) is one thing: if it times out, both reset. Clearing
        // only the stacks used to leave `accumulated` growing forever, which silently disabled the cap on the
        // collapse — see DEVLOG `build=cleanup39`.
        val sameTarget = nbt.getString(NBT_TARGET) == targetId
        val timedOut = sameTarget && now - nbt.getLong(NBT_LAST_HIT) > STACK_TIMEOUT
        val streakAlive = sameTarget && !timedOut

        var stacks = if (streakAlive) nbt.getInt(NBT_STACKS) else 0

        // Ramp first, then stack: the hit that adds the 5th stack does not also get its own bonus.
        if (stacks > 0) {
            val amp = 1f + AMP_PER_STACK * level * stacks
            e.amount *= amp
        }

        var accumulated = if (streakAlive) nbt.getFloat(NBT_ACCUM) else 0f
        accumulated += e.amount
        stacks = (stacks + 1).coerceAtMost(MAX_STACKS)

        nbt.putString(NBT_TARGET, targetId)
        nbt.putInt(NBT_STACKS, stacks)
        nbt.putLong(NBT_LAST_HIT, now)
        nbt.putFloat(NBT_ACCUM, accumulated)

        if (stacks < MAX_STACKS) return
        if (now - nbt.getLong(NBT_LAST_COLLAPSE) < COLLAPSE_COOLDOWN) return

        nbt.putLong(NBT_LAST_COLLAPSE, now)
        nbt.putInt(NBT_STACKS, 0)
        nbt.putFloat(NBT_ACCUM, 0f)

        collapse(attacker, target, level, accumulated)
    }

    private fun collapse(attacker: PlayerEntity, target: LivingEntity, level: Int, accumulated: Float) {
        val percent = 0.05f + 0.05f * level
        val byHealth = target.maxHealth * percent
        val amount = minOf(byHealth, accumulated * 2f)
        if (amount <= 0f) return

        val applied = target.damageElemental(attacker, ElementType.PHYSICAL.damageType, amount)

        val radius = 3.0 + level
        val box = target.boundingBox.expand(radius)
        var splashed = 0
        for (other in target.world.getEntitiesByClass(LivingEntity::class.java, box) {
            it !== target && it !== attacker && it.isAlive && it !is PlayerEntity
        }) {
            other.damageElemental(attacker, ElementType.PHYSICAL.damageType, amount * 0.5f)
            splashed++
        }

        FSTLog.log("VoidCollapse") {
            val cap = if (byHealth <= accumulated * 2f) "health" else "accumulated"
            "target=${target.type.name.string}, level=$level, byHealth=${"%.2f".format(byHealth)}, " +
                "accumulated=${"%.2f".format(accumulated)}, amount=${"%.2f".format(amount)}, cap=$cap, " +
                "applied=$applied, splashed=$splashed, outcome=collapsed"
        }
    }
}
