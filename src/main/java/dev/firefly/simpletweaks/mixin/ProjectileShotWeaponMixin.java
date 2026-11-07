package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.ShotWeapon;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snapshots the firing weapon onto every projectile at the moment its owner is set.
 *
 * <h2>Why {@code setOwner} and not {@code BowItem}</h2>
 * {@code BowItem#onStoppedUsing} spawns its arrows through {@code shootAll}, which is private and creates
 * the entity internally — there is no local to capture the arrow from an injection there. {@code setOwner}
 * is called **inside** that shot, while the shooter still holds the bow, and it is the one seam shared by
 * bows, crossbows, tridents and any modded launcher that reuses {@code PersistentProjectileEntity}.
 *
 * <h2>Static gate (AGENTS.md §4.3)</h2>
 * {@code setOwner} is declared on {@code PersistentProjectileEntity} in 1.21.1 (`javap`: {@code public void
 * setOwner(Entity)}) and inherited from {@code ProjectileEntity}; injecting here runs for exactly the
 * projectile classes this feature is about, and the call inside {@code shootAll} resolves to it.
 *
 * <h2>What is stored</h2>
 * The whole weapon stack, encoded with {@code ItemStack.UNCOUNTED_CODEC}, under
 * {@code custom_data."simple_tweaks:shot_weapon"}. See {@link ShotWeapon} for why the snapshot is needed:
 * weapon-derived effects used to read whatever the player held when the arrow **landed**.
 *
 * <p>Client side is a no-op: the arrow entity exists on both sides, but the snapshot only matters where
 * damage is computed.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class ProjectileShotWeaponMixin {

    /**
     * The seam vanilla **actually** uses: `ArrowItem#createArrow` builds the arrow with
     * `(type, shooter, world, stack, weapon)` and never calls `setOwner` (`javap` on 1.21.1: the 4-arg
     * `ArrowEntity` constructor delegates straight to it). The `setOwner` injection below therefore never
     * fires for a normal bow shot — this one does.
     *
     * <p>The `weapon` argument is exactly the firing weapon vanilla recorded, so it is preferred over
     * reading the shooter's hand.
     */
    @Inject(
        method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/entity/LivingEntity;" +
            "Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
        at = @At("TAIL")
    )
    private void simpletweaks$snapshotOnConstruct(
        net.minecraft.entity.EntityType<?> type,
        net.minecraft.entity.LivingEntity owner,
        net.minecraft.world.World world,
        ItemStack stack,
        ItemStack weapon,
        CallbackInfo ci
    ) {
        if (!(owner instanceof PlayerEntity player)) {
            return;
        }
        if (world.isClient()) {
            return;
        }
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        ItemStack chosen = (weapon != null && !weapon.isEmpty()) ? weapon : player.getMainHandStack();
        ShotWeapon.INSTANCE.record(self, chosen);
    }

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void simpletweaks$snapshotShotWeapon(Entity owner, CallbackInfo ci) {
        if (!(owner instanceof PlayerEntity player)) {
            return;
        }
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (self.getWorld().isClient()) {
            return;
        }
        ShotWeapon.INSTANCE.record(self, player.getMainHandStack());
    }

    /**
     * Persists the snapshot into the projectile's own NBT, so an arrow saved mid-flight keeps the weapon
     * it was fired with. Without this the memory map is lost on chunk save and the arrow falls back to
     * the shooter's **current** hand — the original bug, one reload later.
     */
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void simpletweaks$writeShotWeapon(NbtCompound nbt, CallbackInfo ci) {
        ShotWeapon.INSTANCE.writeTo((PersistentProjectileEntity) (Object) this, nbt);
    }

    /** Restores the snapshot written by the injection above. */
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void simpletweaks$readShotWeapon(NbtCompound nbt, CallbackInfo ci) {
        ShotWeapon.INSTANCE.readFrom((PersistentProjectileEntity) (Object) this, nbt);
    }
}
