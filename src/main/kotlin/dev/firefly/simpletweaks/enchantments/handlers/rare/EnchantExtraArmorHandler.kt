package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.rare.EnchantExtraArmor
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantExtraArmorHandler : Listenable {

    private val ARMOR_UUID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_extra_armor_armor".toByteArray())
    private val TOUGHNESS_UUID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_extra_armor_toughness".toByteArray())

    private val appliedLevel = WeakHashMap<EntityPlayer, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val armorAttr = p.getEntityAttribute(SharedMonsterAttributes.ARMOR) ?: return
        val toughnessAttr = p.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS) ?: return

        val total = p.getArmorEnchantLevel(EnchantExtraArmor)
        val hasModifier = armorAttr.getModifier(ARMOR_UUID) != null

        if (total <= 0 && !hasModifier) {
            appliedLevel.remove(p)
            return
        }
        if (total > 0 && hasModifier && appliedLevel[p] == total) return

        armorAttr.removeModifier(ARMOR_UUID)
        toughnessAttr.removeModifier(TOUGHNESS_UUID)
        val armor = total * 2.0
        val armorToughness = total * 1.6
        val percentageArmor = armor * 0.01 * total
        val percentageToughness = armorToughness * 0.01 * total

        if (total > 0) {
            armorAttr.applyModifier(
                AttributeModifier(ARMOR_UUID, "Extra Armor", percentageArmor + armor, 0)
            )
            toughnessAttr.applyModifier(
                AttributeModifier(TOUGHNESS_UUID, "Extra Armor Toughness", percentageToughness + armorToughness, 0)
            )
            appliedLevel[p] = total
        } else {
            appliedLevel.remove(p)
        }
        p.syncAttributes()
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        clearModifiers(event.original)
        clearModifiers(event.entityPlayer)
        appliedLevel.remove(event.original)
        appliedLevel.remove(event.entityPlayer)
    }

    @SubscribeEvent
    fun onLogout(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        clearModifiers(e.player)
        appliedLevel.remove(e.player)
    }

    private fun clearModifiers(p: EntityPlayer) {
        p.getEntityAttribute(SharedMonsterAttributes.ARMOR).removeModifier(ARMOR_UUID)
        p.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).removeModifier(TOUGHNESS_UUID)
    }
}