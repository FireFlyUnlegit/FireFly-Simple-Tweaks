package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.rare.EnchantExtraArmor
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
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

        val total = p.getArmorEnchantLevel(EnchantExtraArmor)
        if (appliedLevel[p] == total) return

        val armorAttr = p.getEntityAttribute(SharedMonsterAttributes.ARMOR)
        val toughnessAttr = p.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS)

        armorAttr.removeModifier(ARMOR_UUID)
        toughnessAttr.removeModifier(TOUGHNESS_UUID)

        if (total > 0) {
            armorAttr.applyModifier(
                AttributeModifier(ARMOR_UUID, "Extra Armor", total * 2.0, 0)
            )
            toughnessAttr.applyModifier(
                AttributeModifier(TOUGHNESS_UUID, "Extra Armor Toughness", total * 0.8, 0)
            )
        }

        appliedLevel[p] = total
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        clearModifiers(event.original)
        clearModifiers(event.entityPlayer)
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