package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * 千刃 — a hit sometimes spawns two **phantom strikes** at a fraction of the damage.
 *
 * ## Why the phantoms are elemental (`element_physical`) hits
 * The only damage path this mod can use from inside `LivingHurtEvent` is
 * [ElementCombat.enqueueElemental] — queuing is mandatory, because calling `LivingEntity#damage` here
 * re-enters the handler list and discards the outer handler's mutations (`ElementMarks`'s KDoc records
 * the measured 20 HP / four-hits symptom). Every queued hit carries the **auxiliary** flag, so the
 * phantoms cannot re-trigger `myriad_blades`, crit, combo or any other on-hit effect: they are damage
 * and nothing else. `element_physical` is the closest type to "a second sword swing".
 *
 * ## Why 25 % twice and not 35 %
 * Three phantom hits plus the two the author originally sketched would have been `+105 %` at a 50 %
 * chance — a stealth doubling of every swing. `2 × 25 % = +50 %` per proc at III (`50 %` chance) is
 * `+25 %` average, which is where a MYTHIC offensive enchantment belongs without competing with
 * `heavenly_punishment`'s finishing blow.
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

    /** Chance, before the per-level part. III = 50 %. */
    const val BASE_CHANCE = 0.20f
    const val CHANCE_PER_LEVEL = 0.10f

    /** Phantom strikes per proc. */
    const val PHANTOMS = 2

    /** Each phantom deals this fraction of the triggering hit. */
    const val PHANTOM_SHARE = 0.25f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (player === target) return

        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.MYRIAD_BLADES)
        if (level <= 0) return

        val chance = (BASE_CHANCE + CHANCE_PER_LEVEL * level).coerceAtMost(1f)
        if (target.world.random.nextFloat() >= chance) return

        val perPhantom = e.amount * PHANTOM_SHARE
        if (perPhantom <= 0f || !perPhantom.isFinite()) return

        repeat(PHANTOMS) {
            ElementCombat.enqueueElemental(player, target, ElementType.PHYSICAL, perPhantom)
        }

        FSTLog.log("MyriadBlades") {
            "attacker=${player.name.string}, target=${target.type.name.string}, lvl=$level, " +
                "chance=$chance, phantoms=$PHANTOMS, each=${"%.2f".format(perPhantom)}, " +
                "outcome=phantoms"
        }
    }
}
