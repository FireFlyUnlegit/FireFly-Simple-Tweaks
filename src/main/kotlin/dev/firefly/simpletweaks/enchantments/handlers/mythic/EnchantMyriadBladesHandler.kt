package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * 千刃 — a hit sometimes spawns two **phantom strikes** at a fraction of the damage that landed.
 *
 * ## Why `LivingDamageEvent` and not `LivingHurtEvent` (`build=cleanup78`)
 * The first version read `LivingHurtEvent.amount`, which is **pre-armour**, and then dealt the phantoms as
 * `element_physical` — a type in `#minecraft:bypasses_armor`. The measured result was "奇怪的高伤害":
 * a raw 107 hit (armour reduces it to ~40) produced two phantoms of `26.75` each, landing unmitigated, so
 * the proc was worth **~100 % of the damage actually dealt** rather than the intended 50 % of the swing.
 *
 * `LivingDamageEvent` fires *after* armour / enchantment reduction and immediately before health is
 * subtracted, so `e.amount` there is the number that really lands. 25 % of that is 25 % of the real hit,
 * and because the phantom itself still bypasses armour the two roughly cancel out: the phantom is a
 * faithful second chop, not a mitigated one.
 *
 * A second benefit: a hit that vanilla discards through the i-frame gate never reaches `applyDamage`, so
 * phantoms can no longer be spawned by a swing that did nothing.
 *
 * ## Why the phantoms are queued instead of dealt inline
 * Calling `LivingEntity#damage` from inside a damage handler re-enters the handler list and discards the
 * outer handler's mutations (`ElementMarks`'s KDoc records the measured "20 HP mob took four hits"
 * symptom). [ElementCombat.enqueueElemental] queues the hit for the world tick, and the queue marks it
 * **auxiliary**, so phantoms cannot re-trigger `myriad_blades`, crit, combo, `execute` or anything else
 * on-hit. They are damage and nothing else.
 *
 * ## Why the level is clamped to [MAX_LEVEL]
 * `/fst enchant <target> myriad_blades 10` is possible on a debug item (the author's test sword carries
 * every enchantment at 10). Unclamped, that made `chance = 0.20 + 0.10 × 10 = 1.2 → 100 %` — every swing
 * procced — and inflated the log's `lvl=10`. The enchantment declares `max_level: 3`, so the numbers it
 * scales are read at most at 3; anything above is out of spec and must not silently buff the effect.
 */
@ModEnchantment(
    id = "myriad_blades",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 40,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantMyriadBladesHandler : Listenable {

    /** The declared `max_level`, used to clamp an out-of-range `/fst enchant` level. */
    const val MAX_LEVEL = 3

    /** Chance, before the per-level part. III = 50 %. */
    const val BASE_CHANCE = 0.20f
    const val CHANCE_PER_LEVEL = 0.10f

    /** Phantom strikes per proc. */
    const val PHANTOMS = 2

    /** Each phantom deals this fraction of the damage that actually lands. */
    const val PHANTOM_SHARE = 0.25f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (player === target) return

        val rawLevel = getItemSpecificEnchantLevel(
            player.combatStack(e.source),
            GeneratedEnchantments.MYRIAD_BLADES,
        )
        val level = rawLevel.coerceAtMost(MAX_LEVEL)
        if (level <= 0) return

        val chance = (BASE_CHANCE + CHANCE_PER_LEVEL * level).coerceAtMost(1f)
        if (target.world.random.nextFloat() >= chance) return

        val perPhantom = e.amount * PHANTOM_SHARE
        if (perPhantom <= 0f || !perPhantom.isFinite()) return

        repeat(PHANTOMS) {
            ElementCombat.enqueueElemental(player, target, ElementType.PHYSICAL, perPhantom)
        }

        FSTLog.log("MyriadBlades") {
            "attacker=${player.name.string}, target=${target.type.name.string}, " +
                "rawLevel=$rawLevel, lvl=$level, landed=${"%.2f".format(e.amount)}, " +
                "chance=$chance, phantoms=$PHANTOMS, each=${"%.2f".format(perPhantom)}, " +
                "outcome=phantoms"
        }
    }
}
