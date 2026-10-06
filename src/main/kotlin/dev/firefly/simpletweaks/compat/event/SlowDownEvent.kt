package dev.firefly.simpletweaks.compat.event

/**
 * Public API event: posted when the **client player's movement is about to be slowed down**.
 *
 * <h2>What it is for</h2>
 * This is a **public extension point**, not an internal seam — nothing inside this mod listens to it.
 * In 1.12.2 it lived in `extraforgeapi/extraevents/`, a package whose name says the same thing: it
 * exists so *other* mods can observe and override the movement penalty applied while sneaking,
 * blocking with a shield, eating, or drawing a bow.
 *
 * <h2>1.12.2 original, ported verbatim</h2>
 * <pre>
 *   class SlowDownEvent(player, type, var forward: Float, var strafe: Float, val factor: Float) : Event() {
 *       val originalForward = forward; val originalStrafe = strafe
 *       var skipSlowDown = false
 *       val isModified get() = forward != originalForward || strafe != originalStrafe || skipSlowDown
 *       var speedFactor: Float
 *           get() = forward / originalForward.coerceAtLeast(0.0001f)
 *           set(value) { val clamped = value.coerceIn(0f, 1f); val ratio = clamped / factor
 *                        forward = originalForward * ratio; strafe = originalStrafe * ratio
 *                        if (clamped >= 1f) skipSlowDown = true }
 *       enum class Type { SNEAK, BLOCKING, EATING, BOW }
 *   }
 * </pre>
 * Only the base type changed ([Event] instead of Forge's `net.minecraftforge.fml.common.eventhandler.Event`);
 * every field, the `speedFactor` arithmetic and the [Type] values are unchanged.
 *
 * <h2>How `speedFactor` works, and why it is not a plain multiplier</h2>
 * The event is posted **before** vanilla applies its own penalty, and [factor] is that penalty
 * (0.3 in 1.12.2). Setting `speedFactor = s` stores `forward = originalForward * (s / factor)`, so
 * that after vanilla multiplies by `factor` the player ends up moving at exactly `s` times their
 * original input. Setting `1.0` therefore means "no slowdown at all", and it also sets
 * [skipSlowDown].
 *
 * <p>In this port the seam sits **after** vanilla's `Input#tick(boolean, float)` has already applied
 * the factor, so the mixin reconstructs the pre-slowdown input by dividing by [factor] before posting;
 * the net effect for a listener is identical. See `mixin/InputSlowDownMixin.java`.
 *
 * <h2>Consuming this from another mod</h2>
 * Registration goes through this mod's own bus, not a global one — 1.12.2's `MinecraftForge.EVENT_BUS`
 * was shared by every mod, whereas [dev.firefly.simpletweaks.compat.ForgeEventBus] is ours. A third-party
 * mod can still subscribe by depending on this mod and calling `ForgeEventBus.register(owner)` with an
 * `@SubscribeEvent` method, which is what every internal handler does. A dedicated facade (a stable,
 * documented registration entry point that hides the bus) would be the cleaner public API and is a
 * possible follow-up — recorded in `docs/phase6-client-notes.md` §18.
 */
class SlowDownEvent(
    /** The client player being slowed down. */
    val player: net.minecraft.entity.player.PlayerEntity,
    /** Why the slowdown applies. */
    val type: Type,
    /** Forward movement input, **before** vanilla's penalty. Mutable so listeners can override it. */
    var forward: Float,
    /** Strafe movement input, **before** vanilla's penalty. Mutable so listeners can override it. */
    var strafe: Float,
    /** Vanilla's own slowdown factor for this situation (0.3 in 1.12.2). */
    val factor: Float,
) : Event() {

    val originalForward: Float = forward
    val originalStrafe: Float = strafe

    /** Set to true by [speedFactor] when the listener asked for no slowdown at all. */
    var skipSlowDown: Boolean = false

    /** True when a listener changed anything, i.e. the caller should write the values back. */
    val isModified: Boolean
        get() = forward != originalForward || strafe != originalStrafe || skipSlowDown

    /**
     * The movement speed the listener wants, as a fraction of the un-slowed input.
     *
     * Reading it derives the value from [forward]; writing it re-derives [forward]/[strafe] so that
     * vanilla's later multiplication by [factor] yields exactly the requested fraction.
     */
    var speedFactor: Float
        get() = forward / originalForward.coerceAtLeast(0.0001f)
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            val ratio = clamped / factor
            forward = originalForward * ratio
            strafe = originalStrafe * ratio
            if (clamped >= 1f) skipSlowDown = true
        }

    enum class Type {
        SNEAK,
        BLOCKING,
        EATING,
        BOW,
    }
}
