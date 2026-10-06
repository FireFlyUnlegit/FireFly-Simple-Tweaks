package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Port of 1.12.2 {@code MixinContainerRepair#modifyMaxAnvilCost} — the
 * <b>"Disable Anvil Cost Limit" / "Max Anvil Cost"</b> option, i.e. removing the "Too Expensive" cap.
 *
 * <h2>Why this file exists at all</h2>
 * The option was written into {@code GeneralConfig}, the config JSON, the config screen and both lang
 * files by the port, but <b>no mixin ever read it</b> — so it looked implemented and did nothing.
 * 1.12.2 had two mixins for this feature ({@code MixinContainerRepair} for the server-side gate and
 * {@code MixinGuiRepair} for the client-side label); neither was ported. This is the server half, and
 * {@link AnvilScreenMixin} is the client half — <b>both are required</b>: changing only one leaves the
 * anvil still claiming "Too Expensive" and refusing the take.
 *
 * <h2>Yarn 1.21.1 ground truth (bytecode, per MIGRATION.md 铁律二)</h2>
 * {@code AnvilScreenHandler.updateResult()} contains <b>three</b> {@code bipush 40}, at bytecode
 * offsets 723, 899 and 920, with three different meanings:
 *
 * <pre>
 *   offset 723  (ordinal 0)  inside the merge loop:
 *                            {@code if (stack.getCount() &gt; 1) cost = 40;}   &lt;-- a COST, not a cap
 *   offset 899  (ordinal 1)  {@code if (j &gt; 0 &amp;&amp; i == j &amp;&amp; levelCost.get() &gt;= 40) levelCost.set(39);}
 *                            &lt;-- the creative-mode display clamp
 *   offset 920  (ordinal 2)  {@code if (levelCost.get() &gt;= 40 &amp;&amp; !player.getAbilities().creativeMode)}
 *                            {@code     result = ItemStack.EMPTY;}            &lt;-- THE GATE
 * </pre>
 *
 * <b>Only ordinal 2 is the gate</b>, and it is the only one of the three that reads
 * {@code PlayerAbilities.creativeMode} and empties the result slot — that is how it was identified.
 *
 * <h2>Why an explicit {@code ordinal} is mandatory here</h2>
 * A bare {@code @ModifyConstant(intValue = 40)} matches <b>all three</b> occurrences (the project
 * already relies on that "matches every occurrence" behaviour deliberately in
 * {@code EnchantmentHelperMixin}, where the constant is unambiguous). Here it would be a disaster:
 * rewriting ordinal 0 to {@code maxAnvilCost} would make "repair with a stack of materials" cost
 * {@code Integer.MAX_VALUE} levels. Since this option must be able to express a <i>custom</i> cap
 * (e.g. 100) rather than only "no cap", an ordinal-based constant rewrite is the only injection that
 * fits — the usual "pretend creative mode" {@code @Redirect} trick cannot carry a number.
 *
 * <p>Note the ordinal is an element of <b>{@code @Constant}</b>, not of {@code @ModifyConstant}
 * (verified against {@code sponge-mixin} 0.15.5 with {@code javap}: {@code @ModifyConstant} exposes
 * {@code method/target/slice/constant/remap/require/expect/allow/constraints/order}, while only
 * {@code @Constant} has {@code ordinal}).
 *
 * <p>1.12.2 had no ordinal because its equivalent method had a single 40; its second hook (a
 * {@code @ModifyVariable} that turned a stored 40 into 0) targeted the first store of {@code i},
 * which the compiler had already folded away, so it never fired — i.e. ordinal 0 was left at 40 in
 * 1.12.2's <i>effective</i> behaviour too, and leaving it alone here reproduces that.
 *
 * <h2>Failure mode to check first if this regresses</h2>
 * The ordinal is the only fragile part. If the anvil still reports "Too Expensive" with a raise
 * configured, re-dump {@code updateResult} and confirm the gate is still the third {@code bipush 40};
 * a change in that count means the ordinal must be re-derived.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    /**
     * The "Too Expensive" gate of {@code updateResult()} — offsets 920..941, the third
     * {@code bipush 40} in the method.
     *
     * <p>Returns the configured cap. With the default {@code maxAnvilCost = Integer.MAX_VALUE} and
     * {@code disableAnvilCostLimit = true}, {@code levelCost.get() >= MAX_VALUE} is effectively never
     * true, so the gate never fires and the result is never emptied. The player still pays the real
     * cost ({@code canTakeOutput} requires {@code experienceLevel >= levelCost}), which is the point:
     * the cap goes away, the price does not.
     */
    @ModifyConstant(
            method = "updateResult",
            constant = @Constant(intValue = 40, ordinal = 2)
    )
    private int simpletweaks$maxAnvilCost(int original) {
        return GeneralConfig.getDisableAnvilCostLimit() ? GeneralConfig.getMaxAnvilCost() : original;
    }
}
