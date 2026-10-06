package dev.firefly.simpletweaks.mixin.accessor;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseAccessor {

    @Accessor("recentlyHit")
    int getRecentlyHit();

    @Accessor("recentlyHit")
    void setRecentlyHit(int value);

    @Accessor("attackingPlayer")
    EntityPlayer getAttackingPlayer();

    @Accessor("attackingPlayer")
    void setAttackingPlayer(EntityPlayer player);

    @Accessor("lastDamage")
    float getLastDamage();

    @Accessor("lastDamage")
    void setLastDamage(float value);
    @Invoker("applyArmorCalculations")
    float invokeApplyArmorCalculations(DamageSource source, float damage);

    @Invoker("applyPotionDamageCalculations")
    float invokeApplyPotionDamageCalculations(DamageSource source, float damage);

    @Invoker("playHurtSound")
    void invokePlayHurtSound(DamageSource source);

    @Invoker("getHurtSound")
    SoundEvent invokeGetHurtSound(DamageSource source);

    @Invoker("getSoundVolume")
    float invokeGetSoundVolume();

    @Invoker("getSoundPitch")
    float invokeGetSoundPitch();
}