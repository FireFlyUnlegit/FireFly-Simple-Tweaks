package dev.firefly.simpletweaks.enchantments.others

import dev.firefly.simpletweaks.core.Listenable
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.IAttribute
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object HealthNaNFixer : Listenable {

    private val STATS: Array<IAttribute> = arrayOf(
        SharedMonsterAttributes.MAX_HEALTH,
        SharedMonsterAttributes.MOVEMENT_SPEED,
        SharedMonsterAttributes.ATTACK_DAMAGE,
        SharedMonsterAttributes.ATTACK_SPEED,
        SharedMonsterAttributes.ARMOR,
        SharedMonsterAttributes.ARMOR_TOUGHNESS,
        SharedMonsterAttributes.KNOCKBACK_RESISTANCE,
    )

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (world.isRemote) return

        val toRemove = mutableListOf<EntityLivingBase>()
        val toFix = mutableListOf<EntityPlayer>()

        for (entity in world.loadedEntityList) {
            val living = entity as? EntityLivingBase ?: continue
            val h = living.health
            if (!h.isNaN() && !h.isInfinite()) continue

            if (living is EntityPlayer) toFix.add(living)
            else toRemove.add(living)
        }

        for (p in toFix) {
            println(
                "[ST-NaN] player=${p.name} health=${p.health} maxHealth=${p.maxHealth} absorption=${p.absorptionAmount}"
            )

            // 1. 清掉所有 amount = NaN / Infinity 的属性 modifier
            for (attr in STATS) {
                val inst = p.getEntityAttribute(attr) ?: continue
                val bad = inst.modifiers.filter { it.amount.isNaN() || it.amount.isInfinite() }
                for (mod in bad) {
                    println(
                        "[ST-NaN]   remove modifier '${mod.name}' on ${attr.name} amount=${mod.amount}"
                    )
                    inst.removeModifier(mod)
                }
            }

            // 2. 清掉 NaN absorption
            if (p.absorptionAmount.isNaN() || p.absorptionAmount.isInfinite()) {
                p.absorptionAmount = 0f
            }

            // 3. 拉血
            val max = p.maxHealth
            p.health = if (max.isNaN() || max <= 0f || max.isInfinite()) 20f else max
        }

        for (entity in toRemove) {
            println(
                "[ST-NaN] removing ${entity.javaClass.simpleName} (${entity.uniqueID}) health=${entity.health}"
            )
            world.removeEntity(entity)
        }
    }
}