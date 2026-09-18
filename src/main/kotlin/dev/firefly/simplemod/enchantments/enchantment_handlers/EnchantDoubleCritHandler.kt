package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantCritDamage
import dev.firefly.simplemod.enchantments.EnchantDoubleCrit
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import dev.firefly.simplemod.util.isCrit
import dev.firefly.simplemod.util.safeGetCooledAttackStrength
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random.Default.nextFloat

object EnchantDoubleCritHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val living = e.entityLiving?: return
        if (living.safeGetCooledAttackStrength() < 0.848) return

        val lvl = getItemSpecificEnchantLevel(living.heldItemMainhand, EnchantDoubleCrit)
        val lvl2 = getItemSpecificEnchantLevel(e.entityLiving.heldItemMainhand, EnchantCritDamage)

        if (lvl > 0) {
            val critDMG = (
                (.1f * lvl2) +
                    (if (lvl2 >= 2) (.05f * (lvl2 - 2)) else 0.0f) +
                            (if (lvl2 >= 6) (.025f * (lvl2 - 6)) else 0.0f)
                    )
            if (nextFloat() <= lvl * .1 && e.isCrit) {
                e.damageModifier += 0.5f + critDMG
            }
        }
    }
}