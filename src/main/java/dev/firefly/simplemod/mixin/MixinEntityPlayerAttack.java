package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.enchantments.EnchantInfinitePower;
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantInfinitePowerHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayerAttack {

    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"), cancellable = true)
    private void onAttackTarget(Entity target, CallbackInfo ci) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (player.getEntityWorld().isRemote) {
            return;
        }

        ItemStack mainHand = player.getHeldItemMainhand();
        int level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, mainHand);
        if (level <= 0) {
            return;
        }

        if (!(target instanceof EntityLivingBase)) {
            return;
        }

        EnchantInfinitePowerHandler.handleAttack(player, (EntityLivingBase) target, player.getEntityWorld());

        ci.cancel();
    }
}