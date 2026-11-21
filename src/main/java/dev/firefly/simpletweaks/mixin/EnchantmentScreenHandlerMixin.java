package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.FSTLog;
import dev.firefly.simpletweaks.core.EnchantTableGate;
import dev.firefly.simpletweaks.core.AdvancedEnchantMode;
import dev.firefly.simpletweaks.client.AdvancedOffers;
import dev.firefly.simpletweaks.util.AdvancedLevels;
import dev.firefly.simpletweaks.util.UpgradeEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.12.2 `MixinContainerEnchantment` 鈥?the "already-enchanted items and enchanted books can
 * be enchanted again, and re-enchanting <b>replaces</b> the old enchantments" behaviour (B-2a).
 * `ContainerEnchantment` is `EnchantmentScreenHandler` in 1.21.
 *
 * <p>All three changes below are unconditional, exactly as in 1.12.2: the original had no config
 * gate on this mixin, and inventing one would silently change behaviour for anyone who flips an
 * unrelated option.
 *
 * <h2>Yarn 1.21.1 ground truth (bytecode, per MIGRATION.md 閾佸緥浜?</h2>
 * <pre>
 *   onContentChanged(Inventory)   gate: !isEmpty() &amp;&amp; isEnchantable()   (offsets 17..27)
 *   method_17410(ItemStack,int,PlayerEntity,int,ItemStack,World,BlockPos)
 *       0        local8 = arg1                       (the slot-0 stack OBJECT, not a copy)
 *      18        list = generateEnchantments(..., enchantmentPower[slotId])
 *      23..30    if (list.isEmpty()) goto 240        &lt;-- EARLY RETURN (ifne 240)
 *      33..38    player.applyEnchantmentCosts(local8, level)   &lt;-- (3) anchors here
 *      41..49    local8.isOf(Items.BOOK)             &lt;-- (2) redirects here
 *      52..68    if BOOK { local8 = arg1.withItem(ENCHANTED_BOOK);
 *                           inventory.setStack(0, local8); }
 *      73..119   for (entry : list) local8.addEnchantment(entry.enchantment, entry.level)
 *     122..127   lapis.decrementUnlessCreative(level, player)
 *     188..196   seed.set(player.getEnchantmentTableSeed())
 *     199..204   onContentChanged(inventory)         &lt;-- re-zeroes power/id/level
 *     240        return
 * </pre>
 * <b>`method_17410` is genuinely unmapped in yarn 1.21.1+build.3</b>; Mixin's refmap step leaves an
 * unmapped name untouched and the runtime class carries that name, so targeting it works. Every
 * offset above was read out of
 * `minecraft-merged-1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2.jar`, not inferred.
 *
 * <h2>(3) Replace instead of accumulate 鈥?the real root cause, and why three attempts failed</h2>
 * 1.12.2 cleared the old {@code ench} tag <b>between</b> the offer generation and the add loop, so the
 * loop wrote onto a clean stack. Three attempts were needed here and the first two, plus the third's
 * first revision, all failed <b>the same way</b>: the item was left stripped with nothing added.
 *
 * <p>That shared symptom was the clue, and it was not the injection point 鈥?it was <b>removing the
 * component at all</b>. {@code method_17410} writes through {@code ItemStack#addEnchantment}, which
 * is a thin wrapper over {@code EnchantmentHelper#apply(ItemStack, Consumer)}:
 * <pre>
 *   EnchantmentHelper.apply(ItemStack stack, Consumer&lt;Builder&gt; consumer)
 *       0: type = getEnchantmentsComponentType(stack)   // ENCHANTED_BOOK ? STORED_ENCHANTMENTS : ENCHANTMENTS
 *       5: component = stack.get(type)
 *      14: if (component == null) return ItemEnchantmentsComponent.DEFAULT;   &lt;-- SILENT NO-OP
 *      22: builder = new Builder(component); consumer.accept(builder);
 *      51: stack.set(type, builder.build())
 * </pre>
 * So after {@code stack.remove(ENCHANTMENTS)} every following {@code addEnchantment} returns
 * {@code DEFAULT} without writing anything. The clear ran, the adds were discarded, and the item was
 * left empty 鈥?and because the component then stays absent, the table could not offer anything
 * afterwards either.
 *
 * <p><b>The fix is to empty the component rather than delete it</b> 鈥?{@code set(DEFAULT)} keeps
 * {@code get(type)} non-null, so the loop writes normally. The component type is mirrored from
 * {@code getEnchantmentsComponentType} above, because an enchanted book stores its enchantments in
 * {@code STORED_ENCHANTMENTS} and clearing the wrong one would be a no-op.
 *
 * <table>
 *   <tr><th>attempt</th><th>anchor</th><th>clear method</th><th>result</th></tr>
 *   <tr><td>1</td><td>HEAD</td><td>remove</td><td>stripped, nothing added</td></tr>
 *   <tr><td>2</td><td>{@code @Redirect addEnchantment}</td><td>remove</td><td>stripped, nothing added</td></tr>
 *   <tr><td>3a</td><td>after {@code applyEnchantmentCosts}</td><td>remove</td><td>stripped, nothing added</td></tr>
 *   <tr><td><b>3b</b></td><td>after {@code applyEnchantmentCosts}</td><td><b>set(DEFAULT)</b></td><td>鈥?/td></tr>
 * </table>
 *
 * <p>The anchor still matters for a second reason: it sits after the {@code ifne 240} early return, so
 * the callback only runs when there is something to write. But anchoring alone was never sufficient,
 * and the earlier revision of this file wrongly claimed it was.
 *
 * <p>A log line is emitted per replacement. It exists because this seam has now been wrong four
 * times, and "the hook never ran" and "the hook ran but the writes were discarded" were
 * indistinguishable from the player's side.
 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    /** The table handler never kept its player, but the flag lives on the player, so keep it here. */
    @Unique
    private PlayerEntity simpletweaks$owner;

    @Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V",
            at = @At("RETURN"))
    private void simpletweaks$rememberOwner(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory,
            net.minecraft.screen.ScreenHandlerContext context, CallbackInfo ci) {
        this.simpletweaks$owner = playerInventory.player;
    }

    @Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;)V", at = @At("RETURN"))
    private void simpletweaks$rememberOwner(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory,
            CallbackInfo ci) {
        this.simpletweaks$owner = playerInventory.player;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("simple_tweaks/enchant_table");

    /** The unmapped method that applies the enchantments. See the class KDoc. */
    private static final String APPLY_METHOD =
            "method_17410(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/player/PlayerEntity;"
                    + "ILnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/util/math/BlockPos;)V";

    /**
     * (1) 1.12.2 `@Redirect` on `ItemStack#isItemEnchantable` in `onCraftMatrixChanged`.
     *
     * The predicate itself lives in [EnchantTableGate] rather than inline here; see that class's KDoc
     * for the three failure modes (redirect never ran / predicate false / offers downstream) that an
     * inline predicate cannot tell apart from the outside.
     */
    @Redirect(
            method = "onContentChanged(Lnet/minecraft/inventory/Inventory;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEnchantable()Z")
    )
    private boolean simpletweaks$allowReEnchant(ItemStack stack) {
        return EnchantTableGate.canEnchant(stack);
    }

    /**
     * (2) 1.12.2 `@Redirect` on `ItemStack#getItem` (ordinal 0) in `enchantItem`, which mapped
     * `ENCHANTED_BOOK -> BOOK` so an enchanted book took the "this is a book" branch.
     *
     * 1.21 asks {@code stack.isOf(Items.BOOK)} instead of comparing `getItem()`, so the redirect moves
     * to the predicate. It is a **strict superset** of vanilla behaviour (every other item falls
     * through to {@code stack.isOf(item)}), which is why redirecting every `isOf` call in this method
     * without an `ordinal` is safe rather than merely convenient.
     *
     * <p>Note this alone does not make enchanted books work: `Items.ENCHANTED_BOOK.getEnchantability()`
     * is **0**, and `EnchantmentHelper#generateEnchantments` returns an empty list immediately when
     * enchantability is &le; 0, so the book never receives an offer. 1.12.2 fixed exactly that with a
     * separate redirect in `MixinEnchantmentHelper`; that part is deliberately left for later.
     */
    @Redirect(
            method = APPLY_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z")
    )
    private boolean simpletweaks$enchantedBookCountsAsBook(ItemStack stack, Item item) {
        if (item == Items.BOOK && stack.isOf(Items.ENCHANTED_BOOK)) {
            return true;
        }
        return stack.isOf(item);
    }

    /**
     * (3) 1.12.2's `@Inject` after `EntityPlayer#onEnchant`, which removed the `ench` NBT tag so the
     * add loop replaced rather than accumulated. 1.21 removed `onEnchant`; the equivalent moment is
     * immediately after `PlayerEntity#applyEnchantmentCosts`, which is the first instruction past the
     * empty-offer early return. See the class KDoc for why emptying (not removing) the component is
     * the part that actually matters.
     *
     * <p>The parameter list must be the full target signature plus the callback 鈥?Mixin only permits
     * dropping arguments from the end, and `stack` (the first one) is the one that matters.
     */
    @Inject(
            method = APPLY_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;applyEnchantmentCosts(Lnet/minecraft/item/ItemStack;I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void simpletweaks$replaceInsteadOfAccumulate(ItemStack stack, int slotId, PlayerEntity player, int level,
            ItemStack lapis, World world, BlockPos pos, CallbackInfo ci) {
        // Mirrors EnchantmentHelper#getEnchantmentsComponentType, read from its bytecode:
        // `stack.isOf(Items.ENCHANTED_BOOK) ? STORED_ENCHANTMENTS : ENCHANTMENTS`.
        ComponentType<ItemEnchantmentsComponent> type = stack.isOf(Items.ENCHANTED_BOOK)
                ? DataComponentTypes.STORED_ENCHANTMENTS
                : DataComponentTypes.ENCHANTMENTS;

        ItemEnchantmentsComponent old = stack.get(type);

        if (old == null || old.isEmpty()) {
            // Nothing to replace. Returning here also keeps a plain BOOK untouched: writing an empty
            // ENCHANTMENTS component onto it would leak into the ENCHANTED_BOOK copy made at offset 52.
            return;
        }

        // `set(DEFAULT)`, **not** `remove`: EnchantmentHelper#apply returns early when the component
        // is absent, which silently discards every following addEnchantment. See the class KDoc.
        stack.set(type, ItemEnchantmentsComponent.DEFAULT);

        if (FSTLog.INSTANCE.getEnabled()) {
            LOGGER.info("[enchant-table] re-enchant on {}: replaced {} existing enchantment(s)",
                    Registries.ITEM.getId(stack.getItem()), old.getEnchantments().size());
        }
    }
    /**
     * (4) Advanced mode, offer side: mark the roll so {@code getPossibleEntries}'s post-processing
     * skips the "at most one simple_tweaks enchantment" cap, then append one advanced entry when the
     * stack may legally carry it (a book always may; equipment only when the base form is already at
     * its own max level).
     */
    @Inject(method = "generateEnchantments", at = @At("HEAD"))
    private void simpletweaks$beginAdvancedRoll(DynamicRegistryManager registryManager, ItemStack stack,
            int slot, int level, CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        AdvancedEnchantMode.INSTANCE.beginRoll(simpletweaks$owner != null && AdvancedEnchantMode.INSTANCE.isOn(simpletweaks$owner));
    }

    @Inject(method = "generateEnchantments", at = @At("RETURN"), cancellable = true)
    private void simpletweaks$advancedOffer(DynamicRegistryManager registryManager, ItemStack stack,
            int slot, int level, CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        boolean advanced = AdvancedEnchantMode.INSTANCE.inAdvancedRoll();
        AdvancedEnchantMode.INSTANCE.endRoll();
        if (!advanced || stack.isEmpty()) {
            return;
        }
        List<EnchantmentLevelEntry> base = cir.getReturnValue();
        if (base == null) {
            return;
        }
        List<EnchantmentLevelEntry> list = new ArrayList<>(base);
        // Advanced picks are capped at power / 30; every pick the cap refuses turns into a normal one.
        int converted = simpletweaks$appendAdvanced(registryManager, stack, list, AdvancedEnchantMode.INSTANCE.advancedBudget(level));
        // Advanced mode guarantees power / 12 entries, so a 64-power roll is never a single line.
        int minimum = level / 12 + converted;
        if (list.size() < minimum) {
            simpletweaks$appendExtraNormals(registryManager, stack, list, minimum - list.size());
        }

        FSTLog.INSTANCE.log("AdvancedOffer", "power=" + level + ", entries=" + list.size());
        cir.setReturnValue(list);
    }

    /**
     * Advanced mode also widens the ordinary half of the offer: the extra entries are drawn from the
     * same weighted pool vanilla uses, so advanced-ness still comes from `appendAdvanced` alone.
     */
    private static void simpletweaks$appendExtraNormals(DynamicRegistryManager registryManager, ItemStack stack,
            List<EnchantmentLevelEntry> list, int wanted) {
        Registry<Enchantment> registry = registryManager.get(RegistryKeys.ENCHANTMENT);
        boolean book = UpgradeEnchantments.INSTANCE.isBook(stack);
        List<RegistryEntry<Enchantment>> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (RegistryEntry<Enchantment> reference : registry.getIndexedEntries()) {
            RegistryKey<Enchantment> key = reference.getKey().orElse(null);
            if (key == null) continue;
            if (UpgradeEnchantments.INSTANCE.isUpgrade(key)) continue;
            if (!AdvancedOffers.allowed(key.getValue().getPath())) continue;
            Enchantment enchantment = reference.value();
            if (!book && !enchantment.isAcceptableItem(stack)) continue;
            boolean already = false;
            for (EnchantmentLevelEntry entry : list) {
                if (entry.enchantment.matchesKey(key)) { already = true; break; }
            }
            if (already) continue;
            int weight = Math.max(1, enchantment.getWeight());
            pool.add(reference);
            weights.add(weight);
            total += weight;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int added = 0; added < wanted && !pool.isEmpty() && total > 0; added++) {
            int roll = random.nextInt(total);
            int index = 0;
            for (; index < pool.size(); index++) {
                roll -= weights.get(index);
                if (roll < 0) break;
            }
            if (index >= pool.size()) index = pool.size() - 1;
            Enchantment picked = pool.get(index).value();
            int level = 1 + random.nextInt(Math.max(1, Math.min(3, picked.getMaxLevel())));
            list.add(new EnchantmentLevelEntry(pool.get(index), Math.min(level, picked.getMaxLevel())));
            total -= weights.remove(index);
            pool.remove(index);
        }
    }
    private static int simpletweaks$appendAdvanced(DynamicRegistryManager registryManager, ItemStack stack,
            List<EnchantmentLevelEntry> list, int cap) {
        if (cap <= 0) {
            return 0;
        }
        Registry<Enchantment> registry = registryManager.get(RegistryKeys.ENCHANTMENT);
        List<UpgradeEnchantments.Rule> eligible = new ArrayList<>();
        for (UpgradeEnchantments.Rule rule : UpgradeEnchantments.INSTANCE.rules()) {
            boolean allowed = UpgradeEnchantments.INSTANCE.isBook(stack)
                    || UpgradeEnchantments.INSTANCE.baseAtMax(stack, rule);
            if (!allowed) {
                continue;
            }
            boolean already = false;
            for (EnchantmentLevelEntry entry : list) {
                if (entry.enchantment.matchesKey(rule.getUpgrade())) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                eligible.add(rule);
            }
        }
        if (eligible.isEmpty() || ThreadLocalRandom.current().nextFloat() > 0.45f) {
            return 0;
        }
        // The roll wants `wanted` picks, the power / 30 cap allows `cap`; the difference is returned so the
        // caller can turn it into ordinary picks.
        int wanted = Math.min(eligible.size(), 1 + eligible.size() / 6);
        int count = Math.min(wanted, cap);
        java.util.Collections.shuffle(eligible, ThreadLocalRandom.current());
        for (int added = 0; added < count; added++) {
            UpgradeEnchantments.Rule rule = eligible.get(added);
            RegistryEntry<Enchantment> entry = registry.getEntry(rule.getUpgrade()).orElse(null);
            if (entry != null) {
                list.add(new EnchantmentLevelEntry(entry, AdvancedLevels.pick(entry.value().getMaxLevel())));
            }
        }
        return Math.max(0, wanted - count);
    }

    /**
     * (5) Advanced mode, cost side. Vanilla charges {@code slot + 1} levels and as many lapis; advanced
     * mode charges the number printed next to the button and a third of it in lapis.
     *
     * <p>The guard has to run in {@code onButtonClick} (HEAD) as well: vanilla's own lapis check only
     * compares against {@code slot + 1}, so a player holding three lapis would otherwise pass the check
     * and have ten decremented from a stack of three.
     */
    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$advancedCostGate(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!AdvancedEnchantMode.INSTANCE.isOn(player)) {
            return;
        }
        int power = ((EnchantmentScreenHandler) (Object) this).enchantmentPower[id];
        if (power <= 0 || player.isCreative()) {
            return;
        }
        ItemStack lapis = ((EnchantmentScreenHandler) (Object) this).getSlot(1).getStack();
        int need = AdvancedEnchantMode.INSTANCE.lapisCost(power);
        int levels = AdvancedEnchantMode.INSTANCE.xpCost(power);
        if (lapis.isEmpty() || lapis.getCount() < need || player.experienceLevel < levels) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(
            method = APPLY_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;applyEnchantmentCosts(Lnet/minecraft/item/ItemStack;I)V")
    )
    private void simpletweaks$advancedXpCost(PlayerEntity player, ItemStack stack, int level) {
        dev.firefly.simpletweaks.compat.FSTLog.INSTANCE.log("AdvancedCost", "xp path advanced=" + AdvancedEnchantMode.INSTANCE.isOn(player) + ", vanillaLevel=" + level + ", applied=" + simpletweaks$advancedCost(player, level) + ", xpBefore=" + player.experienceLevel);
        player.applyEnchantmentCosts(stack, simpletweaks$advancedCost(player, level));
    }

    @Redirect(
            method = APPLY_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;decrementUnlessCreative(ILnet/minecraft/entity/LivingEntity;)V")
    )
    private void simpletweaks$advancedLapisCost(ItemStack lapis, int amount, net.minecraft.entity.LivingEntity owner) {
        dev.firefly.simpletweaks.compat.FSTLog.INSTANCE.log("AdvancedCost", "lapis path advanced=" + (owner instanceof PlayerEntity pl && AdvancedEnchantMode.INSTANCE.isOn(pl)) + ", amount=" + amount + ", levels=" + simpletweaks$advancedCost(owner instanceof PlayerEntity lp ? lp : null, amount) + ", have=" + lapis.getCount());
        int levels = simpletweaks$advancedCost(owner instanceof PlayerEntity p ? p : null, amount);
        int lapisCost = owner instanceof PlayerEntity p && AdvancedEnchantMode.INSTANCE.isOn(p)
                ? AdvancedEnchantMode.INSTANCE.lapisCost(levels)
                : amount;
        lapis.decrementUnlessCreative(lapisCost, owner);
    }

    private int simpletweaks$advancedCost(PlayerEntity player, int vanillaLevel) {
        if (player == null || !AdvancedEnchantMode.INSTANCE.isOn(player)) {
            return vanillaLevel;
        }
        int slot = vanillaLevel - 1;
        int[] power = ((EnchantmentScreenHandler) (Object) this).enchantmentPower;
        if (slot < 0 || slot >= power.length || power[slot] <= 0) {
            return vanillaLevel;
        }
        return AdvancedEnchantMode.INSTANCE.xpCost(power[slot]);
    }
}
