package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.common.EnchantAntiKnockback
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantAntiKnockbackHandler : Listenable {
    private val ATTRIBUTE_UUID = UUID.nameUUIDFromBytes("Anti Knockback attribute".toByteArray())
    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val lvl = e.player.getArmorEnchantLevel(EnchantAntiKnockback)
        if (lvl > 0) {
            val kbAttr = e.player.getEntityAttribute(
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE
            )
            if (kbAttr != null) {
                val hasKBModifier = kbAttr.getModifier(ATTRIBUTE_UUID) != null
                if (!hasKBModifier) {
                    kbAttr.applyModifier(
                        AttributeModifier(
                            "[AntiKnockback] KnockBack Resistance Booster",
                            0.125 * lvl,
                            0
                        )
                    )
                } else kbAttr.removeModifier(ATTRIBUTE_UUID)

            }
        }
    }
}