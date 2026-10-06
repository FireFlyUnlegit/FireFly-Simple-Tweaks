package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mythic.EnchantPrismaticBlessing
import dev.firefly.simpletweaks.util.displayedItem
import dev.firefly.simpletweaks.util.getItemsSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.ai.attributes.IAttribute
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantPrismaticBlessingHandler : Listenable {

    private val MODIFIER_UUID: UUID =
        UUID.nameUUIDFromBytes("simple_tweaks_prismatic_blessing".toByteArray())


    private val STATS: Array<IAttribute> = arrayOf(
        SharedMonsterAttributes.MAX_HEALTH,
        SharedMonsterAttributes.MOVEMENT_SPEED,
        SharedMonsterAttributes.ATTACK_DAMAGE,
        SharedMonsterAttributes.ATTACK_SPEED,
        SharedMonsterAttributes.ARMOR,
        SharedMonsterAttributes.ARMOR_TOUGHNESS,
    )

    private val appliedLevels = WeakHashMap<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val lvl = getItemsSpecificEnchantLevel(p.displayedItem, EnchantPrismaticBlessing)

        val prev = appliedLevels[p.uniqueID] ?: 0
        if (lvl == prev) return
        appliedLevels[p.uniqueID] = lvl

        for (attr in STATS) {
            p.getEntityAttribute(attr).removeModifier(MODIFIER_UUID)
        }

        if (lvl > 0) {
            val ratio = 0.04 * lvl
            for (attr in STATS) {
                val inst = p.getEntityAttribute(attr) ?: continue
                inst.applyModifier(
                    AttributeModifier(
                        MODIFIER_UUID,
                        "Prismatic Blessing",
                        ratio,
                        1,
                    )
                )
            }
            if (p.health > p.maxHealth) p.health = p.maxHealth
        }

        p.syncAttributes()
    }

    @SubscribeEvent
    fun onPlayerClone(e: PlayerEvent.Clone) {
        if (!e.isWasDeath) return
        appliedLevels.remove(e.original.uniqueID)
        for (attr in STATS) {
            e.entityPlayer.getEntityAttribute(attr).removeModifier(MODIFIER_UUID)
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerLoggedOutEvent) {
        appliedLevels.remove(e.player.uniqueID)
    }
}