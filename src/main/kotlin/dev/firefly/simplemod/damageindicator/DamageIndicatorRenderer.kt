package dev.firefly.simplemod.damageindicator

import dev.firefly.simplemod.core.config.DamageIndicatorConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = "assets/simplemod", value = [Side.CLIENT])
object DamageIndicatorRenderer {
    private val mc = Minecraft.getMinecraft()

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        if (!DamageIndicatorConfig.enabled) {
            DamageIndicatorManager.clear()
            return
        }

        val player = mc.player ?: return
        val renderManager = mc.renderManager
        val viewX = renderManager.viewerPosX
        val viewY = renderManager.viewerPosY
        val viewZ = renderManager.viewerPosZ

        DamageIndicatorManager.update()

        GlStateManager.pushMatrix()
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )

        val numbers = DamageIndicatorManager.getNumbers().toList()

        for (number in numbers) {
            val entity = number.entity
            if (entity.isDead) continue

            val pos = Vec3d(number.fixedX, number.getCurrentY(), number.fixedZ)
            val dx = pos.x - viewX
            val dy = pos.y - viewY
            val dz = pos.z - viewZ

            val dist = MathHelper.sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > DamageIndicatorConfig.maxDistance || dist < 0.01) continue

            val isSelf = number.isSelf

            if (!isSelf) {
                val look = player.getLook(1f)
                val toEntity = Vec3d(dx, dy, dz).normalize()
                if (toEntity.dotProduct(look) < -0.1) continue
            }

            GlStateManager.pushMatrix()
            GlStateManager.translate(dx.toFloat(), dy.toFloat(), dz.toFloat())

            GlStateManager.rotate(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f)
            GlStateManager.rotate(renderManager.playerViewX, 1.0f, 0.0f, 0.0f)

            val scale = number.getScale() * 0.025f
            GlStateManager.scale(-scale, -scale, -scale)

            val alpha = number.getAlpha()
            val color = number.getColor()
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f

            val font = mc.fontRenderer
            val showShadow = DamageIndicatorConfig.showShadow

            val text1 = number.getText()
            val width1 = font.getStringWidth(text1)
            val yOffset = -6f

            if (showShadow) {
                GlStateManager.color(0f, 0f, 0f, alpha * 0.6f)
                font.drawString(text1, -width1 / 2f + 1, yOffset + 1, 0x000000, false)
            }

            GlStateManager.color(r, g, b, alpha)
            font.drawString(text1, -width1 / 2f, yOffset, color, false)

            val text2 = number.getSecondLineText()
            val width2 = font.getStringWidth(text2)
            val yOffset2 = yOffset + 12f

            val color2 = number.getSecondLineColor()
            val r2 = ((color2 shr 16) and 0xFF) / 255f
            val g2 = ((color2 shr 8) and 0xFF) / 255f
            val b2 = (color2 and 0xFF) / 255f

            if (showShadow) {
                GlStateManager.color(0f, 0f, 0f, alpha * 0.6f)
                font.drawString(text2, -width2 / 2f + 1, yOffset2 + 1, 0x000000, false)
            }

            GlStateManager.color(r2, g2, b2, alpha)
            font.drawString(text2, -width2 / 2f, yOffset2, color2, false)

            GlStateManager.popMatrix()
        }

        GlStateManager.disableBlend()
        GlStateManager.enableDepth()
        GlStateManager.popMatrix()
    }
}