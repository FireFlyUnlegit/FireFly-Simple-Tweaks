package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.client.hud.HudBar
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.config.ConfigBounds
import dev.firefly.simpletweaks.core.config.MobInfoBarConfig
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.WorldRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating name + health + armor above every mob, drawn with the same pieces as the player HUD bars in
 * [HudBar]: container/fill sprite pair, 1 px cut-corner frame, per-row gradient track, value centred in
 * the track.
 *
 * Everything is laid out in a local pixel space (Y down, the matrix flips it). Each element gets its own
 * depth between [Z_BACK] and [Z_HILITE]: coplanar quads in a translucent layer are sorted by camera
 * distance, and equal distances flipped order between frames, which flickered.
 */
object MobInfoBarRenderer {

    /** Design size: 0.0125 puts the 80 px track at roughly one block. */
    private const val BASE_SCALE = 0.0125f
    private const val ICON = HudBar.ICON.toFloat()
    private const val BAR_W = HudBar.WIDTH.toFloat()
    private const val BAR_H = HudBar.HEIGHT.toFloat()
    private const val GROUP_W = ICON + BAR_W
    private const val GROUP_LEFT = -GROUP_W / 2f
    private const val NAME_Y = -12f
    private const val ARMOR_Y = BAR_H + 1f
    private const val NAME_LIMIT = 28
    private const val MAX_BARS = 24
    private const val FADE = 4.0
    private const val LIGHT = 0xF000F0
    private const val THICK = 0.004f
    // Every quad sits behind z = 0 so the value text, icons and name (drawn at z = 0 with a polygon
    // offset) stay in front of the fill; putting the fill in front hid the health text entirely.
    private const val Z_BACK = -0.12f
    private const val Z_FRAME = -0.1f
    private const val Z_TRACK = -0.09f
    private const val Z_GHOST = -0.08f
    private const val Z_FILL = -0.07f
    private const val Z_HILITE = -0.05f

    private val HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container")
    private val HEART_FULL = Identifier.ofVanilla("hud/heart/full")
    private val HEART_HALF = Identifier.ofVanilla("hud/heart/half")
    private val ARMOR_EMPTY = Identifier.ofVanilla("hud/armor_empty")
    private val ARMOR_FULL = Identifier.ofVanilla("hud/armor_full")

    private const val BORDER = 0xFF000000.toInt()
    private const val TRACK = 0xFF2B2B2B.toInt()
    private const val FILL_TOP = 0xFF8FE88F.toInt()
    private const val FILL_BOTTOM = 0xFF2C8A2C.toInt()
    private const val LOW_TOP = 0xFFFF7A7A.toInt()
    private const val LOW_BOTTOM = 0xFF8E1B1B.toInt()
    private const val HIGHLIGHT = 0x66FFFFFF
    private const val GHOST = 0x99FFFFFF.toInt()
    private const val LABEL = 0xFFFFFFFF.toInt()

    private val candidates = ArrayList<LivingEntity>(32)
    private val ghosts = HashMap<Int, HudBar.Smooth>()
    private var loggedIcon = false

    fun register() {
        WorldRenderEvents.AFTER_ENTITIES.register { context -> render(context) }
    }

    private fun render(context: WorldRenderContext) {
        if (!MobInfoBarConfig.enabled) return
        val client = MinecraftClient.getInstance()
        val world = context.world() ?: client.world ?: return
        val player = client.player ?: return
        if (client.options.hudHidden) return
        val matrices = context.matrixStack() ?: return
        val camera = context.camera()
        val consumers = context.consumers() as? VertexConsumerProvider.Immediate ?: return
        val textRenderer = client.textRenderer
        val tickDelta = client.renderTickCounter.getTickDelta(false)
        val atlas = client.guiAtlasManager

        val max = MobInfoBarConfig.maxDistance
        val reach = max + 8.0
        val look = Vec3d.fromPolar(camera.pitch, camera.yaw)

        candidates.clear()
        world.getEntitiesByClass(LivingEntity::class.java, Box.from(camera.pos).expand(reach)) { entity ->
            entity.isAlive && !entity.isInvisible && !entity.isRemoved && isTarget(entity, player)
        }.forEach { entity ->
            val pos = entity.getLerpedPos(tickDelta)
            val dist = pos.distanceTo(camera.pos)
            if (dist > max || dist < 0.01) return@forEach
            if (pos.subtract(camera.pos).dotProduct(look) < -0.1) return@forEach
            candidates.add(entity)
        }
        if (candidates.isEmpty()) {
            if (ghosts.size > 64) ghosts.clear()
            return
        }
        candidates.sortBy { it.squaredDistanceTo(camera.pos) }
        val drawn = min(candidates.size, MAX_BARS)

        // Panels are their own batch. Translucent quads are sorted by distance inside a layer, so a panel
        // submitted together with the bars can be blended on top of its own fill and text, which made a
        // higher `backgroundOpacity` dim everything above it. Flushing first makes "background" actually
        // sit behind.
        if (MobInfoBarConfig.backgroundOpacity > 0) {
            val panelVc = consumers.getBuffer(RenderLayer.getDebugFilledBox())
            val panel = (MobInfoBarConfig.backgroundOpacity * 255 / 100) shl 24
            for (index in 0 until drawn) {
                val entity = candidates[index]
                val pos = entity.getLerpedPos(tickDelta)
                val dist = pos.distanceTo(camera.pos)
                val alphaMul = ((max - dist) / FADE).coerceIn(0.0, 1.0).toFloat()
                if (alphaMul <= 0.02f) continue
                val armor = entity.getAttributeValue(EntityAttributes.GENERIC_ARMOR)
                val bottomY = if (MobInfoBarConfig.showArmor && armor > 0.0) ARMOR_Y + ICON else BAR_H
                billboard(matrices, entity, pos, camera)
                quad(
                    panelVc, matrices,
                    GROUP_LEFT - 4f, NAME_Y - 3f, -GROUP_LEFT + 4f, bottomY + 3f,
                    Z_BACK, tint(panel, alphaMul)
                )
                matrices.pop()
            }
            consumers.draw()
        }

        // draw() invalidates every BufferBuilder returned by the immediate provider, so obtain the bar
        // buffer only after the panel flush.
        val vc = consumers.getBuffer(RenderLayer.getDebugFilledBox())

        for (index in 0 until drawn) {
            val entity = candidates[index]
            val pos = entity.getLerpedPos(tickDelta)
            val dist = pos.distanceTo(camera.pos)
            val alphaMul = ((max - dist) / FADE).coerceIn(0.0, 1.0).toFloat()
            if (alphaMul <= 0.02f) continue

            val maxHealth = entity.maxHealth.coerceAtLeast(1f)
            val health = entity.health.coerceIn(0f, maxHealth)
            val ratio = health / maxHealth
            val ghost = ghosts.getOrPut(entity.id) { HudBar.Smooth() }.update(ratio)
            val low = ratio < 0.3f
            val armor = entity.getAttributeValue(EntityAttributes.GENERIC_ARMOR)
            val showArmor = MobInfoBarConfig.showArmor && armor > 0.0

            billboard(matrices, entity, pos, camera)

            val matrix = matrices.peek().positionMatrix
            val layer = TextRenderer.TextLayerType.POLYGON_OFFSET

            // Icon pairs exactly like HudBar.icon: container first, then the fill on top.
            val heartTop = if (ratio < 0.6f) HEART_HALF else HEART_FULL
            icon(consumers, matrices, atlas.getSprite(HEART_CONTAINER), atlas.getSprite(heartTop), GROUP_LEFT, 0f, alphaMul)
            frame(vc, matrices, GROUP_LEFT + ICON, 0f, BAR_W, BAR_H, alphaMul)

            val innerW = BAR_W - 2f
            val filled = ceil(innerW * ratio).toInt()
            val ghostPx = ceil(innerW * ghost.coerceIn(0f, 1f)).toInt()
            if (ghostPx > filled) {
                quad(vc, matrices, GROUP_LEFT + ICON + 1f + filled, 1f, GROUP_LEFT + ICON + 1f + ghostPx, BAR_H - 1f, Z_GHOST, tint(GHOST, alphaMul))
            }
            fillRows(vc, matrices, GROUP_LEFT + ICON, BAR_W, BAR_H, ratio, low, alphaMul)

            val label = label(entity)
            if (label.isNotEmpty()) {
                textRenderer.draw(
                    label, -textRenderer.getWidth(label) / 2f, NAME_Y, tint(LABEL, alphaMul),
                    true, matrix, consumers, layer, 0, LIGHT
                )
            }
            val hpText = hpText(health, maxHealth, ratio)
            if (hpText.isNotEmpty()) {
                textRenderer.draw(
                    hpText, GROUP_LEFT + ICON + (BAR_W - textRenderer.getWidth(hpText)) / 2f, 1f,
                    tint(LABEL, alphaMul), true, matrix, consumers, layer, 0, LIGHT
                )
            }

            if (showArmor) {
                val armorText = HudBar.number(armor)
                val rowW = ICON + 2f + textRenderer.getWidth(armorText)
                val rowX = -rowW / 2f
                icon(consumers, matrices, atlas.getSprite(ARMOR_EMPTY), atlas.getSprite(ARMOR_FULL), rowX, ARMOR_Y, alphaMul)
                textRenderer.draw(
                    armorText, rowX + ICON + 2f, ARMOR_Y, tint(LABEL, alphaMul),
                    true, matrix, consumers, layer, 0, LIGHT
                )
            }

            matrices.pop()
        }
        consumers.draw()
    }

    /** Pushes a camera-facing, uniformly scaled pixel space above [entity]; caller must pop. */
    /** Tall models swing limbs well above a 1.8-block hitbox; give the bar room in proportion. */
    private fun headroom(entity: LivingEntity): Float =
        0.32f + (entity.height - 1.8f).coerceAtLeast(0f) * 0.8f

    private fun billboard(matrices: MatrixStack, entity: LivingEntity, pos: Vec3d, camera: Camera) {
        matrices.push()
        val headroom = headroom(entity)
        matrices.translate(
            pos.x - camera.pos.x,
            pos.y + entity.height + headroom - camera.pos.y,
            pos.z - camera.pos.z
        )
        matrices.multiply(camera.rotation)
        val scale = BASE_SCALE * MobInfoBarConfig.scalePercent.coerceIn(ConfigBounds.MOB_INFO_SCALE_PERCENT) / 100f
        matrices.scale(scale, -scale, scale)
    }

    /** Container sprite then fill sprite, the layering that gives vanilla icons their outline. */
    private fun icon(
        consumers: VertexConsumerProvider,
        matrices: MatrixStack,
        container: Sprite?,
        fill: Sprite?,
        x: Float,
        y: Float,
        alphaMul: Float
    ) {
        if (container != null) {
            if (!loggedIcon) {
                loggedIcon = true
                FSTLog.log("MobInfoBar") {
                    "icon atlas=${container.atlasId} u=${container.getFrameU(0f)}..${container.getFrameU(1f)} v=${container.getFrameV(0f)}..${container.getFrameV(1f)}"
                }
            }
            drawSprite(consumers, matrices, container, x, y, ICON, tint(0xFFFFFFFF.toInt(), alphaMul))
        }
        if (fill != null) drawSprite(consumers, matrices, fill, x, y, ICON, tint(0xFFFFFFFF.toInt(), alphaMul))
    }

    /** 1 px outline with cut corners, like `HudBar.frame`. */
    private fun frame(vc: VertexConsumer, matrices: MatrixStack, x: Float, y: Float, w: Float, h: Float, alphaMul: Float) {
        quad(vc, matrices, x + 1f, y, x + w - 1f, y + 1f, Z_FRAME, tint(BORDER, alphaMul))
        quad(vc, matrices, x + 1f, y + h - 1f, x + w - 1f, y + h, Z_FRAME, tint(BORDER, alphaMul))
        quad(vc, matrices, x, y + 1f, x + 1f, y + h - 1f, Z_FRAME, tint(BORDER, alphaMul))
        quad(vc, matrices, x + w - 1f, y + 1f, x + w, y + h - 1f, Z_FRAME, tint(BORDER, alphaMul))
        quad(vc, matrices, x + 1f, y + 1f, x + w - 1f, y + h - 1f, Z_TRACK, tint(TRACK, alphaMul))
    }

    private fun fillRows(
        vc: VertexConsumer,
        matrices: MatrixStack,
        x: Float,
        w: Float,
        h: Float,
        ratio: Float,
        low: Boolean,
        alphaMul: Float
    ) {
        val top = if (low) LOW_TOP else FILL_TOP
        val bottom = if (low) LOW_BOTTOM else FILL_BOTTOM
        val innerX = x + 1f
        val innerY = 1f
        val innerW = w - 2f
        val innerH = (h - 2f).toInt()
        val filled = ceil(innerW * ratio.coerceIn(0f, 1f)).toInt()
        if (filled <= 0 || innerH <= 0) return
        for (row in 0 until innerH) {
            val t = if (innerH <= 1) 0f else row.toFloat() / (innerH - 1)
            quad(vc, matrices, innerX, innerY + row, innerX + filled, innerY + row + 1, Z_FILL, tint(lerp(top, bottom, t), alphaMul))
        }
        quad(vc, matrices, innerX, innerY, innerX + filled, innerY + 1f, Z_HILITE, tint(HIGHLIGHT, alphaMul))
    }

    private fun isTarget(entity: LivingEntity, viewer: LivingEntity): Boolean = when (entity) {
        viewer -> false
        is PlayerEntity -> MobInfoBarConfig.showPlayers
        is MobEntity -> true
        else -> false
    }

    private fun label(entity: LivingEntity): String {
        // getDisplayName is already decorated with `[Lv.N]` by EntityDisplayNameMixin; adding the level
        // here as well printed it twice.
        val name = (entity.displayName ?: entity.name).string.replace('\n', ' ')
        return if (name.length > NAME_LIMIT) name.substring(0, NAME_LIMIT) + "…" else name
    }

    /** First value form that fits the track; mirrors `HudBar.fitText`/`fitNumber`. */
    private fun hpText(health: Float, maxHealth: Float, ratio: Float): String {
        val renderer = MinecraftClient.getInstance().textRenderer
        val options = when (MobInfoBarConfig.hpText) {
            MobInfoBarConfig.HpText.NONE -> return ""
            MobInfoBarConfig.HpText.PERCENT -> listOf("${(ratio * 100f).roundToInt()}%")
            MobInfoBarConfig.HpText.ABSOLUTE -> listOf(
                "${HudBar.number(health.toDouble())}/${HudBar.number(maxHealth.toDouble())}",
                "${HudBar.one(health.toDouble())}/${HudBar.one(maxHealth.toDouble())}",
                "${HudBar.zero(health.toDouble())}/${HudBar.zero(maxHealth.toDouble())}",
                "${HudBar.compact(health.toDouble())}/${HudBar.compact(maxHealth.toDouble())}",
            )
        }
        val innerW = BAR_W - 2f
        return options.firstOrNull { renderer.getWidth(it) <= innerW } ?: options.last()
    }

    /** GUI-atlas sprite in the current billboard space; [size] is the edge length in pixels. */
    private fun drawSprite(
        consumers: VertexConsumerProvider,
        matrices: MatrixStack,
        sprite: Sprite,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        z: Float = 0f
    ) {
        val m = matrices.peek().positionMatrix
        val a = ((color ushr 24) and 0xFF) / 255f
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        // GUI sprites are animation strips: `hud/heart/container` is 9x18 (two frames stacked), so the raw
        // min/max V spans both frames and squashing that into a 9x9 quad smears the icon darker.
        // getFrameU/V return the atlas UVs of the frame that is currently on screen.
        val u0 = sprite.getFrameU(0f)
        val u1 = sprite.getFrameU(1f)
        val v0 = sprite.getFrameV(0f)
        val v1 = sprite.getFrameV(1f)
        val vc = consumers.getBuffer(RenderLayer.getTextPolygonOffset(sprite.atlasId))
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u0, v0).light(LIGHT)
        vc.vertex(m, x, y + size, z).color(r, g, b, a).texture(u0, v1).light(LIGHT)
        vc.vertex(m, x + size, y + size, z).color(r, g, b, a).texture(u1, v1).light(LIGHT)
        vc.vertex(m, x + size, y, z).color(r, g, b, a).texture(u1, v0).light(LIGHT)
    }

    private fun quad(
        vc: VertexConsumer,
        matrices: MatrixStack,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        z: Float,
        color: Int
    ) {
        val a = ((color ushr 24) and 0xFF) / 255f
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        WorldRenderer.renderFilledBox(
            matrices, vc,
            x1.toDouble(), y1.toDouble(), z.toDouble(),
            x2.toDouble(), y2.toDouble(), (z + THICK).toDouble(),
            r, g, b, a
        )
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun mix(shift: Int): Int {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            return (ca + ((cb - ca) * t).toInt()).coerceIn(0, 255)
        }
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun tint(color: Int, factor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * factor).toInt().coerceIn(1, 255)
        return (alpha shl 24) or (color and 0xFFFFFF)
    }
}
