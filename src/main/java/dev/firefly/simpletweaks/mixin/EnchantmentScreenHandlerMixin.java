package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.STLog;
import dev.firefly.simpletweaks.core.EnchantTableGate;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.12.2 `MixinContainerEnchantment` — the "already-enchanted items and enchanted books can
 * be enchanted again, and re-enchanting <b>replaces</b> the old enchantments" behaviour (B-2a).
 * `ContainerEnchantment` is `EnchantmentScreenHandler` in 1.21.
 *
 * <p>All three changes below are unconditional, exactly as in 1.12.2: the original had no config
 * gate on this mixin, and inventing one would silently change behaviour for anyone who flips an
 * unrelated option.
 *
 * <h2>Yarn 1.21.1 ground truth (bytecode, per MIGRATION.md 铁律二)</h2>
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
 * <h2>(3) Replace instead of accumulate — the real root cause, and why three attempts failed</h2>
 * 1.12.2 cleared the old {@code ench} tag <b>between</b> the offer generation and the add loop, so the
 * loop wrote onto a clean stack. Three attempts were needed here and the first two, plus the third's
 * first revision, all failed <b>the same way</b>: the item was left stripped with nothing added.
 *
 * <p>That shared symptom was the clue, and it was not the injection point — it was <b>removing the
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
 * left empty — and because the component then stays absent, the table could not offer anything
 * afterwards either.
 *
 * <p><b>The fix is to empty the component rather than delete it</b> — {@code set(DEFAULT)} keeps
 * {@code get(type)} non-null, so the loop writes normally. The component type is mirrored from
 * {@code getEnchantmentsComponentType} above, because an enchanted book stores its enchantments in
 * {@code STORED_ENCHANTMENTS} and clearing the wrong one would be a no-op.
 *
 * <table>
 *   <tr><th>attempt</th><th>anchor</th><th>clear method</th><th>result</th></tr>
 *   <tr><td>1</td><td>HEAD</td><td>remove</td><td>stripped, nothing added</td></tr>
 *   <tr><td>2</td><td>{@code @Redirect addEnchantment}</td><td>remove</td><td>stripped, nothing added</td></tr>
 *   <tr><td>3a</td><td>after {@code applyEnchantmentCosts}</td><td>remove</td><td>stripped, nothing added</td></tr>
 *   <tr><td><b>3b</b></td><td>after {@code applyEnchantmentCosts}</td><td><b>set(DEFAULT)</b></td><td>—</td></tr>
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
     * <p>The parameter list must be the full target signature plus the callback — Mixin only permits
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

        if (STLog.INSTANCE.getEnabled()) {
            LOGGER.info("[enchant-table] re-enchant on {}: replaced {} existing enchantment(s)",
                    Registries.ITEM.getId(stack.getItem()), old.getEnchantments().size());
        }
    }
}
