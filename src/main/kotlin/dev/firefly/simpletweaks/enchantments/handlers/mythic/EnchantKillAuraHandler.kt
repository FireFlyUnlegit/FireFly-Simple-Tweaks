package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mythic.EnchantKillAura
import dev.firefly.simpletweaks.util.canEntitySee
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.monster.IMob
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.DamageSource
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantKillAuraHandler : Listenable {

    private val cooldown = WeakHashMap<UUID, Int>()
    private val priorityTargets = WeakHashMap<UUID, MutableList<PriorityEntry>>()

    private var auraAttacking = false

    private class PriorityEntry(val entity: EntityLivingBase) {
        var ticksOutOfRange: Int = 0
    }

    private fun maxCooldown(lvl: Int): Int = (40 - 8 * lvl).coerceAtLeast(2)

    private fun rangeFor(lvl: Int): Double = (1.5 + 0.5 * lvl).coerceAtMost(3.5)

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return
        if (p.isDead) return

        val lvl = getItemSpecificEnchantLevel(p.heldItemMainhand, EnchantKillAura)

        cleanPriorityTargets(p, if (lvl > 0) rangeFor(lvl) else 1.5)

        if (lvl <= 0) {
            cooldown.remove(p.uniqueID)
            return
        }

        val cd = (cooldown[p.uniqueID] ?: 0) - 1
        if (cd > 0) {
            cooldown[p.uniqueID] = cd
            return
        }

        val target = findTarget(p, lvl) ?: return

        val damage = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)
            .attributeValue.toFloat()
        val weapon = p.heldItemMainhand

        val enchantBonus = EnchantmentHelper.getModifierForCreature(
            weapon,
            target.creatureAttribute,
        )
        val multiplier = 0.4 + (lvl * 0.1)

        auraAttacking = true
        val result = try {
            target.runPlayerAttack(p, (damage + enchantBonus) * multiplier, forceHit = true)
        } finally {
            auraAttacking = false
        }

        if (result > 0f) {
            p.addExhaustion(0.5f)
            p.heldItemMainhand.damageItem(1, p)
        }

        cooldown[p.uniqueID] = maxCooldown(lvl)
    }

    @SubscribeEvent
    fun onPlayerAttack(e: AttackEntityEvent) {
        if (auraAttacking) return

        val p = e.entityPlayer
        if (p.world.isRemote) return

        val lvl = getItemSpecificEnchantLevel(p.heldItemMainhand, EnchantKillAura)
        if (lvl <= 0) return

        (e.target as? EntityLivingBase)?.let { addPriorityTarget(p, it) }

        cooldown[p.uniqueID] = maxCooldown(lvl)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerLoggedOutEvent) {
        cooldown.remove(e.player.uniqueID)
        priorityTargets.remove(e.player.uniqueID)
    }


    private fun addPriorityTarget(p: EntityPlayer, target: EntityLivingBase) {
        val list = priorityTargets.getOrPut(p.uniqueID) { mutableListOf() }
        list.removeAll { it.entity === target }
        list.add(0, PriorityEntry(target))
    }

    private fun cleanPriorityTargets(p: EntityPlayer, range: Double) {
        val list = priorityTargets[p.uniqueID] ?: return
        if (list.isEmpty()) {
            priorityTargets.remove(p.uniqueID)
            return
        }

        val maxDistSq = range * range
        val iter = list.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val e = entry.entity

            val remove = when {
                e.isDead || !e.isEntityAlive -> true
                e.world !== p.world -> true
                p.getDistanceSq(e) > maxDistSq -> {
                    entry.ticksOutOfRange++
                    entry.ticksOutOfRange > 100
                }
                else -> {
                    entry.ticksOutOfRange = 0
                    false
                }
            }

            if (remove) iter.remove()
        }

        if (list.isEmpty()) priorityTargets.remove(p.uniqueID)
    }

    private fun hasMutualAttackHistory(p: EntityPlayer, e: EntityLivingBase): Boolean {
        if (p.revengeTarget === e) return true
        if (e.revengeTarget === p) return true
        val list = priorityTargets[p.uniqueID]
        return list != null && list.any { it.entity === e }
    }

    private fun isValidTarget(
        p: EntityPlayer,
        e: EntityLivingBase,
        source: DamageSource,
        maxDistSq: Double,
    ): Boolean {
        if (e === p) return false
        if (!e.isEntityAlive) return false
        if (e.world !== p.world) return false
        if (p.getDistanceSq(e) > maxDistSq) return false
        if (e.isOnSameTeam(p)) return false
        if (e.isEntityInvulnerable(source)) return false

        if (e !is IMob && !hasMutualAttackHistory(p, e)) return false

        if (!p.world.canEntitySee(p, e)) return false

        return true
    }

    private fun findTarget(p: EntityPlayer, lvl: Int): EntityLivingBase? {
        val range = rangeFor(lvl)
        val maxDistSq = range * range
        val source = DamageSource.causePlayerDamage(p)

        priorityTargets[p.uniqueID]?.let { list ->
            for (entry in list) {
                val e = entry.entity
                if (isValidTarget(p, e, source, maxDistSq)) return e
            }
        }

        val box = p.entityBoundingBox.grow(range)
        return p.world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)
            .filter { isValidTarget(p, it, source, maxDistSq) }
            .minByOrNull { p.getDistanceSq(it) }
    }
}