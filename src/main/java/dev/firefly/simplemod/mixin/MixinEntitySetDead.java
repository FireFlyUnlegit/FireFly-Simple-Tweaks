package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.enchantments.EnchantInfinitePower;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntitySetDead {

    @Inject(method = "setDead", at = @At("HEAD"), cancellable = true)
    private void onSetDead(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) self;
        ItemStack stack = player.getHeldItemMainhand();
        int level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.Companion.getINSTANCE(), stack);
        if (level > 0) {
            ci.cancel();
        }
    }
}