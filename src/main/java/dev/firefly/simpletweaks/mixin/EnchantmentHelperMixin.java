package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.EnchantTableGate;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Port of 1.12.2 `MixinEnchantmentHelper` — the two enchantment-**generation** behaviours that the
 * enchanting table needs. The table's own mixin is {@link EnchantmentScreenHandlerMixin}; this one
 * covers the helpers it calls.
 *
 * <h2>(a) Enchanted books must be able to receive an offer at all</h2>
 * 1.12.2 redirected {@code Item#getItemEnchantability(ItemStack)} inside `buildEnchantmentList` and
 * returned 15 when the item was an `ENCHANTED_BOOK` (whose enchantability is 0). 1.21 dropped the
 * stack parameter from that getter, so the same intent needs the redirect in <b>two</b> places —
 * 1.12.2 needed a second hook in `ContainerEnchantment` for exactly the same reason:
 * <table>
 *   <tr><th>1.21.1 method</th><th>why it is needed</th></tr>
 *   <tr><td>{@code calculateRequiredExperienceLevel}</td>
 *       <td>offset 6..19: {@code i = item.getEnchantability(); if (i <= 0) return 0;} — without this
 *           the table's offer power for a book is <b>0</b>, so {@code method_17410} returns before it
 *           ever generates anything (its guard is {@code enchantmentPower[id] <= 0})</td></tr>
 *   <tr><td>{@code generateEnchantments}</td>
 *       <td>offset 13..25: the same {@code i <= 0 → return empty list} early return, this time on the
 *           list itself</td></tr>
 * </table>
 * Both are `@Redirect`s on {@code Item#getEnchantability()I}; the call's receiver <i>is</i> the item,
 * so the value 1.12.2 read off the stack is available here without the stack.
 *
 * <p><b>Override audit:</b> both targets are {@code EnchantmentHelper}'s own static methods, and
 * neither is overridden anywhere (the class is final and has no subclasses), so the injections run.
 *
 * <h2>(b) Minimum enchantment count (1.12.2 `simpletweaks$ensureMinEnchants`)</h2>
 * 1.12.2 injected at the RETURN of `buildEnchantmentList`, computed `minEnchants = level / 15`, and
 * topped the generated list up to that size by repeatedly picking a random compatible enchantment
 * (max 30 attempts). The 1.21.1 equivalent of `buildEnchantmentList` is
 * {@code generateEnchantments(Random, ItemStack, int, Stream)}, so the top-up injects at its RETURN.
 *
 * <p>The one thing that needed a bridge: 1.12.2's version iterated {@code Enchantment.REGISTRY} to
 * find candidates, whereas 1.21.1 filters through the {@code #minecraft:in_enchanting_table} tag and
 * hands the result to {@code EnchantmentHelper#getPossibleEntries}. That method's return value is the
 * exact candidate set — already filtered by power, by {@code isPrimaryItem(stack)} and by the
 * enchantment table tag (which is also what excludes treasure enchantments, replacing 1.12.2's
 * `allowTreasure` flag) — so it is captured into a {@link ThreadLocal} at its RETURN and reused here.
 * That is strictly better than re-deriving candidates: the top-up can only offer enchantments the
 * table itself would have offered at this power.
 *
 * <p>Deviations from 1.12.2, both minor:
 * <ul>
 *   <li>The weighted pick uses {@code Enchantment#getWeight()} (the JSON {@code weight}) rather than
 *       1.12.2's `ModEnchantments.getCategory().getWeight()`. In 1.21 the category weight is already
 *       carried by that field, so this is the same number reached through the registry.</li>
 *   <li>{@code isPrimaryItem(stack)} replaces {@code canApply(stack)}; {@code getPossibleEntries} has
 *       already applied the former, so this is a re-check rather than a new filter.</li>
 * </ul>
 *
 * <h2>(c) The bookshelf cap (`disableEnchantmentTableLimit` / `maxEnchantmentPower`)</h2>
 * 1.12.2's `MixinEnchantmentHelper#modifyMaxPower` was a `@ModifyConstant(intValue = 15)` on
 * `calcItemStackEnchantability`; the 1.21.1 equivalent is the same annotation on
 * {@code calculateRequiredExperienceLevel}. See the hook itself for the two-constant analysis.
 *
 * <p>This config pair was **persisted but inert** in the port until now: `maxEnchantmentPower` was
 * written to and read from `config/simple_tweaks.json`, and the config screen exposed the toggle, but
 * nothing consumed the value.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    private static final String CALCULATE =
            "calculateRequiredExperienceLevel(Lnet/minecraft/util/math/random/Random;II"
                    + "Lnet/minecraft/item/ItemStack;)I";

    private static final String GENERATE =
            "generateEnchantments(Lnet/minecraft/util/math/random/Random;Lnet/minecraft/item/ItemStack;"
                    + "ILjava/util/stream/Stream;)Ljava/util/List;";

    private static final String POSSIBLE =
            "getPossibleEntries(ILnet/minecraft/item/ItemStack;Ljava/util/stream/Stream;)Ljava/util/List;";

    /** 1.12.2's hard-coded value for `ENCHANTED_BOOK` in both of its book hooks. */
    private static final int BOOK_ENCHANTABILITY = 15;

    /** 1.12.2's attempt cap in `simpletweaks$ensureMinEnchants`. */
    private static final int MAX_ATTEMPTS = 30;

    /** Candidate set for the invocation in progress. Thread-local: this runs per player per click. */
    private static final ThreadLocal<List<EnchantmentLevelEntry>> CANDIDATES = new ThreadLocal<>();

    @Redirect(
            method = CALCULATE,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;getEnchantability()I")
    )
    private static int simpletweaks$bookOfferPower(Item item) {
        return simpletweaks$enchantability(item);
    }

    @Redirect(
            method = GENERATE,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;getEnchantability()I")
    )
    private static int simpletweaks$bookEnchantability(Item item) {
        return simpletweaks$enchantability(item);
    }

    private static int simpletweaks$enchantability(Item item) {
        int result = item.getEnchantability();
        if (result <= 0 && item == Items.ENCHANTED_BOOK) {
            return BOOK_ENCHANTABILITY;
        }
        return result;
    }

    /**
     * (c) The enchanting-table bookshelf cap — 1.12.2 `MixinEnchantmentHelper#modifyMaxPower`.
     *
     * `calculateRequiredExperienceLevel` clamps the bookshelf count with the literal 15 twice, and a
     * scan of the method shows those are the **only** two `bipush 15` in it:
     * <pre>
     *   20: iload_2      (bookshelfCount)
     *   21: bipush 15    &lt;-- comparison operand
     *   23: if_icmple 29
     *   26: bipush 15    &lt;-- clamp value
     *   28: istore_2
     * </pre>
     * Replacing both with the configured value yields exactly `if (bookshelfCount > max) bookshelfCount = max;`
     * — the same shape 1.12.2 produced. Replacing only one would produce a broken pair, which is why
     * this uses no `ordinal` and deliberately matches every occurrence.
     */
    @ModifyConstant(method = CALCULATE, constant = @Constant(intValue = 15))
    private static int simpletweaks$maxEnchantmentPower(int original) {
        return EnchantTableGate.maxEnchantmentPower();
    }

    /**
     * (a2) {@code getPossibleEntries} must treat an enchanted book as a book — this is the piece that
     * actually made book enchanting fail, and it is the 1.21.1 form of 1.12.2's
     * {@code MixinEnchantmentHelper#firefly$getEnchantmentDatas}.
     *
     * <p>The candidate filter branches on a single boolean computed at offsets 4..11:
     * <pre>
     *   4: aload_1                        (stack)
     *   5: getstatic  Items.BOOK
     *   8: invokevirtual ItemStack.isOf
     *  11: istore 4                        isBook
     *  13: stream.filter(lambda(stack, isBook))
     * </pre>
     * {@code Items.ENCHANTED_BOOK.isOf(Items.BOOK)} is <b>false</b> — they are different items — so an
     * enchanted book took the "not a book" branch and was filtered with {@code isPrimaryItem(stack)}.
     * No enchantment lists an enchanted book as a primary item, so the candidate set came back
     * <b>empty</b>, {@code generateEnchantments} returned an empty list, and
     * {@code method_17410} took its {@code if (list.isEmpty()) goto 240} early return.
     *
     * <p>1.12.2 hit the identical problem and solved it by replacing {@code getEnchantmentDatas}
     * wholesale for {@code ENCHANTED_BOOK}, filtering with {@code isAllowedOnBooks()} instead of
     * {@code canApply(stack)}. This redirect is the same statement in one line, and it mirrors the
     * redirect already used in {@link EnchantmentScreenHandlerMixin} for {@code method_17410}'s own
     * {@code isOf(Items.BOOK)} check — both halves of "an enchanted book is a book" are needed:
     * this one so an offer can be generated, that one so the offer can be written.
     *
     * <p>Why the failure was silent: the offer <b>power</b> is computed by
     * {@code calculateRequiredExperienceLevel} (fixed by (a) above), so the buttons lit up normally at
     * levels like {@code [14, 34, 64]} — the player sees a valid offer, clicks, and nothing happens,
     * because the list is empty by then. On a creative-mode client every {@code onButtonClick} guard is
     * bypassed, so "not enough levels" cannot be the explanation either.
     */
    @Redirect(
            method = POSSIBLE,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z")
    )
    private static boolean simpletweaks$enchantedBookCountsAsBook(ItemStack stack, Item item) {
        if (item == Items.BOOK && stack.isOf(Items.ENCHANTED_BOOK)) {
            return true;
        }
        return stack.isOf(item);
    }

    @Inject(method = POSSIBLE, at = @At("RETURN"))
    private static void simpletweaks$capturePossible(int level, ItemStack stack,
            Stream<RegistryEntry<Enchantment>> candidates,
            CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        CANDIDATES.set(cir.getReturnValue());
    }

    @Inject(method = GENERATE, at = @At("RETURN"), cancellable = true)
    private static void simpletweaks$ensureMinEnchants(Random random, ItemStack stack, int level,
            Stream<RegistryEntry<Enchantment>> candidates,
            CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        // Cleared first so an early return below cannot leak the candidate set into the next
        // invocation. `generateEnchantments` has two ARETURNs (the enchantability<=0 early exit and
        // the real one) and only one of them executes per call; on the early exit CANDIDATES is null,
        // which is exactly the "no candidates" case.
        List<EnchantmentLevelEntry> possible = CANDIDATES.get();
        CANDIDATES.remove();

        if (stack.isEmpty() || possible == null || possible.isEmpty()) {
            return;
        }

        // 1.12.2: `int minEnchants = level / 15;`
        int min = level / 15;
        if (min <= 0) {
            return;
        }

        List<EnchantmentLevelEntry> before = cir.getReturnValue();
        if (before == null) {
            before = List.of();
        }
        if (before.size() >= min) {
            return;
        }

        // Copied rather than mutated in place: the returned list happens to be an ArrayList in
        // 1.21.1, but that is an implementation detail of the method being injected into.
        List<EnchantmentLevelEntry> list = new ArrayList<>(before);
        boolean isBook = stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK);

        Set<RegistryEntry<Enchantment>> used = new HashSet<>();
        for (EnchantmentLevelEntry entry : list) {
            used.add(entry.enchantment);
        }

        int attempts = 0;
        while (list.size() < min && attempts < MAX_ATTEMPTS) {
            attempts++;

            List<EnchantmentLevelEntry> pool = new ArrayList<>();
            for (EnchantmentLevelEntry entry : possible) {
                if (used.contains(entry.enchantment)) {
                    continue;
                }
                Enchantment enchantment = entry.enchantment.value();
                if (!isBook && !enchantment.isPrimaryItem(stack)) {
                    continue;
                }
                boolean conflict = false;
                for (RegistryEntry<Enchantment> other : used) {
                    if (!Enchantment.canBeCombined(entry.enchantment, other)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) {
                    continue;
                }
                pool.add(entry);
            }
            if (pool.isEmpty()) {
                break;
            }

            EnchantmentLevelEntry picked = simpletweaks$weightedPick(random, pool);
            if (picked == null) {
                break;
            }
            // 1.12.2: `int lvl = 1 + random.nextInt(picked.getMaxLevel());`
            int pickedLevel = 1 + random.nextInt(picked.enchantment.value().getMaxLevel());
            list.add(new EnchantmentLevelEntry(picked.enchantment, pickedLevel));
            used.add(picked.enchantment);
        }

        cir.setReturnValue(list);
    }

    /** 1.12.2 `simpletweaks$weightedEnchantPick`, using the 1.21 weight source. */
    private static EnchantmentLevelEntry simpletweaks$weightedPick(Random random,
            List<EnchantmentLevelEntry> pool) {
        int total = 0;
        for (EnchantmentLevelEntry entry : pool) {
            total += Math.max(1, entry.enchantment.value().getWeight());
        }
        if (total <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        int pick = random.nextInt(total);
        for (EnchantmentLevelEntry entry : pool) {
            pick -= Math.max(1, entry.enchantment.value().getWeight());
            if (pick < 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
