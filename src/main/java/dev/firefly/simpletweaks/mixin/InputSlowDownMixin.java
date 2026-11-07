package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.SlowDownEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link SlowDownEvent} when the client player's movement input is slowed down.
 *
 * <h2>1.12.2 original</h2>
 * {@code MixinEntityPlayerSP} injected into {@code EntityPlayerSP#onLivingUpdate} at
 * {@code INVOKE MovementInput#updatePlayerMoveState()} {@code shift = AFTER}, then:
 * <pre>
 *   if (self.isRiding()) return;
 *   if (self.capabilities.isFlying) return;
 *   if (self.isHandActive()) { BLOCK / BOW / EATING from the active item's EnumAction }
 *   else if (self.isSneaking()) { SNEAK }
 *   else return;
 *   post(new SlowDownEvent(self, type, input.moveForward, input.moveStrafe, 0.3F));
 *   if (event.isModified()) { input.moveForward = ...; input.moveStrafe = ...; }
 * </pre>
 *
 * <h2>Why the seam moved to {@code Input#tick}, and why it is at RETURN</h2>
 * In 1.21 the movement input and the slowdown live inside
 * {@code net.minecraft.client.input.Input#tick(boolean slowDown, float slowDownFactor)} — that single
 * method both reads the key bindings into {@code movementForward}/{@code movementSideways} and applies
 * the penalty. Verified from the jar: {@code public void tick(boolean, float)}.
 *
 * <p>Injecting at **RETURN** means the penalty has already been applied, which is the opposite of
 * 1.12.2's position. That is handled explicitly rather than ignored:
 * <ul>
 *   <li>the input is divided by {@code slowDownFactor} before posting, so {@code forward}/{@code strafe}
 *       mean "the input before the penalty" to a listener — exactly what they meant in 1.12.2;</li>
 *   <li>on write-back the factor is re-applied, so a listener setting {@code speedFactor = s} ends up
 *       moving at {@code s} times the raw input, again matching 1.12.2's pre-compensation.</li>
 * </ul>
 * The alternative — injecting inside {@code Input#tick} before the penalty — is not reachable with a
 * plain injector, because the field writes that produce the raw values and the multiply that consumes
 * them are in the same method with no intervening call to anchor on.
 *
 * <h2>Override audit</h2>
 * {@code Input} is a concrete class with **no subclasses** in the vanilla jar; there is no override to
 * route around, and the client player is the only instance whose {@code tick} is ever called
 * ({@code ClientPlayerEntity#tickMovement}). The identity check below is therefore belt-and-braces.
 *
 * <h2>Why the `client` list</h2>
 * {@code net.minecraft.client.input.Input} is a client-only class, so this mixin must never be applied
 * on a dedicated server. It is listed under {@code "client"} in {@code simple_tweaks.mixins.json}.
 */
@Mixin(Input.class)
public abstract class InputSlowDownMixin {

    @Inject(method = "tick(ZF)V", at = @At("RETURN"))
    private void simpletweaks$postSlowDown(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        // No penalty was applied, so there is nothing to expose or override.
        if (!slowDown) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        Input self = (Input) (Object) this;
        if (self != player.input) {
            return;
        }

        // 1.12.2 skipped these two cases outright.
        if (player.hasVehicle()) {
            return;
        }
        if (player.getAbilities().flying) {
            return;
        }

        SlowDownEvent.Type type;
        if (player.isUsingItem()) {
            ItemStack active = player.getActiveItem();
            UseAction action = active.isEmpty() ? null : active.getUseAction();
            if (action == UseAction.BLOCK) {
                type = SlowDownEvent.Type.BLOCKING;
            } else if (action == UseAction.BOW) {
                type = SlowDownEvent.Type.BOW;
            } else {
                type = SlowDownEvent.Type.EATING;
            }
        } else if (player.isSneaking()) {
            type = SlowDownEvent.Type.SNEAK;
        } else {
            return;
        }

        float factor = slowDownFactor;
        float rawForward = factor == 0.0f ? self.movementForward : self.movementForward / factor;
        float rawStrafe = factor == 0.0f ? self.movementSideways : self.movementSideways / factor;

        SlowDownEvent event = new SlowDownEvent(player, type, rawForward, rawStrafe, factor);
        ForgeEventBus.INSTANCE.post(event);

        if (event.isModified()) {
            self.movementForward = event.getForward() * factor;
            self.movementSideways = event.getStrafe() * factor;
        }
    }
}
