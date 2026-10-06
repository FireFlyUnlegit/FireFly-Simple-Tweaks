package dev.firefly.simpletweaks.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Un-finals {@link Slot#x} and {@link Slot#y} so a GUI can reposition slots at runtime.
 *
 * <h2>Why this mixin is unavoidable rather than a shortcut</h2>
 * In 1.12.2 `GuiContainer`'s slot coordinates were ordinary mutable fields, and `GuiInfiniteBag`
 * relied on that: every frame it moved the slots that fall inside the scroll window to their visible
 * coordinates and parked the rest off-screen, which is how a 6-row window scrolls over a 54-row
 * inventory **without changing any slot index**. The server and client therefore always agree about
 * which bag slot a click means.
 *
 * <p>1.21.1 made those fields <b>final</b>, and this is verified, not assumed:
 * <pre>
 *   net/minecraft/screen/slot/Slot -&gt;
 *       private final int index;
 *       public  final net.minecraft.inventory.Inventory inventory;
 *       public  int id;                       &lt;-- the only mutable coordinate-adjacent field
 *       public  final int x;
 *       public  final int y;
 * </pre>
 * Writing them from another class throws
 * {@code IllegalAccessError: Update to non-static final field Slot.x} at <b>runtime</b> — the Kotlin
 * compiler accepts the assignment (it treats Java final fields as writable), so this cannot be caught
 * at build time. That is exactly how this was found: the client crashed on the first frame of
 * rendering the bag.
 *
 * <p>There is also <b>no accessor to override</b>: `HandledScreen` reads the coordinates with
 * {@code getfield Slot.x:I} directly (confirmed across its bytecode), so a `Slot` subclass with an
 * overridden getter would not be consulted. Un-finaling the fields is the only option that preserves
 * 1.12.2's "fixed indices, moving coordinates" contract.
 *
 * <h2>Safety of stripping `final`</h2>
 * Vanilla assigns `x`/`y` only in the `Slot` constructor and never writes them again, so removing the
 * modifier cannot change vanilla behaviour — the JVM's final-field rule exists to make such writes
 * illegal, not to make the value constant. No code constant-folds these reads either (they are
 * instance fields read via `getfield`), so there is no stale-inlining hazard.
 */
@Mixin(Slot.class)
public abstract class SlotPositionMixin {

    @Mutable
    @Shadow
    public int x;

    @Mutable
    @Shadow
    public int y;
}
