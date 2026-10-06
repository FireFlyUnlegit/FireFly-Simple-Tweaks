
package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantPiercingArrowHandler;
import dev.firefly.simpletweaks.interfaces.IPiercingArrow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityArrow.class)
public abstract class EntityArrowPiercingMixin extends Entity implements IPiercingArrow {

    @Unique
    private static final DataParameter<Byte> ST_PIERCING =
            EntityDataManager.createKey(EntityArrow.class, DataSerializers.BYTE);

    @Unique
    private static final DataParameter<Integer> ST_REMAINING =
            EntityDataManager.createKey(EntityArrow.class, DataSerializers.VARINT);

    @Unique
    private static final DataParameter<Integer> ST_MAX_PER_TARGET =
            EntityDataManager.createKey(EntityArrow.class, DataSerializers.VARINT);

    public EntityArrowPiercingMixin(World worldIn) {
        super(worldIn);
    }

    @Inject(method = "entityInit", at = @At("TAIL"))
    private void firefly$entityInit(CallbackInfo ci) {
        this.dataManager.register(ST_PIERCING, (byte) 0);
        this.dataManager.register(ST_REMAINING, 0);
        this.dataManager.register(ST_MAX_PER_TARGET, 0);
    }

    @Override
    public boolean simpletweaks$isPiercing() {
        return this.dataManager.get(ST_PIERCING) != 0;
    }

    @Override
    public void simpletweaks$setPiercing(boolean value) {
        this.dataManager.set(ST_PIERCING, (byte) (value ? 1 : 0));
    }

    @Override
    public int simpletweaks$getPierceRemaining() {
        return this.dataManager.get(ST_REMAINING);
    }

    @Override
    public void simpletweaks$setPierceRemaining(int value) {
        this.dataManager.set(ST_REMAINING, value);
    }

    @Override
    public int simpletweaks$getMaxPerTarget() {
        return this.dataManager.get(ST_MAX_PER_TARGET);
    }

    @Override
    public void simpletweaks$setMaxPerTarget(int value) {
        this.dataManager.set(ST_MAX_PER_TARGET, value);
    }

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void firefly$onHit(RayTraceResult result, CallbackInfo ci) {
        EntityArrow arrow = (EntityArrow) (Object) this;
        if (EnchantPiercingArrowHandler.handleHit(arrow, result)) {
            ci.cancel();
        }
    }
}