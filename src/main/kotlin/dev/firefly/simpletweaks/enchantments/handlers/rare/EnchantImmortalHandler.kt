package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.chance
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import java.util.*
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantImmortalHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                          | 1.21.1                                                             |
 * |-------------------------------------------------|--------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`          | `compat.event.LivingHurtEvent`                                     |
 * | `net.minecraftforge...gameevent.PlayerEvent`    | `compat.event.PlayerEvent` (both Forge classes are merged here)     |
 * | `target.uniqueID`                               | `target.uuid` (`EntityLike.getUuid`, method_5667)                  |
 * | `chance(0.2 + 0.2 * lvl)`                       | `compat.chance(Double)` — unchanged helper                          |
 * | `attacker.world.playSound(attacker, x, y, z, SoundEvent, SoundCategory, vol, pitch)` | identical 1.21 overload `World.playSound(PlayerEntity, double, double, double, SoundEvent, SoundCategory, float, float)` exists, so nothing changes |
 * | `attacker.posX/posY/posZ`                       | `attacker.x/y/z`                                                   |
 * | `EnchantImmortal` (Enchantment object)          | `GeneratedEnchantments.IMMORTAL` (RegistryKey)                        |
 */
@ModEnchantment(
    id = "immortal",
    category = EnchantCategory.RARE,
    type = EnchantType.ARMOR,
    maxLevel = 1,
    weight = 6,
    anvilCost = 6,
    minCostBase = 30,
    supportedItems = "#minecraft:enchantable/armor",
    slots = [EnchantSlot.ARMOR],
    order = 21,
)
object EnchantImmortalHandler : Listenable {


    private val hitCounter = mutableMapOf<UUID, Int>()
    private val shieldCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val target = e.entityLiving
        val id = target.uuid
        val attacker = e.attacker?: return
        val lvl = target.getArmorEnchantLevel(GeneratedEnchantments.IMMORTAL)
        if (lvl <= 0) {
            hitCounter.remove(id)
            shieldCounter.remove(id)
            return
        }

        val shield = shieldCounter[id] ?: 0
        if (shield > 0) {
            shieldCounter[id] = shield - 1
            e.isCanceled = true
            STLog.log("Immortal") {
                "target=${target.name.string}, attacker=${attacker.name.string}, lvl=$lvl, " +
                    "shieldLeft=${shield - 1}, damage=${e.amount}, outcome=shield-absorbed"
            }
            return
        }

        val hits = (hitCounter[id] ?: 0) + 1
        if (hits < 22 - lvl * 2) {
            hitCounter[id] = hits
            return
        }
        hitCounter[id] = 0

        if (chance(0.2 + 0.2 * lvl)) return

        shieldCounter[id] = lvl - 1
        e.isCanceled = true
        STLog.log("Immortal") {
            "target=${target.name.string}, attacker=${attacker.name.string}, lvl=$lvl, hits=$hits, " +
                "threshold=${22 - lvl * 2}, shieldGranted=${lvl - 1}, damage=${e.amount}, outcome=shield-triggered"
        }
        if (attacker is PlayerEntity) {
            attacker.world.playSound(
                attacker,
                attacker.x,
                attacker.y,
                attacker.z,
                SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
            )
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uuid
        hitCounter.remove(id)
        shieldCounter.remove(id)
    }
}
