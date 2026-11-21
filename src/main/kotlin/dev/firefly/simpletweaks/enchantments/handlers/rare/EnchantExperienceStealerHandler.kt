package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
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
 *
 * ## Every kind of damage counts (`build=cleanup73`)
 * Every other on-hit handler starts with `if (e.source.isAuxiliaryDamage()) return`, because the mod's
 * own follow-up damage (elemental conversion, reaction riders, poison tick, chain arcs, shield bash) is
 * **player-attributed**: letting it re-enter the on-hit stack re-fires the entire handler list and the
 * damage multiplies (`build=cleanup22`'s "伤害爆高").
 *
 * This handler deliberately does **not** take that guard. XP is not damage, so re-entering here cannot
 * feed back into the pipeline — and the author's requirement is "steal for *all* damage", not only for
 * the physical swing. The consequence, spelled out so it is not mistaken for a bug later:
 *
 * | source of damage | counted before | counted now |
 * |---|---|---|
 * | sword swing | yes | yes |
 * | elemental conversion rider | **no** | yes |
 * | reaction (conduct / overload / …) | **no** | yes |
 * | venom DoT tick | **no** | yes |
 * | chain arc / holy light / shield bash | **no** | yes |
 * | shield reflection (`reflection`) | **no** | yes |
 *
* Still not counted: damage with **no player attacker** (fire, fall, mob-on-mob).
 *
 * ## Why XP is not proportional to damage any more (`build=devVer.1.0.18`)
 *
 * The first cut paid `damage x 0.1 x level`. That was fine while a sword hit for tens of points; once the
 * mod's own multipliers stacked up it handed out tens of thousands of XP per swing — one warden kill was
 * worth 48 878 XP, three levels' worth of "instant max level". XP now scales with the **fraction of the
 * target's health the hit removed** (see [stolenXp]), so a full kill pays "what that mob is worth x 0.25 x
 * level" no matter how absurd the numbers on the sword get.
 */
@ModEnchantment(
    id = "experience_stealer",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 8,
    weight = EnchantItemWeights.RARE,
    anvilCost = 6,
    minCostBase = 25,
    minCostPerLevel = 5,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 17,
)
object EnchantExperienceStealerHandler : Listenable {

    /** Rough XP a mob is worth per point of max health: a zombie lands near 3, a warden near 34. */
    private const val XP_PER_HEALTH = 0.03f

    /** Floor of the estimate, so a 1-health mob is still worth something. */
    private const val BASE_KILL_XP = 3f

    /** Share of the estimated kill XP paid out per enchantment level, across a whole kill. */
    private const val SHARE_PER_LEVEL = 0.25f

    private val pendingXp = WeakHashMap<PlayerEntity, Float>()

    /**
     * XP for one hit: the fraction of the target's health this hit removed, times the XP that target is
     * roughly worth, times `0.25 x level`.
     *
     * Damage-proportional XP was tried first and blew up once the mod's own multipliers grew: a 150 000
     * damage swing paid `150 000 x 0.1 x level` on **every** rider, so one warden kill handed out ~49 000
     * XP. Dividing by max health makes the ceiling "a whole kill", which is the only quantity a player can
     * reason about, and keeps overkill hits from paying more than the mob is worth.
     */
    @JvmStatic
    fun stolenXp(damage: Float, maxHealth: Float, level: Int): Float {
        if (level <= 0 || maxHealth <= 0f || damage <= 0f) return 0f
        val share = (damage / maxHealth).coerceIn(0f, 1f)
        val killXp = BASE_KILL_XP + maxHealth * XP_PER_HEALTH
        return share * killXp * SHARE_PER_LEVEL * level
    }

    /**
     * The same payout with the health share **not** clamped, i.e. including overkill.
     *
     * Overkill is real damage and the player who lands a 5 000-damage hit on a 1 024-health mob did
     * deal it, so it is worth XP — but only a little more: [onLivingHurt] spends this difference under
     * a one-level budget, so a ridiculous hit cannot jump the bar by hundreds of levels.
     */
    @JvmStatic
    fun stolenXpUncapped(damage: Float, maxHealth: Float, level: Int): Float {
        if (level <= 0 || maxHealth <= 0f || damage <= 0f) return 0f
        val share = damage / maxHealth
        val killXp = BASE_KILL_XP + maxHealth * XP_PER_HEALTH
        return share * killXp * SHARE_PER_LEVEL * level
    }

    /** XP still missing for the player's *next* level, in the vanilla curve. */
    @JvmStatic
    fun xpToNextLevel(level: Int, progress: Float): Float {
        val need = when {
            level >= 30 -> 112 + (level - 30) * 9
            level >= 15 -> 37 + (level - 15) * 5
            else -> 7 + level * 2
        }.toFloat()
        return (need - progress * need).coerceAtLeast(0f)
    }


    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        // ⚠️ **No `isAuxiliaryDamage()` guard here, on purpose** — see the class KDoc. XP is not damage,
        // so counting the mod's own riders cannot re-enter the pipeline, and "all damage" is the point.
        if (e.invalid) return
        val atk = e.attacker ?: return
        val lvl = getItemSpecificEnchantLevel(atk.combatStack(e.source), GeneratedEnchantments.EXPERIENCE_STEALER)
        if (lvl <= 0) return

        val targetMax = e.entityLiving.maxHealth
        val raw = stolenXp(e.amount, targetMax, lvl)
        // Overkill: everything above `max health` is extra, paid under a one-level budget below.
        val extra = (stolenXpUncapped(e.amount, targetMax, lvl) - raw).coerceAtLeast(0f)
        // Logged so "the rider also paid out" is observable rather than inferred: `aux=false` is a real
        // swing, `aux=true` is conversion / reaction / DoT / reflection damage.
        val auxiliary = e.source.isAuxiliaryDamage()
        if (atk is PlayerEntity) {
            val acc = (pendingXp[atk] ?: 0f) + raw
            val whole = acc.toInt()
            if (whole > 0) {
                atk.addExperience(whole)
                pendingXp[atk] = acc - whole
                FSTLog.log("ExperienceStealer") {
                    "attacker=${atk.name.string}, target=${e.entityLiving.name.string}, lvl=$lvl, " +
                        "aux=$auxiliary, raw=$raw, gained=$whole, pending=${acc - whole}"
                }
            } else {
                pendingXp[atk] = acc
            }

            if (extra > 0f) {
                // The base payout may already have crossed several levels; overkill may add **one**
                // more, never more than that, however absurd the damage was.
                val allowance = xpToNextLevel(atk.experienceLevel, atk.experienceProgress)
                val bonus = extra.coerceAtMost(allowance).toInt()
                if (bonus > 0) {
                    atk.addExperience(bonus)
                    FSTLog.log("ExperienceStealer") {
                        "attacker=${atk.name.string}, target=${e.entityLiving.name.string}, " +
                            "overkill=$extra, allowance=$allowance, gained=$bonus, " +
                            "level=${atk.experienceLevel}"
                    }
                }
            }
        }

        val tgt = e.target
        if (tgt is PlayerEntity) {
            val drain = raw.coerceAtMost(tgt.experienceProgress)
            tgt.experienceProgress = (tgt.experienceProgress - drain).coerceAtLeast(0f)
            FSTLog.log("ExperienceStealer") {
                "attacker=${atk.name.string}, target=${tgt.name.string}, lvl=$lvl, " +
                    "aux=$auxiliary, raw=$raw, drained=$drain, targetProgress=${tgt.experienceProgress}"
            }
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        pendingXp.remove(e.player)
    }
}
