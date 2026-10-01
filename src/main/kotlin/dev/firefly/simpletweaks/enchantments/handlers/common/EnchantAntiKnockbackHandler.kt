package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.common.EnchantAntiKnockback
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantAntiKnockbackHandler : Listenable {

    private val ATTRIBUTE_UUID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_anti_knockback".toByteArray())
    private val appliedLevel = WeakHashMap<EntityPlayer, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (p.world.isRemote) return

        val attr = p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE) ?: return
        val lvl = p.getArmorEnchantLevel(EnchantAntiKnockback)
        val hasModifier = attr.getModifier(ATTRIBUTE_UUID) != null

        if (lvl <= 0 && !hasModifier) {
            appliedLevel.remove(p)
            return
        }
        if (lvl > 0 && hasModifier && appliedLevel[p] == lvl) return

        attr.removeModifier(ATTRIBUTE_UUID)

        if (lvl > 0) {
            attr.applyModifier(
                AttributeModifier(ATTRIBUTE_UUID, "Anti Knockback", 0.125 * lvl, 0)
            )
            appliedLevel[p] = lvl
        } else {
            appliedLevel.remove(p)
        }
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        clearModifier(event.original)
        clearModifier(event.entityPlayer)
        appliedLevel.remove(event.original)
        appliedLevel.remove(event.entityPlayer)
    }

    @SubscribeEvent
    fun onLogout(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        clearModifier(e.player)
        appliedLevel.remove(e.player)
    }

    private fun clearModifier(p: EntityPlayer) {
        p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE)
            ?.removeModifier(ATTRIBUTE_UUID)
    }
}