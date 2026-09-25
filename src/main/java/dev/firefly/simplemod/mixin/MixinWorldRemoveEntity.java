package dev.firefly.simplemod.mixin;

import dev.firefly.simplemod.enchantments.mythic.EnchantInfinitePower;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldRemoveEntity {

    @Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true)
    private void onRemoveEntity(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        ItemStack stack = player.getHeldItemMainhand();
        int level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack);
        if (level > 0) {
            ci.cancel();
        }
    }
}