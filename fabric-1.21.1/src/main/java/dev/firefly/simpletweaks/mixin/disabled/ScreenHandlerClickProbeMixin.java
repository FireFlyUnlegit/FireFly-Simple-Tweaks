package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.STLog;
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.ContainerInfiniteBag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TEMPORARY diagnostic: logs the exact decision state the click handler starts from, on both sides.
 *
 * <h2>Why this exists</h2>
 * The `infinite_power` bag was reported as "items cannot be taken out — they are picked up for an
 * instant and then vanish". The bag's own probes settled the *shape* of the failure decisively:
 * <pre>
 *   [Render thread] bag take: slot=1, requested=64, taken=64, remaining=0, outcome=taken   (x16)
 *   [Render thread] bag quickMove: slot=514, from=player, ... outcome=moved
 *   [Server thread] bag quickMove: slot=514, from=player, ... outcome=moved
 * </pre>
 * i.e. on a normal click the server **never reaches** {@code Slot#takeStackRange} — no
 * {@code [Server thread] bag take} line exists at all — while shift-click is processed normally on
 * both sides. So the packet arrives, the dispatch works, and the PICKUP branch bails before touching
 * the slot. What it bails on is not visible from outside, which is precisely what this hook exposes.
 *
 * <h2>What it prints, and how to read it</h2>
 * <pre>
 *   bag click: side=server, action=PICKUP, slot=3, button=0, cursor=0xAIR, slotStack=64xSTONE
 * </pre>
 * <ul>
 *   <li>{@code cursor} is the decisive field. If the server's cursor is <b>not empty</b> while the
 *       player sees an empty one, PICKUP takes the "place the cursor stack" path instead of the
 *       "take from the slot" path — which both explains the failed pickup and produces the doubling
 *       that was also reported (placing merges, so 64 + 64 becomes 128).</li>
 *   <li>{@code side} distinguishes "the click never reached the server" from "the server decided
 *       differently than the client".</li>
 *   <li>{@code slotStack} is the server's authoritative view of the clicked slot; a mismatch against
 *       what the player sees means a sync problem rather than a click-logic one.</li>
 * </ul>
 *
 * <p>Scoped to [ContainerInfiniteBag] so no other screen is affected. Remove this class (and its
 * entry in `simple_tweaks.mixins.json`) once the bag's click path is verified.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerClickProbeMixin {

    /** `ScreenHandler#cursorStack` — the authoritative cursor, and the field under suspicion. */
    @Shadow
    private ItemStack cursorStack;

    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD")
    )
    private void simpletweaks$probeBagClick(int slotIndex, int button, SlotActionType actionType,
                                            PlayerEntity player, CallbackInfo ci) {
        ScreenHandler self = (ScreenHandler) (Object) this;
        if (!(self instanceof ContainerInfiniteBag)) {
            return;
        }

        Slot slot = slotIndex >= 0 && slotIndex < self.slots.size() ? self.slots.get(slotIndex) : null;
        ItemStack inSlot = slot == null ? ItemStack.EMPTY : slot.getStack();

        STLog.INSTANCE.log("InfinitePower",
                "bag click: side=" + (player.getWorld().isClient() ? "client" : "server")
                        + ", action=" + actionType
                        + ", slot=" + slotIndex
                        + ", button=" + button
                        + ", cursor=" + this.cursorStack.getCount() + "x" + this.cursorStack.getItem()
                        + ", slotStack=" + inSlot.getCount() + "x" + inSlot.getItem());
    }
}
