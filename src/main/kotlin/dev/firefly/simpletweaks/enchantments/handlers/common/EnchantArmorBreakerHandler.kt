package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.getRandomArmor
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import kotlin.random.Random.Default.nextFloat

/**
 * 1.21 port of `enchantments/handlers/common/EnchantArmorBreakerHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                     | 1.21.1                                                              |
 * |--------------------------------------------|---------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`     | `compat.event.LivingHurtEvent`                                      |
 * | `EntityLivingBase`                         | `LivingEntity`                                                      |
 * | `EntityPlayer`                             | `PlayerEntity`                                                      |
 * | `e.source.trueSource`                      | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529)   |
 * | `attacker.heldItemMainhand`                | `attacker.mainHandStack` (method_6047)                              |
 * | `EnchantArmorBreaker` (Enchantment object) | `ModEnchantmentKeys.ARMOR_BREAKER` (RegistryKey)                    |
 * | `stack.damageItem(lvl, target)`            | `stack.damage(lvl, target, EquipmentSlot.MAINHAND)` (method_7970)   |
 *
 * `target.getRandomArmor()` is the `util/PlayerUtils.kt` extension — see the batch report: the 1.21.1
 * port of that util file does not exist yet, so this call is kept 1:1 and will resolve once it lands.
 */
object EnchantArmorBreakerHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.attacker as? LivingEntity)?: return
        val target = e.entityLiving as? PlayerEntity?: return

        val lvl = getItemSpecificEnchantLevel( attacker.mainHandStack, ModEnchantmentKeys.ARMOR_BREAKER)
        val rate = 0.15 * lvl + 0.1
        if (lvl > 0 && rate > nextFloat()) {
            val armor = target.getRandomArmor()
            val damageBefore = armor.damage
            armor.damage(lvl,target, EquipmentSlot.MAINHAND)
            STLog.log("ArmorBreaker") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, rate=$rate, " +
                    "armor=${armor.item}, damage=$damageBefore->${armor.damage}"
            }
        }
    }
}
