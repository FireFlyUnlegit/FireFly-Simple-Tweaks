package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client half of [PacketDamageIndicator].
 *
 * The `context.client().execute { }` hop is redundant on Fabric (receivers already run on the client
 * thread) and is kept so the port stays diffable against the 1.12.2 handler that needed it.
 */
object PacketDamageIndicatorReceiver {

    private var logged = false

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketDamageIndicator.ID) { payload, context ->
            context.client().execute {
                // The config can be off even though the server sent something (it is the same switch
                // on both sides, but a server can have it on while a client has it off).
                if (!DamageIndicatorConfig.enabled) return@execute

                val world = context.client().world ?: return@execute
                val entity = world.getEntityById(payload.entityId) ?: return@execute

                DamageIndicatorManager.addDamage(
                    entity,
                    payload.actualDamage,
                    payload.isHeal,
                    payload.isSelf,
                    payload.maxHealth,
                    payload.realMaxHealth,
                    payload.afterRealHealth,
                    payload.elementOrdinal,
                    payload.role,
                    payload.label,
                    payload.crit,
                )

                if (!logged) {
                    logged = true
                    FSTLog.log("DamageIndicator") {
                        "packet=received, entity=${entity.type.name.string}, " +
                            "actual=${payload.actualDamage}, isHeal=${payload.isHeal}, " +
                            "isSelf=${payload.isSelf}, maxHealth=${payload.maxHealth}, " +
                            "realMaxHealth=${payload.realMaxHealth}, " +
                            "afterRealHealth=${payload.afterRealHealth}, " +
                            "element=${payload.elementOrdinal}, role=${payload.role}, outcome=received"
                    }
                }
            }
        }
    }
}
