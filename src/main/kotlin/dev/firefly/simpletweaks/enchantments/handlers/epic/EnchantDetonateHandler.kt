package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * 引爆 — consumes the target's whole aura and deals `units × (2 + 0.5 × level)` damage of that element.
 *
 * ## Why this is not "another reaction"
 * A reaction needs two different elements and is throttled by [dev.firefly.simpletweaks.util.elements.AttachmentIcd]
 * *and* the transformative cooldown. This is the **player-controlled** finisher: it needs a pre-existing
 * aura, it consumes all of it, and it does not touch the reaction tables at all. So the loop becomes
 * "attach, then detonate", which is the one thing the aura system could not express before.
 *
 * ## Why it must skip `isOwnElementalDamage`
 * Our elemental riders arrive as `LivingHurtEvent`s with the player as attacker; without the guard this
 * would fire on its own damage and could detonate the aura it just produced.
 */
@ModEnchantment(
    id = "detonate",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 25,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantDetonateHandler : Listenable {

    /** Below this aura amount there is nothing worth consuming. */
    private const val MIN_UNITS = 2.0f
    private const val BASE = 2.0f
    private const val PER_LEVEL = 0.5f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving

        val level = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.DETONATE)
        if (level <= 0) return

        val aura = ElementAuras.peek(target) ?: return
        if (aura.second < MIN_UNITS) return

        val element = aura.first
        val units = aura.second
        ElementAuras.clear(target)

        val amount = units * (BASE + PER_LEVEL * level)
        val applied = target.damageElemental(attacker, element.damageType, amount)
        FSTLog.log("Detonate") {
            "target=${target.type.name.string}, element=${element.id}, units=${"%.2f".format(units)}, " +
                "amount=${"%.2f".format(amount)}, applied=$applied, outcome=detonated"
        }
    }
}
