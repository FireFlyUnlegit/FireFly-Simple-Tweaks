package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.TridentEntity
import net.minecraft.item.Items

/**
 * 海神之怒 — a thrown trident keeps striking along its path.
 *
 * ## The empty axis this fills
 * Tridents were the only weapon class this mod did not touch at all: `loyalty` / `impaling` / `riptide` are
 * vanilla components, so nothing here read a trident. Damage is dealt as **lightning elemental damage**, which
 * means it can start a reaction (`conduct` on a wet target) instead of being another flat multiplier.
 *
 * ## Conditions
 * - the hit must come from a **trident projectile** (`TridentEntity`) — a trident swung as a melee weapon is
 *   a normal hit and is handled by the conversion enchantments like any sword;
 * - in rain or when the victim is in water the extra damage doubles, which is the "sea god" fantasy and also
 *   gives the enchantment a reason to care about weather.
 *
 * ## Guard
 * The extra share is `damageElemental`, so it carries the auxiliary flag: it cannot re-enter the on-hit
 * pipeline, and `isOwnElementalDamage` stops it from feeding itself.
 */
@ModEnchantment(
    id = "trident_wrath",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = "#minecraft:enchantable/trident",
    slots = [EnchantSlot.MAINHAND],
)
object EnchantTridentWrathHandler : Listenable {

    private const val SHARE_PER_LEVEL = 0.4f
    private const val WET_MULTIPLIER = 2.0f
    private const val AURA_UNITS = ElementAuras.WEAK

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return
        if (e.source.source !is TridentEntity) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        val weapon = attacker.combatStack(e.source)
        if (weapon.item != Items.TRIDENT) return

        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.TRIDENT_WRATH)
        if (level <= 0) return

        val wet = target.isTouchingWater || attacker.world.isRaining
        val bonus = e.amount * SHARE_PER_LEVEL * level * (if (wet) WET_MULTIPLIER else 1f)
        if (bonus <= 0f) return

        target.damageElemental(attacker, ElementType.LIGHTNING.damageType, bonus)
        if (wet) ElementAuras.attach(target, ElementType.LIGHTNING, AURA_UNITS)

        FSTLog.log("TridentWrath") {
            "target=${target.type.name.string}, level=$level, wet=$wet, bonus=${"%.2f".format(bonus)}, " +
                "outcome=struck"
        }
    }
}
