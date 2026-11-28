package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.mob.MobEntity
import net.minecraft.server.world.ServerWorld

/**
 * Heals mobs that have not been hurt for [OUT_OF_COMBAT_TICKS].
 *
 * The combat check reuses vanilla's `lastAttackedTime` (set from `Entity#age` on every hurt and saved
 * as `HurtByTimestamp`) instead of keeping a per-mob map, so nothing has to be synced or cleaned up.
 */
object MobRegen : Listenable {

    /** 10 s, fixed: a third config row would overflow the 9-row budget of the mob page. */
    private const val OUT_OF_COMBAT_TICKS = 200

    override fun init() {
        ServerTickEvents.END_WORLD_TICK.register { world -> onWorldTick(world) }
    }

    private fun onWorldTick(world: ServerWorld) {
        if (!MobEnhancementConfig.enabled || !MobEnhancementConfig.regenEnabled) return
        val percent = MobEnhancementConfig.regenPercent
        if (percent <= 0) return
        if (world.time % 20L != 0L) return
        for (entity in world.iterateEntities()) {
            val mob = entity as? MobEntity ?: continue
            if (!mob.isAlive || mob.health >= mob.maxHealth) continue
            if (mob.age - mob.lastAttackedTime < OUT_OF_COMBAT_TICKS) continue
            mob.heal(mob.maxHealth * percent / 100f)
            if (mob.health >= mob.maxHealth) {
                FSTLog.log("MobRegen") {
                    "mob=${mob.type} level=${(mob as? LevelledMob)?.mobLevel ?: 0} outcome=full"
                }
            }
        }
    }
}
