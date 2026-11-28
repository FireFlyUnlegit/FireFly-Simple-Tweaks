package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.registry.tag.DamageTypeTags

/**
 * Scales every damage a levelled mob deals except plain melee, which the `generic.attack_damage`
 * modifier written by [MobLevels.apply] already covers.
 *
 * Vanilla only reads that attribute for melee swings. Arrows, fireballs, wither skulls, sonic booms and
 * explosions compute damage from their own base values, so without this seam a level-30 skeleton's arrow
 * or a high-level mob's blast stays at level-1 strength.
 *
 * The melee test cannot be done by damage-type name alone: in 1.21 a mob's arrow also reports
 * `mob` (`mob_projectile` shares the message id). What separates them is that an arrow's direct
 * entity is a projectile while a melee hit's direct entity is the attacker itself.
 *
 * Explosions are additionally capped by [MobLevels.explosionMultiplier]. Their radius is left alone:
 * growing the blast size turns a "harder mob" into terrain damage, and vanilla derives radius and
 * damage from the same power value, so the radius is no longer touched anywhere.
 */
object MobAbilityDamage : Listenable {

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return

        val projectile = e.source.source is ProjectileEntity || e.source.isIn(DamageTypeTags.IS_PROJECTILE)
        val explosion = e.source.isIn(DamageTypeTags.IS_EXPLOSION)
        // A mob explosion's direct source is the mob itself, which keeps working even if a modded
        // explosion forgets to pass the causing entity as the attacker.
        val mob = e.attacker as? MobEntity
            ?: (e.source.source as? MobEntity).takeIf { explosion }
            ?: return
        val level = (mob as? LevelledMob)?.mobLevel ?: 0
        if (level <= 1) return

        // Plain melee: the attribute already scaled it, scaling the event too would double-dip.
        if (!projectile && !explosion &&
            (e.source.name == "mob" || e.source.name == "mob_attack_no_aggro")
        ) {
            return
        }

        val multiplier = if (explosion) MobLevels.explosionMultiplier(level) else MobLevels.attackMultiplier(level)
        e.amount *= multiplier
        FSTLog.log("MobAbility") {
            "mob=${mob.type} level=$level kind=${e.source.name} projectile=$projectile " +
                "outcome=scaled before=${e.amount / multiplier} after=${e.amount} multiplier=$multiplier"
        }
    }
}
