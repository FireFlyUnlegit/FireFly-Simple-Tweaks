package dev.firefly.simplemod.gui

import dev.firefly.simplemod.core.configs.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import org.lwjgl.input.Mouse

object Component {
    private val mc = Minecraft.getMinecraft()

    fun draw(
        value: Value<*>,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ): Boolean {
        return when (value) {
            is BoolValue -> drawBool(value, x, y, width, mouseX, mouseY)
            is IntValue -> drawInt(value, x, y, width, mouseX, mouseY)
            is FloatValue -> drawFloat(value, x, y, width, mouseX, mouseY)
            is ChoiceValue -> drawChoice(value, x, y, width, mouseX, mouseY)
            else -> drawDefault(value, x, y, width)
        }
    }

    private fun drawBool(value: BoolValue, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int): Boolean {
        val isHovered = mouseX in x..x + width && mouseY in y..y + 18
        val bgColor = if (isHovered) 0xCC444444.toInt() else 0xCC333333.toInt()
        val toggleColor = if (value.get()) 0xFF00AA00.toInt() else 0xCC666666.toInt()

        Gui.drawRect(x, y, x + width, y + 18, bgColor)
        Gui.drawRect(x + width - 24, y + 2, x + width - 2, y + 16, toggleColor)

        val textColor = if (value.get()) 0xFF88FF88.toInt() else 0xFFFFFFFF.toInt()
        mc.fontRenderer.drawStringWithShadow(value.name, x + 4f, y + 4f, textColor)

        val status = if (value.get()) "§aON" else "§cOFF"
        mc.fontRenderer.drawStringWithShadow(status, (x + width - 20 - mc.fontRenderer.getStringWidth(status)).toFloat(), y + 4f, 0xFFFFFFFF.toInt())

        if (isHovered && Mouse.isButtonDown(0)) {
            value.toggle()
            return true
        }
        return false
    }

    private fun drawInt(value: IntValue, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int): Boolean {
        return drawSlider(
            value = value,
            x = x,
            y = y,
            width = width,
            mouseX = mouseX,
            mouseY = mouseY,
            displayValue = value.get().toString() + (value.suffix ?: ""),
            min = value.min.toFloat(),
            max = value.max.toFloat(),
            current = value.get().toFloat()
        ) { newValue ->
            value.set(newValue.toInt())
        }
    }

    private fun drawFloat(value: FloatValue, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int): Boolean {
        return drawSlider(
            value = value,
            x = x,
            y = y,
            width = width,
            mouseX = mouseX,
            mouseY = mouseY,
            displayValue = String.format("%.1f", value.get()) + (value.suffix ?: ""),
            min = value.min,
            max = value.max,
            current = value.get()
        ) { newValue ->
            value.set(newValue)
        }
    }

    private fun drawSlider(
        value: Value<*>,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int,
        displayValue: String,
        min: Float,
        max: Float,
        current: Float,
        onValueChanged: (Float) -> Unit
    ): Boolean {
        val sliderX = x + 60
        val sliderWidth = width - 60 - 45
        val percent = (current - min) / (max - min)
        val thumbX = sliderX + (sliderWidth * percent.coerceIn(0f, 1f)).toInt()
        val isHovered = mouseX in x..x + width && mouseY in y..y + 24

        Gui.drawRect(x, y, x + width, y + 24, if (isHovered) 0xCC444444.toInt() else 0xCC333333.toInt())

        mc.fontRenderer.drawStringWithShadow(value.name, x + 4f, y + 6f, 0xFFFFFFFF.toInt())

        Gui.drawRect(sliderX, y + 10, sliderX + sliderWidth, y + 12, 0xFF555555.toInt())
        Gui.drawRect(sliderX, y + 10, thumbX, y + 12, 0xFF00AAFF.toInt())

        Gui.drawRect(thumbX - 3, y + 7, thumbX + 3, y + 15, 0xFFFFFFFF.toInt())

        mc.fontRenderer.drawStringWithShadow(displayValue, (x + width - 40).toFloat(), y + 6f, 0xFFFFFFFF.toInt())

        if (Mouse.isButtonDown(0) && mouseX in sliderX..sliderX + sliderWidth && mouseY in y..y + 24) {
            val newPercent = (mouseX - sliderX).toFloat() / sliderWidth
            val newValue = min + (max - min) * newPercent.coerceIn(0f, 1f)
            onValueChanged(newValue)
            return true
        }
        return false
    }

    private fun drawChoice(value: ChoiceValue, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int): Boolean {
        val isHovered = mouseX in x..x + width && mouseY in y..y + 20
        Gui.drawRect(x, y, x + width, y + 20, if (isHovered) 0xCC444444.toInt() else 0xCC333333.toInt())

        mc.fontRenderer.drawStringWithShadow(value.name, x + 4f, y + 5f, 0xFFFFFFFF.toInt())

        val currentValue = value.get()
        val arrow = if (isHovered) " §7>§f" else ""
        mc.fontRenderer.drawStringWithShadow("§7$currentValue$arrow", (x + width - 10 - mc.fontRenderer.getStringWidth(currentValue)).toFloat(), y + 5f, 0xFFFFFFFF.toInt())

        if (isHovered && Mouse.isButtonDown(0)) {
            value.set(value.next())
            return true
        }
        return false
    }

    private fun drawDefault(value: Value<*>, x: Int, y: Int, width: Int): Boolean {
        Gui.drawRect(x, y, x + width, y + 18, 0xCC333333.toInt())
        mc.fontRenderer.drawStringWithShadow("${value.name}: ${value.get()}", x + 4f, y + 4f, 0xFFFFFFFF.toInt())
        return false
    }
}