package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingFallEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items

/**
 * 天坠 — the mace's ground slam becomes a real shockwave, and the fall that caused it costs nothing.
 *
 * ## Why the mace needed its own family
 * 1.21's mace is the only weapon class this mod had not touched. Its damage already scales with fall
 * distance (vanilla `smash` bonus), so the interesting axis is not "more damage" but "the slam hits a
 * radius" — and the fall damage that makes the mace risky is exactly what a fantasy version of it should
 * remove.
 *
 * ## Two halves
 * - **Shockwave**: on a hit that carries fall distance (`player.fallDistance >= MIN_FALL`), every other
 *   living entity within `2 + level` blocks takes `damage × 15% × level` as `true_damage`.
 * - **No fall damage**: holding the enchanted mace cancels fall damage outright, which is what makes
 *   repeatedly jumping off cliffs with a mace viable instead of suicidal.
 *
 * ## Guards
 * - `fallDistance` is read on the **hurt** event, before vanilla resets it; a melee swing on flat ground
 *   therefore never produces a shockwave (that is the `MIN_FALL` gate).
 * - The AoE goes through `damageElemental`, so it carries the auxiliary flag and cannot re-enter the
 *   on-hit pipeline.
 */
@ModEnchantment(
    id = "skyfall",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = "#minecraft:enchantable/mace",
    slots = [EnchantSlot.MAINHAND],
)
object EnchantSkyfallHandler : Listenable {

    /** Below this fall distance the slam is just a swing. */
    private const val MIN_FALL = 1.5

    private const val PER_LEVEL = 0.15f
    private const val BASE_RADIUS = 2.0

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val weapon = attacker.combatStack(e.source)
        if (weapon.item != Items.MACE) return

        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.SKYFALL)
        if (level <= 0) return
        if (attacker.fallDistance < MIN_FALL) return

        val target = e.entityLiving
        val radius = BASE_RADIUS + level
        val shockwave = e.amount * PER_LEVEL * level
        val box = target.boundingBox.expand(radius)

        var hit = 0
        for (other in target.world.getEntitiesByClass(LivingEntity::class.java, box) {
            it !== attacker && it.isAlive && it !== target
        }) {
            other.damageElemental(attacker, ModDamageTypes.TRUE_DAMAGE, shockwave)
            hit++
        }

        FSTLog.log("Skyfall") {
            "target=${target.type.name.string}, level=$level, fall=${"%.1f".format(attacker.fallDistance)}, " +
                "shockwave=${"%.2f".format(shockwave)}, radius=${"%.1f".format(radius)}, hit=$hit, " +
                "outcome=slammed"
        }
    }

    /** Holding the enchanted mace means the fall that powers it never hurts you. */
    @SubscribeEvent
    fun onLivingFall(e: LivingFallEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (player.world.isClient) return
        if (player.mainHandStack.item != Items.MACE) return
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.SKYFALL)
        if (level <= 0) return
        e.isCanceled = true
    }
}
