package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.legendary.EnchantCombo
import dev.firefly.simpletweaks.util.attacker
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.target
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.network.play.server.SPacketEntityProperties
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantComboHandler : Listenable {

    private const val ATTACK_SPEED_PER_LEVEL = 0.35

    private val ATTACK_SPEED_UUID: UUID =
        UUID.nameUUIDFromBytes("simple_tweaks_combo_attack_speed".toByteArray())
    private val appliedLevel = WeakHashMap<EntityPlayer, Int>()

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val target = e.target

        val level = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantCombo)
        if (level <= 0) return

        val multiplier = (1f - level * 0.1f).coerceAtLeast(0f)
        target.hurtResistantTime =
            (target.hurtResistantTime * multiplier).toInt().coerceAtLeast(0)
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val attr = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED) ?: return
        val level = getItemSpecificEnchantLevel(p.heldItemMainhand, EnchantCombo)
        val hasModifier = attr.getModifier(ATTACK_SPEED_UUID) != null

        if (level <= 0 && !hasModifier) {
            appliedLevel.remove(p)
            return
        }
        if (level > 0 && hasModifier && appliedLevel[p] == level) return

        attr.removeModifier(ATTACK_SPEED_UUID)
        if (level > 0) {
            attr.applyModifier(
                AttributeModifier(
                    ATTACK_SPEED_UUID,
                    "Combo Attack Speed",
                    ATTACK_SPEED_PER_LEVEL * level,
                    2
                )
            )
            appliedLevel[p] = level
        } else {
            appliedLevel.remove(p)
        }

        if (p is EntityPlayerMP) {
            p.connection.sendPacket(
                SPacketEntityProperties(p.entityId, p.attributeMap.allAttributes)
            )
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
        p.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).removeModifier(ATTACK_SPEED_UUID)
    }
}