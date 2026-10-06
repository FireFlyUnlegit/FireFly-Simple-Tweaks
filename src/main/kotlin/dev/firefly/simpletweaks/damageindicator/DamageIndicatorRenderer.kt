package dev.firefly.simpletweaks.damageindicator

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * Client half of the damage indicator: draws the floating numbers.
 *
 * <h2>1.12.2 -&gt; 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `RenderWorldLastEvent` | `WorldRenderEvents.AFTER_ENTITIES` (**not** `LAST` — see below) |
 * | `GlStateManager.disableDepth()` | `TextRenderer.TextLayerType.SEE_THROUGH` (the layer type *is* the depth-test setting in 1.21) |
 * | `GlStateManager.color(r,g,b,a)` | the alpha is packed into the ARGB `color` argument of `TextRenderer.draw` |
 * | `GlStateManager.rotate(-playerViewY, 0,1,0)` + `rotate(playerViewX, 1,0,0)` | `MatrixStack#multiply(Camera#getRotation())` — same billboard, one quaternion |
 * | `fontRenderer.drawString(s, x, y, color, false)` | `TextRenderer#draw(String, float, float, int, boolean, Matrix4f, VertexConsumerProvider, TextLayerType, int, int)` |
 * | `GlStateManager.pushMatrix()/popMatrix()` | `MatrixStack#push()/pop()` |
 *
 * <p>{@code GlStateManager} no longer exists in 1.21 — there is no global GL state to save/restore, so
 * the whole {@code disableDepth/enableBlend/tryBlendFuncSeparate} dance at the top of the 1.12.2 method
 * is gone. The blend function is now chosen by the vertex-consumer layer, and depth handling by
 * [TextRenderer.TextLayerType].
 *
 * <h2>Faithfulness notes</h2>
 * <ul>
 *   <li>The shadow uses **vanilla's built-in drop shadow** (`TextRenderer.draw(..., shadow = true,
 *       ...)`), not 1.12.2's hand-drawn black copy.
 *
 *       <p>1.12.2 drew a second black pass at `(+1, +1)` with `alpha * 0.6` and passed `false` for the
 *       built-in shadow. Reimplementing that faithfully reproduced only its artefacts: the colour it
 *       passed was `0x000000`, whose alpha bits are zero, so 1.12.2's own FontRenderer forced it
 *       opaque (`if ((color & 0xFC000000) == 0) color |= 0xFF000000;`) — meaning the `alpha * 0.6`
 *       never took effect and the shadow never faded with the text. The visible result was a hard
 *       black offset that looked washed-out/blurred next to 1.21's text rendering. The author chose
 *       the built-in shadow instead: cleaner, it fades with the text, and it makes
 *       `DamageIndicatorConfig.showShadow` mean exactly what it says.</li>
 *   <li>Text is drawn at `y = -6` and `y = +6` (the second line is `-6 + 12`), and the scale is
 *       `getScale() * 0.025` with the signs **`+s, -s, +s`**.
 *
 *       <p><b>This one sign was the whole reason the feature was invisible across every build.</b>
 *       The authority is vanilla itself — this is the exact instruction sequence from
 *       `EntityRenderer#renderLabelIfPresent`, dumped from the 1.21.1 jar:
 *       <pre>
 *         120: ldc   #28    // float 0.025f
 *         123: ldc_w #436   // float -0.025f
 *         126: ldc   #28    // float 0.025f
 *         128: invokevirtual MatrixStack.scale:(FFF)V
 *       </pre>
 *       i.e. **vanilla nameplates scale `(0.025, -0.025, 0.025)` — X is POSITIVE.** This port assumed
 *       the opposite (`-0.025, -0.025, 0.025`) and carried `-s` on X from the first version onwards.
 *
 *       <p>Why the sign matters: the product of the three scale factors is the determinant, and the
 *       text layer's vertices have a fixed winding. `(+, -, +)` gives a **negative** determinant, which
 *       is the one that survives culling; `(-, -, +)` and `(-, -, -)` flip it positive and the quads
 *       are culled. Empirically, every configuration that rendered used a negative determinant
 *       (`(0.08, -0.08, 0.08)` markers, vanilla's `(0.025, -0.025, 0.025)`), and every one that did
 *       not used a positive one (the `(-0.03, -0.03, 0.03)` number, the `(-0.05, -0.05, 0.05)` height
 *       sweep).
 *
 *       <p>An earlier revision of this KDoc asserted the opposite sign was at fault ("negative
 *       determinant gets culled"). That was wrong, and it was written from a correlation the author
 *       never falsified. The correct rule, verified against vanilla, is in the table above.</li>
 *   <li>The back-face cull (`dot(look, toEntity) < -0.1`) is preserved, except for self-damage
 *       numbers, which are always drawn.</li>
 *   <li>`light = 0xF000F0` (full brightness) replaces the 1.12.2 behaviour of ignoring light entirely —
 *       1.21 has no "unlit" mode for text, so full-bright is the equivalent.</li>
 * </ul>
 *
 * <p><b>Note on vanilla's own label rendering</b> (read for comparison, deliberately not copied):
 * `renderLabelIfPresent` draws the label **twice** — once with `TextLayerType.SEE_THROUGH` and a
 * low-alpha colour `0x20FFFFFF` as a soft backdrop, then again opaquely with `NORMAL`. It also passes
 * `backgroundColour = textBackgroundOpacity &lt;&lt; 24`. This port still draws a single pass rather than
 * vanilla's two, and it does **not** pass a background colour, so the numbers keep 1.12.2's flat look
 * instead of getting modern nameplate backing. What it *does* share with vanilla is the part that
 * matters mechanically: the matrix operations, the scale signs and the `draw` overload. The shadow is
 * vanilla's built-in one (see the Faithfulness notes above).</li>
 *
 * <p><b>Lesson recorded for future world-space rendering in this port:</b> the object of faithfulness
 * is the *observable behaviour*, not the arithmetic of a call whose surrounding state has changed.
 * Copying `GlStateManager.scale(-s,-s,-s)` literally was less faithful than `-s,-s,s`, not more.
 *
 * <h2>Dynamic evidence</h2>
 * A one-shot `[ST-DamageIndicator]` line is emitted the first time a number is actually drawn, which
 * proves the whole chain (server diff → packet → client receiver → render) end to end. Note that this
 * probe **cannot** prove the pixels are visible — that took a human, and the useful technique was to
 * have the human *walk around the marker* to expose orientation dependence.
 */
object DamageIndicatorRenderer {

    private var loggedFirstDraw = false

    fun register() {
        // AFTER_ENTITIES, not LAST. See the class KDoc — `LAST` fires after `WorldRenderer.render`
        // has returned, by which point the matrix stack no longer carries the camera's world
        // transform, so camera-relative `translate(dx, dy, dz)` lands off-screen.
        WorldRenderEvents.AFTER_ENTITIES.register { context -> render(context) }
    }

    private fun render(context: WorldRenderContext) {
        if (!DamageIndicatorConfig.enabled) {
            DamageIndicatorManager.clear()
            return
        }

        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val camera = context.camera()
        val viewX = camera.pos.x
        val viewY = camera.pos.y
        val viewZ = camera.pos.z

        DamageIndicatorManager.update()

        val matrices: MatrixStack = context.matrixStack() ?: return
        val consumers: VertexConsumerProvider.Immediate = client.bufferBuilders.entityVertexConsumers
        val textRenderer = client.textRenderer
        val showShadow = DamageIndicatorConfig.showShadow

        for (number in DamageIndicatorManager.getNumbers().toList()) {
            val entity = number.entity
            if (entity.isRemoved) continue

            val pos = Vec3d(number.fixedX, number.getCurrentY(), number.fixedZ)
            val dx = pos.x - viewX
            val dy = pos.y - viewY
            val dz = pos.z - viewZ

            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > DamageIndicatorConfig.maxDistance || dist < 0.01) continue

            if (!number.isSelf) {
                val look = player.getRotationVec(1f)
                val toEntity = Vec3d(dx, dy, dz).normalize()
                if (toEntity.dotProduct(look) < -0.1) continue
            }

            matrices.push()
            matrices.translate(dx, dy, dz)
            // Billboard: replaces the two GlStateManager.rotate calls.
            matrices.multiply(camera.rotation)

            val scale = number.getScale() * 0.025f
            // `+s, -s, +s` — X POSITIVE. This is vanilla's own nameplate scale, straight out of
            // `EntityRenderer#renderLabelIfPresent`: `scale(0.025f, -0.025f, 0.025f)`.
            //
            // The sign of X decides the determinant, and the text quads only survive culling with a
            // NEGATIVE determinant: `(+)(-)(+) < 0` is correct, `(-)(-)(+) > 0` is culled. Carrying
            // `-s` on X (from a wrong assumption about vanilla, plus 1.12.2's literal
            // `GlStateManager.scale(-s,-s,-s)` whose GL state no longer exists) is what made this
            // feature invisible in every build up to now. See the class KDoc for the full evidence.
            matrices.scale(scale, -scale, scale)

            val alpha = number.getAlpha()
            val matrix = matrices.peek().positionMatrix

            drawLine(
                textRenderer, consumers, number.getText(), number.getColor(),
                alpha, -6f, matrix, showShadow,
                TextRenderer.TextLayerType.SEE_THROUGH,
            )
            drawLine(
                textRenderer, consumers, number.getSecondLineText(), number.getSecondLineColor(),
                alpha, 6f, matrix, showShadow,
                TextRenderer.TextLayerType.SEE_THROUGH,
            )

            matrices.pop()

            if (!loggedFirstDraw) {
                loggedFirstDraw = true
                STLog.log(
                    "DamageIndicator",
                    "seam=alive, drawn=1, entity=${entity.type.name.string}, " +
                        "text=${number.getText()}, second=${number.getSecondLineText()}, " +
                        "alpha=${"%.2f".format(alpha)}, dist=${"%.1f".format(dist)}, " +
                        "percentageMode=${DamageIndicatorConfig.percentageMode}, " +
                        "symbol=${DamageIndicatorConfig.symbol}, shadow=$showShadow, " +
                        "scale=${"%.4f".format(scale)}, scaleSigns=+,-,+, outcome=drawn",
                )
            }
        }

        consumers.draw()
    }

    /**
     * Draws one line, using **vanilla's built-in drop shadow** (`shadow = showShadow`), not 1.12.2's
     * hand-drawn black copy. See the "Faithfulness notes" in the class KDoc for the reasoning.
     *
     * <p>⚠️ This doc comment previously still described the 1.12.2 replica ("black at `+1,+1`,
     * `alpha * 0.6`") long after the code had moved to the built-in shadow. The stale text made a
     * correct implementation look reverted, which is the same failure as §14.1 — documentation
     * contradicting the code is a defect, not a cosmetic issue. Keep this in step with the body.
     */
    private fun drawLine(
        textRenderer: TextRenderer,
        consumers: VertexConsumerProvider,
        text: String,
        color: Int,
        alpha: Float,
        y: Float,
        matrix: org.joml.Matrix4f,
        showShadow: Boolean,
        layer: TextRenderer.TextLayerType,
    ) {
        val width = textRenderer.getWidth(text)
        val x = -width / 2f
        val alphaByte = ((alpha.coerceIn(0f, 1f) * 255f).toInt() and 0xFF)

        // Vanilla's own drop shadow (`shadow = true`), not 1.12.2's hand-drawn black copy.
        //
        // 1.12.2 drew a second black pass at `(+1, +1)` and passed `false` here. That manual copy was
        // the source of the "washed out / blurred" look in this port: its colour was `0x000000`, whose
        // alpha bits are zero, so 1.12.2's FontRenderer forced it opaque — meaning the accompanying
        // `GlStateManager.color(0f, 0f, 0f, alpha * 0.6f)` never took effect and the shadow never
        // faded with the text. Reimplementing that by hand only reproduced its artefacts.
        //
        // Letting the renderer draw the shadow gives a single, correctly-offset, correctly-faded
        // shadow that matches modern expectations, and it makes `DamageIndicatorConfig.showShadow`
        // mean exactly what it says. The author chose this over the 1.12.2 replica.
        textRenderer.draw(
            text, x, y,
            (alphaByte shl 24) or (color and 0xFFFFFF),
            showShadow,
            matrix, consumers,
            layer, 0, LIGHT,
        )
    }

    /** Full-bright packed light value (`LightmapTextureManager.MAX_LIGHT_COORDINATE`). */
    private const val LIGHT = 0xF000F0
}
