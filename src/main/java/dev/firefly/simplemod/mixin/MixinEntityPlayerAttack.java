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
        // 只在服务端执行
        if (player.getEntityWorld().isRemote) {
            return;
        }

        // 检查主手是否持有虚空附魔
        ItemStack mainHand = player.getHeldItemMainhand();
        int level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.Companion.getINSTANCE(), mainHand);
        if (level <= 0) {
            return;
        }

        // 目标必须是生物
        if (!(target instanceof EntityLivingBase)) {
            return;
        }

        // 调用 Kotlin 业务逻辑（静态方法）
        EnchantInfinitePowerHandler.handleAttack(player, (EntityLivingBase) target, player.getEntityWorld());

        // 取消原版攻击处理，避免重复伤害
        ci.cancel();
    }
}