package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantExecuteHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                                          |
 * |-----------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`        | `compat.event.LivingHurtEvent`                                  |
 * | `EntityLivingBase`                            | `net.minecraft.entity.LivingEntity`                             |
 * | `e.source.trueSource`                         | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529) |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)                          |
 * | `target.maxHealth` / `target.health` / `target.absorptionAmount` | unchanged (method_6063 / method_6032 / method_6067) |
 * | `EnchantExecute` (Enchantment object)         | `GeneratedEnchantments.EXECUTE` (RegistryKey)                      |
 *
 * ## Why the bonus is capped against the hit (`build=cleanup70`)
 * The formula is `2 % × level × missing health`, i.e. it reads the **target's health pool**, which
 * nothing in the mod controls. On a vanilla zombie that is a rounding error; on a target whose max
 * health has been inflated by a mod, an attribute command or a boss bar it becomes an arbitrarily large
 * number — measured in the author's session: a 1,000,000 HP target produced individual hits of
 * 494,731 / 312,750, because the bonus is added to `e.amount` **before** the crit / combo / true-damage
 * multipliers run, so every later multiplier also scaled the *health pool*.
 *
 * The fix keeps the finisher fantasy and makes it independent of how big the bar is: the bonus may
 * never exceed [FINISHER_HIT_MULTIPLIER] × the swing that triggered it (`1.25` since it is a RARE
 * enchantment — see that constant). For every normal mob nothing changes (`2 % × level × missing health`
 * is far below `1.25 × hit`), while a 1M HP target can no longer turn one swing into a 500k hit.
 */
@ModEnchantment(
    id = "execute",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.RARE,
    anvilCost = 6,
    minCostBase = 25,
    minCostPerLevel = 5,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 16,
)
object EnchantExecuteHandler : Listenable {

    /**
     * How many times the triggering hit the bonus may add.
     *
     * **1.25**, not 2: 处决 is a RARE enchantment, so its finisher bonus should read as "a bit more than
     * the swing", not as "double the swing against anything with a big bar". At 1.25 the cap only ever
     * binds on inflated health pools; on vanilla mobs the raw formula (`2 % × level × missing health`) is
     * already below it, so nothing changes there.
     */
    const val FINISHER_HIT_MULTIPLIER = 1.25f

    /** Floor for the cap, so a tiny hit against a hurt target still gets a usable bonus. */
    const val FINISHER_MIN_CAP = 4f

    /**
     * The execute bonus for [hit] damage against [missingHealth], at enchantment [level].
     *
     * Public and pure so the cap is testable without an entity: a game test has no player, and this is
     * the exact arithmetic that produced the 1M-HP nuke.
     */
    fun executeBonus(hit: Float, missingHealth: Float, level: Int): Float {
        if (level <= 0 || hit <= 0f || missingHealth <= 0f) return 0f
        val raw = missingHealth * 0.02f * level
        val cap = maxOf(hit * FINISHER_HIT_MULTIPLIER, FINISHER_MIN_CAP)
        return raw.coerceAtMost(cap)
    }

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {

        // `build=cleanup22`: the mod's own follow-up damage (poison tick, chain arc, holy light, shield
        // reflection) is player-attributed, so without this guard each of them counts as a new hit and
        // re-fires the whole on-hit stack — one poisoned target multiplied the damage every second.
        if (e.source.isAuxiliaryDamage()) return
        if (e.invalid) return
        val attacker = (e.source.attacker as? LivingEntity)?: return
        val target = e.entityLiving
        val lvl = getItemSpecificEnchantLevel(attacker.combatStack(e.source), GeneratedEnchantments.EXECUTE)
        if (lvl > 0) {
            val missing = ((target.maxHealth + target.absorptionAmount) -
                (target.health + target.absorptionAmount)).coerceAtLeast(0f)
            val damageBefore = e.amount
            val lostHealth = executeBonus(damageBefore, missing, lvl)
            e.amount += lostHealth
            FSTLog.log("Execute") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "health=${target.health}/${target.maxHealth}, missing=$missing, " +
                    "bonus=$lostHealth, damage=$damageBefore->${e.amount}, " +
                    "outcome=${if (lostHealth < missing * 0.02f * lvl) "finisher-capped" else "finisher"}"
            }
        }
    }
}
