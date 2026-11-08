package dev.firefly.simpletweaks.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the **elytra** enchantable at an enchanting table.
 *
 * <h2>Why this exists</h2>
 * `Items.ELYTRA` is a plain `Item` with the default enchantability of **0**, and 1.21.1 treats 0 as
 * "this item cannot be enchanted at a table". That is a single number gating three separate
 * statements, all of which the mod's own `gale_elytra` runs into:
 *
 * <pre>
 *   ItemStack.isEnchantable()                      -> getEnchantability() &gt; 0   (slot refuses the elytra)
 *   EnchantmentHelper.calculateRequiredExperienceLevel -> if (i &lt;= 0) return 0   (offer power 0)
 *   EnchantmentHelper.generateEnchantments             -> if (i &lt;= 0) return []  (empty result)
 * </pre>
 *
 * So a player holding an elytra gets **no offers at all** — for `gale_elytra` and equally for vanilla
 * Unbreaking/Mending. The item merely looks un-enchantable, with no message, which is what made the
 * report "疾风鞘翅不能附魔在鞘翅上" (`build=cleanup68`). Note this is *vanilla* behaviour: the elytra
 * is normally enchanted with an anvil and a book.
 *
 * <h2>Why inject into `Item#getEnchantability` and not into the three call sites</h2>
 * One statement instead of three, and it cannot drift: every consumer of the value sees the same
 * number. The mod already redirects two of those call sites for enchanted books
 * ({@code EnchantmentHelperMixin}) — that port had to stay faithful to 1.12.2, but a new behaviour
 * should fix the value at its source rather than patching each reader.
 *
 * <h2>Why the identity check makes override auditing unnecessary</h2>
 * `Items.ELYTRA` is constructed from the `Item` constructor itself (not a subclass), so `this ==
 * Items.ELYTRA` can only ever be true inside a body whose declaring class *is* `Item` — there is no
 * subclass override that could shadow this injection for that instance (`AGENTS.md` §4.3).
 *
 * <h2>The value</h2>
 * **15**, the same constant the book port uses ({@code BOOK_ENCHANTABILITY}), i.e. roughly leather
 * armour. Enchantability is table *power*, not a cap: 15 reaches the high bands with a full bookshelf
 * ring while never exceeding what vanilla armour already gets.
 *
 * <p>This is a deliberate vanilla deviation, documented in `README` and `DEVLOG`. It is unconditional
 * on purpose — a config toggle here would mean "the mod's own elytra enchantment may be unobtainable",
 * which is not a state worth configuring.
 */
@Mixin(Item.class)
public abstract class ItemEnchantabilityMixin {

    private static final int ELYTRA_ENCHANTABILITY = 15;

    @Inject(method = "getEnchantability()I", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$elytraEnchantability(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == Items.ELYTRA) {
            cir.setReturnValue(ELYTRA_ENCHANTABILITY);
        }
    }
}
