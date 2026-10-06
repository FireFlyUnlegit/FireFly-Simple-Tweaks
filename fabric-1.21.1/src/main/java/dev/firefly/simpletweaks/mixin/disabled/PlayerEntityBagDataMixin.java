package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.InfiniteBagStore;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists the `infinite_power` bag on the player.
 *
 * <h2>Why this exists at all</h2>
 * 1.12.2 read and wrote the bag through Forge's `player.getEntityData()` — a persistent per-entity
 * NBT tag with a public accessor. 1.21 has no such map: a player's data is only reachable from inside
 * the NBT callbacks. So the bag is stored in
 * {@link InfiniteBagStore} between callbacks and copied in/out here, which is also what removes
 * 1.12.2's `SaveHandler` (its only job was `inv.markDirty()` on `PlayerEvent.SaveToFile`).
 *
 * <h2>Yarn 1.21.1 ground truth (from the bytecode, per MIGRATION.md 铁律二)</h2>
 * <pre>
 *   net/minecraft/entity/player/PlayerEntity -&gt;
 *       public void readCustomDataFromNbt(NbtCompound)
 *       public void writeCustomDataToNbt(NbtCompound)
 * </pre>
 * Both are <b>declared by {@code PlayerEntity} itself and are public</b> — not inherited from
 * {@code Entity}. That matters because of the {@code EntityDamageMixin} incident: a Mixin only
 * rewrites the body of the class it targets, so injecting at the declaring superclass would have been
 * silently dead. Here the declaring class is the target, so no override audit is needed beyond
 * confirming nothing else calls these before/after in a way that would double-apply.
 *
 * <p>TAIL rather than HEAD: vanilla's own fields must be read first (the world and inventory are
 * needed by {@code InfiniteBagStore}, which resolves registry entries through
 * {@code player.world.registryManager}), and on write vanilla's own data must be written before ours
 * so the compound is in a valid state either way.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBagDataMixin {

    @Inject(method = "readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    private void simpletweaks$readBag(NbtCompound nbt, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        InfiniteBagStore.readFromNbt(self, nbt);
    }

    @Inject(method = "writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    private void simpletweaks$writeBag(NbtCompound nbt, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        InfiniteBagStore.writeToNbt(self, nbt);
    }
}
