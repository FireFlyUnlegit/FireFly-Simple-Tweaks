package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantAoeAttackHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                       | 1.21.1                                                          |
 * |----------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`       | `compat.event.LivingHurtEvent`                                   |
 * | `net.minecraftforge...EventPriority`         | `compat.event.EventPriority` (annotation keeps `priority`)       |
 * | `EntityLivingBase` / `EntityPlayer`          | `LivingEntity` / `PlayerEntity`                                  |
 * | `e.source.trueSource`                        | `e.source.attacker` (method_5529)                                |
 * | `attacker.heldItemMainhand`                  | `attacker.mainHandStack` (method_6047)                           |
 * | `target.entityBoundingBox.grow(d)`           | `target.boundingBox.expand(d)` (`Box.expand(double)`, method_1014) |
 * | `world.getEntitiesWithinAABB(C::class.java, box)` | `world.getEntitiesByClass(C::class.java, box) { true }` (method_8390, declared on `EntityView`) |
 * | `it.isEntityAlive`                           | `it.isAlive` (method_5805)                                       |
 * | `it.canBeAttackedWithItem()`                 | `it.isAttackable` (method_5732) — 1.12.2's `EntityLivingBase#canBeAttackedWithItem` has no 1.21 name; `Entity.isAttackable()` is the flag vanilla's `PlayerEntity#attack` checks and non-living entities override to `false`, which is the same role |
 * | `it.isEntityInvulnerable(source)`            | `it.isInvulnerableTo(source)` (method_5679)                      |
 * | `EnchantAoeAttack` (Enchantment object)      | `GeneratedEnchantments.AOE_ATTACK` (RegistryKey)                    |
 * | `net.minecraft.util.DamageSource`            | `net.minecraft.entity.damage.DamageSource` (kept unused, exactly as in 1.12.2) |
 *
 * <h2>This handler was blocked on the crit seam, and no longer is</h2>
 * Its only non-mechanical dependency was `other.runPlayerAttack(attacker, perTarget, true)`: the
 * 1.12.2 helper's body calls `ForgeHooks.getCriticalHit` / `CriticalHitEvent`, which did not exist in
 * this port when the batch was written. That seam now exists, and
 * [dev.firefly.simpletweaks.util.runPlayerAttack] honours it — so the splash damage crit-rolls
 * exactly as it did in 1.12.2 (the 3rd positional argument is `forceHit = true`; `allowCrit` keeps
 * its default of `true`).
 *
 * The re-entrancy guard matters: the splash goes through `LivingEntity#damage`, which re-fires
 * `LivingHurtEvent`, so without `inAoe` the handler would recurse.
 */
@ModEnchantment(
    id = "aoe_attack",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 8,
    anvilCost = 4,
    minCostBase = 30,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.25,
    order = 6,
)
object EnchantAoeAttackHandler : Listenable {

    private val inAoe = ThreadLocal.withInitial { false }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (inAoe.get()) return
        if (e.invalid) return
        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving ?: return

        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.AOE_ATTACK)
        if (lvl <= 0) return

        val box = target.boundingBox.expand((0.5 * lvl).coerceAtMost(2.0))
        val others = target.world.getEntitiesByClass(LivingEntity::class.java, box) { true }
            .filter {
                it !== attacker && it !== target && it.isAlive &&
                        it.isAttackable && !it.isInvulnerableTo(e.source)
            }
        if (others.isEmpty()) return

        val originalAmount = e.amount
        val perTarget = (originalAmount * lvl / (others.size + 1)).coerceAtMost(originalAmount)

        e.amount = perTarget

        inAoe.set(true)
        try {
            for (other in others) {
                other.runPlayerAttack(attacker, perTarget, true)
            }
        } finally {
            inAoe.set(false)
        }

        STLog.log("AoeAttack") {
            "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                "others=${others.size}, originalAmount=$originalAmount, perTarget=$perTarget, " +
                "targets=${others.joinToString(",") { it.name.string }}"
        }
    }
}
