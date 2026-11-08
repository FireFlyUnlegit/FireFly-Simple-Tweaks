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
 * Still not counted: damage with **no player attacker** (fire, fall, mob-on-mob). The XP is computed
 * from `e.amount`, i.e. the value about to be applied *before* armour, so a rider that the i-frame gate
 * later discards can still pay out — the same inaccuracy direct hits have always had, and it errs in
 * the player's favour by a small amount rather than silently dropping an entire damage class.
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

    private val pendingXp = WeakHashMap<PlayerEntity, Float>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        // ⚠️ **No `isAuxiliaryDamage()` guard here, on purpose** — see the class KDoc. XP is not damage,
        // so counting the mod's own riders cannot re-enter the pipeline, and "all damage" is the point.
        if (e.invalid) return
        val atk = e.attacker ?: return
        val lvl = getItemSpecificEnchantLevel(atk.combatStack(e.source), GeneratedEnchantments.EXPERIENCE_STEALER)
        if (lvl <= 0) return

        val raw = e.amount * 0.1f * lvl
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
