package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantExperienceStealerHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                      | 1.21.1                                                          |
 * |---------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`      | `compat.event.LivingHurtEvent`                                  |
 * | `net.minecraftforge...gameevent.PlayerEvent`| `compat.event.PlayerEvent` (both Forge PlayerEvent classes are merged into this one) |
 * | `EntityPlayer`                              | `net.minecraft.entity.player.PlayerEntity`                      |
 * | `atk.heldItemMainhand`                      | `atk.mainHandStack` (method_6047)                               |
 * | `EntityPlayer.addExperience(int)`           | unchanged (`method_7258`)                                       |
 * | `EntityPlayer.experience` (the 0..1 progress float, **not** total XP) | `PlayerEntity.experienceProgress` (public field `field_7531`) |
 * | `EnchantExperienceStealer` (Enchantment)    | `GeneratedEnchantments.EXPERIENCE_STEALER` (RegistryKey)           |
 *
 * Non-obvious mapping worth calling out: 1.21 renamed the two XP fields. `experienceLevel` (int) is
 * still the level and `totalExperience` still exists, but the float that 1.12.2 called `experience`
 * is now `experienceProgress` — hence the rename on the drain branch below.
 */
@ModEnchantment(
    id = "experience_stealer",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 8,
    weight = 6,
    anvilCost = 6,
    minCostBase = 25,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.3,
    order = 17,
)
object EnchantExperienceStealerHandler : Listenable {

    private val pendingXp = WeakHashMap<PlayerEntity, Float>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val atk = e.attacker ?: return
        val lvl = getItemSpecificEnchantLevel(atk.mainHandStack, GeneratedEnchantments.EXPERIENCE_STEALER)
        if (lvl <= 0) return

        val raw = e.amount * 0.1f * lvl
        if (atk is PlayerEntity) {
            val acc = (pendingXp[atk] ?: 0f) + raw
            val whole = acc.toInt()
            if (whole > 0) {
                atk.addExperience(whole)
                pendingXp[atk] = acc - whole
                STLog.log("ExperienceStealer") {
                    "attacker=${atk.name.string}, target=${e.entityLiving.name.string}, lvl=$lvl, " +
                        "raw=$raw, gained=$whole, pending=${acc - whole}"
                }
            } else {
                pendingXp[atk] = acc
            }
        }

        val tgt = e.target
        if (tgt is PlayerEntity) {
            val drain = raw.coerceAtMost(tgt.experienceProgress)
            tgt.experienceProgress = (tgt.experienceProgress - drain).coerceAtLeast(0f)
            STLog.log("ExperienceStealer") {
                "attacker=${atk.name.string}, target=${tgt.name.string}, lvl=$lvl, " +
                    "raw=$raw, drained=$drain, targetProgress=${tgt.experienceProgress}"
            }
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        pendingXp.remove(e.player)
    }
}
