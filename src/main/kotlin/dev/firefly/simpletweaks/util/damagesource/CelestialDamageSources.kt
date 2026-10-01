package dev.firefly.simpletweaks.util.damagesource
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.DamageSource
object CelestialDamageSources {
    /**
     * 圣光粒子伤害：视为魔法伤害、玩家来源、不可格挡
     */
    @JvmStatic
    fun dealDamage(attacker: EntityPlayer): DamageSource {
        return object : DamageSource("celestial_particle") {
            override fun getTrueSource(): EntityLivingBase = attacker
            override fun getImmediateSource(): EntityLivingBase = attacker
            override fun isMagicDamage(): Boolean = true
            override fun isUnblockable(): Boolean = true
        }
    }
}