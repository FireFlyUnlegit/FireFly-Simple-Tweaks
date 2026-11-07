package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.FSTLog;
import dev.firefly.simpletweaks.util.mobs.MobLevels;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tops up a mob's equipment so the enchantment layer has something to work on.
 *
 * <h2>Why this exists</h2>
 * A real session produced **36 mod enchantments on `MAINHAND`, 1 on `CHEST` and 1 on `FEET`**. Armour
 * enchantments were effectively unreachable because vanilla almost never gives a mob armour, and
 * vanilla's own weapon chance for zombies is **1 % (5 % on hard)** — measured in `ZombieEntity#initEquipment`
 * bytecode (`ldc 0.01f` / `ldc 0.05f`). Since `MobEntity#enchantEquipment` returns immediately on an empty
 * stack, an unarmed mob can never be enchanted at all.
 *
 * <h2>Why {@code updateEnchantments} HEAD and not {@code initEquipment}</h2>
 * `MobEntity#initialize` runs `initEquipment(...)` **first**, then `updateEnchantments(...)`. Injecting here
 * means every vanilla equipment decision has already been made (so nothing is ever overwritten — the
 * checks below skip non-empty slots), while the private `enchantEquipment` has **not** run yet, so the gear
 * added here is exactly the gear that gets rolled for enchantments. Doing it at `initEquipment` would race
 * with `ZombieEntity`'s own sword roll, which happens *after* the base method returns.
 *
 * <h2>Coverage</h2>
 * `ZombieEntity` covers the whole zombie family (`Husk`, `Drowned`, `ZombieVillager`, `ZombifiedPiglin` all
 * extend it), and `AbstractSkeletonEntity` covers `Skeleton`, `Stray` and `Bogged` — which matters because
 * skeletons have a bow but no armour, so armour enchantments could never appear on them either.
 *
 * <p>Mobs without hands (`Creeper`, `Spider`, `Slime`, …) are deliberately excluded: a sword-equipped creeper
 * is not "harder", it is a bug.
 *
 * <h2>Chances</h2>
 * Weapon and armour are rolled separately, per slot, and scale with `/simple_tweaks difficulty`.
 *
 * <h2>Drops</h2>
 * Anything added here **can be dropped** by vanilla's equipment drop chance — but every `simple_tweaks:*`
 * enchantment is stripped again in `MobEnchantments.stripAll`, which runs at `MobEntity#dropEquipment` HEAD.
 * That strip already covers all six slots and is exercised by the same log (`CHEST`, `LEGS`, `FEET` lines).
 */
@Mixin(MobEntity.class)
public abstract class MobEquipmentTopUpMixin {

    /** Weapon chance below hard difficulty. Vanilla is `0.01`. */
    private static final float WEAPON_CHANCE_NORMAL = 0.12f;

    /** Weapon chance on hard. Vanilla is `0.05`. */
    private static final float WEAPON_CHANCE_HARD = 0.30f;

    /** Per-armour-slot chance below hard. Vanilla gives zombies armour far more rarely than this. */
    private static final float ARMOR_CHANCE_NORMAL = 0.10f;

    /** Per-armour-slot chance on hard. */
    private static final float ARMOR_CHANCE_HARD = 0.22f;

    /** Each point of `/simple_tweaks difficulty` adds this much to both chances. */
    private static final float CHANCE_PER_OFFSET = 0.04f;

    /** Ceiling for both, so a large debug offset cannot arm and plate the entire world. */
    private static final float MAX_CHANCE = 0.75f;

    /** Log suffix per armour slot, index-aligned with [ARMOR_SLOTS]. */
    private static final String[] SLOT_SUFFIX = { "helmet", "chestplate", "leggings", "boots" };
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    /**
     * Armour materials, **worst to best**. A roll picks an index, and `/simple_tweaks difficulty` shifts
     * the roll upward, so a plain world sees mostly leather/chain while a high-offset test world sees
     * iron and the occasional diamond piece.
     *
     * Why not a fixed iron set: the author asked for variety, and a mob in leather is a visibly different
     * encounter from one in diamond — with the same enchantment layer on top.
     */
    private static final Item[][] ARMOR_BY_MATERIAL = {
            // leather
            { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
            // chainmail
            { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
                    Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
            // iron
            { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
            // golden
            { Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS },
            // diamond
            { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
    };

    /** Registry id prefix per material, index-aligned with [ARMOR_BY_MATERIAL], for the log only. */
    private static final String[] MATERIAL_IDS = {
            "minecraft:leather", "minecraft:chainmail", "minecraft:iron",
            "minecraft:golden", "minecraft:diamond",
    };

    @Inject(
            method = "updateEnchantments(Lnet/minecraft/world/ServerWorldAccess;"
                    + "Lnet/minecraft/util/math/random/Random;Lnet/minecraft/world/LocalDifficulty;)V",
            at = @At("HEAD")
    )
    private void simpletweaks$topUpEquipment(ServerWorldAccess world, Random random, LocalDifficulty difficulty,
            CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        if (!(self instanceof ZombieEntity) && !(self instanceof AbstractSkeletonEntity)) {
            return;
        }

        boolean hard = self.getWorld().getDifficulty() == Difficulty.HARD;
        float offset = MobLevels.getDifficultyOffset();

        float weaponChance = Math.min(
                (hard ? WEAPON_CHANCE_HARD : WEAPON_CHANCE_NORMAL) + offset * CHANCE_PER_OFFSET,
                MAX_CHANCE);
        if (self.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() && random.nextFloat() < weaponChance) {
            self.equipStack(EquipmentSlot.MAINHAND, worn(new ItemStack(Items.IRON_SWORD), random));
            log(self, EquipmentSlot.MAINHAND.getName(), "minecraft:iron_sword", weaponChance);
        }

        float armorChance = Math.min(
                (hard ? ARMOR_CHANCE_HARD : ARMOR_CHANCE_NORMAL) + offset * CHANCE_PER_OFFSET,
                MAX_CHANCE);
        // One material roll per mob (not per slot), so a mob's set looks like a set rather than four
        // independently-generated pieces.
        int material = pickMaterial(random, offset);
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            EquipmentSlot slot = ARMOR_SLOTS[i];
            if (!self.getEquippedStack(slot).isEmpty()) {
                continue;
            }
            if (random.nextFloat() >= armorChance) {
                continue;
            }
            self.equipStack(slot, worn(new ItemStack(ARMOR_BY_MATERIAL[material][i]), random));
            log(self, slot.getName(), MATERIAL_IDS[material] + "_" + SLOT_SUFFIX[i], armorChance);
        }
    }

    /**
     * Weighted material pick, shifted upward by the debug difficulty offset.
     *
     * `roll = random + offset * 0.08`, clamped to `[0, 0.999]`, mapped onto the five materials: leather is
     * the widest band (so plain worlds stay light), diamond only appears above ~`0.8`, which needs an
     * offset of about 10.
     */
    private static int pickMaterial(Random random, float offset) {
        float roll = random.nextFloat() + offset * 0.08f;
        if (roll < 0.35f) {
            return 0;
        }
        if (roll < 0.62f) {
            return 1;
        }
        if (roll < 0.83f) {
            return 2;
        }
        if (roll < 0.95f) {
            return 3;
        }
        return 4;
    }

    /** Same shape as vanilla's zombie sword: partly worn rather than pristine. */
    private static ItemStack worn(ItemStack stack, Random random) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 1) {
            stack.setDamage(random.nextInt(maxDamage / 2));
        }
        return stack;
    }

    /**
     * `FSTLog.log`'s lambda overload is Kotlin `inline`, so it does not exist as a JVM method — from Java
     * the `enabled` guard has to be explicit to keep the string from being built on a normal run.
     */
    private static void log(MobEntity mob, String slot, String item, float chance) {
        if (!FSTLog.INSTANCE.getEnabled()) {
            return;
        }
        FSTLog.INSTANCE.log("MobEnchant",
                "mob=" + mob.getType().getName().getString()
                        + ", slot=" + slot
                        + ", item=" + item
                        + ", chance=" + String.format("%.2f", chance)
                        + ", outcome=mob-given-equipment");
    }
}
