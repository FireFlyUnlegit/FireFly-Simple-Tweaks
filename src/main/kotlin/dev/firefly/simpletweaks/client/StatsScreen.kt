package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.client.hud.BarNumber
import dev.firefly.simpletweaks.util.ArcaneEcho
import dev.firefly.simpletweaks.util.PlayerPower
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.MathHelper
import kotlin.math.max

/**
 * `/fst stats`: the numbers the server already acts on, in one place.
 *
 * Everything is read from the local player, so the screen needs no payload beyond the "open" request.
 * Values are live: [rebuild] runs on open and on every scroll, so a re-equip shows up without reopening.
 */
class StatsScreen(private val parent: Screen?) : Screen(Text.translatable(TITLE)) {

    private data class Line(val text: Text, val indent: Int, val colour: Int)

    private val lines = mutableListOf<Line>()
    private var scroll = 0

    override fun init() {
        rebuild()
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simple_tweaks.config.done")) { close() }
                .dimensions(this.width / 2 - 60, this.height - 28, 120, 20).build(),
        )
    }

    private fun rebuild() {
        lines.clear()
        val player = client?.player ?: return

        val parts = PlayerPower.partsOf(player)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.power", num(parts.total)), 0, 0xFFFF55)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.power.armor", num(parts.armor)), 1, GREY)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.power.health", num(parts.health)), 1, GREY)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.power.attack", num(parts.attack)), 1, GREY)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.power.enchant", num(parts.enchants), "${parts.enchantCount}"), 1, GREY)

        lines += header("gui.simple_tweaks.stats.attributes")
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.health", num(player.maxHealth.toDouble())), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.armor", num(attribute(player, EntityAttributes.GENERIC_ARMOR))), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.toughness", num(attribute(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS))), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.attack", num(attribute(player, EntityAttributes.GENERIC_ATTACK_DAMAGE))), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.attack_speed", num(attribute(player, EntityAttributes.GENERIC_ATTACK_SPEED))), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.move_speed", num(attribute(player, EntityAttributes.GENERIC_MOVEMENT_SPEED))), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.knockback", percent(attribute(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE))), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.attr.luck", num(attribute(player, EntityAttributes.GENERIC_LUCK))), 0, WHITE)

        val echo = ArcaneEcho.statsOf(player)
        lines += header("gui.simple_tweaks.stats.arcane")
        if (!echo.active) {
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.none").formatted(Formatting.DARK_GRAY), 0, GREY)
        } else {
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.summary", num(echo.level.toDouble()), num(echo.units.toDouble())), 0, 0xFFAA00)
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.final_damage", percent(echo.finalDamage)), 0, WHITE)
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.final_reduction", percent(echo.finalReduction)), 0, WHITE)
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.flat", num(echo.damageReduction)), 0, WHITE)
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.element", percent(echo.element)), 0, WHITE)
            lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.reaction", percent(echo.reaction)), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.ranged", percent(echo.rangedDamage)), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.healing", percent(echo.healing)), 0, WHITE)
        lines += Line(Text.translatable("gui.simple_tweaks.stats.arcane.mining", percent(echo.miningSpeed)), 0, WHITE)
        }

        lines += Line(Text.translatable("gui.simple_tweaks.stats.hint").formatted(Formatting.DARK_GRAY), 0, GREY)
        scroll = MathHelper.clamp(scroll, 0, max(0, lines.size - visibleLines()))
    }

    private fun header(key: String) = Line(Text.translatable(key).formatted(Formatting.AQUA), 0, 0x55FFFF)

    private fun attribute(player: net.minecraft.entity.player.PlayerEntity, attribute: net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>): Double =
        player.getAttributeValue(attribute)

    private fun num(value: Double): String = BarNumber.of(value, 2)

    private fun percent(value: Double): String = "+" + num(value * 100.0) + "%"

    private fun visibleLines(): Int = max(1, (this.height - LIST_TOP - FOOTER) / LINE_H)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, WHITE)

        val left = this.width / 2 - ROW_W / 2
        context.fill(left - 6, LIST_TOP - 4, left + ROW_W + 6, this.height - FOOTER + 2, 0x66000000.toInt())

        context.enableScissor(left - 6, LIST_TOP - 4, left + ROW_W + 6, this.height - FOOTER + 2)
        val end = minOf(lines.size, scroll + visibleLines())
        for (index in scroll until end) {
            val line = lines[index]
            val y = LIST_TOP + (index - scroll) * LINE_H
            context.drawTextWithShadow(this.textRenderer, line.text, left + line.indent * 8, y, line.colour)
        }
        context.disableScissor()

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val before = scroll
        scroll = MathHelper.clamp(scroll - verticalAmount.toInt() * 2, 0, max(0, lines.size - visibleLines()))
        return scroll != before
    }

    override fun close() {
        this.client?.setScreen(parent)
    }

    private companion object {
        const val TITLE = "gui.simple_tweaks.stats.title"
        const val LINE_H = 11
        const val LIST_TOP = 34
        const val FOOTER = 40
        const val ROW_W = 300
        const val WHITE = 0xFFFFFF
        const val GREY = 0xA0A0A0
    }
}
