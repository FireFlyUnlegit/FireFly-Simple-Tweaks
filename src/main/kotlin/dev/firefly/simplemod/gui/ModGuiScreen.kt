package dev.firefly.simplemod.gui

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.core.Module
import dev.firefly.simplemod.core.ModuleManager
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

class ModGuiScreen : GuiScreen() {
    private var scrollOffset = 0f
    private var expandedModule: Module? = null
    private var bindingModule: Module? = null
    private var bindingGuiKey: Boolean = false

    private val panelWidth = 200
    private val panelHeight = 22
    private val padding = 4
    private val settingsPadding = 10

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()

        drawCenteredString(fontRenderer, "§lSimple Optimization Mod", width / 2, 8, 0xFFFFFF)

        // ✅ 显示当前 GUI 按键
        val guiKeyName = if (bindingGuiKey) "§e按任意键..." else "§7GUI 按键: §f${Keyboard.getKeyName(SimpleMod.guiKey)}"
        drawCenteredString(fontRenderer, guiKeyName, width / 2, 22, 0x888888)
        drawCenteredString(fontRenderer, "§7Click name to toggle | Click key to bind | Click right to expand", width / 2, 36, 0x666666)

        val modules = ModuleManager.getAll()
        val startX = width / 2 - panelWidth / 2

        GlStateManager.pushMatrix()
        GlStateManager.translate(0f, scrollOffset, 0f)

        var currentY = 60
        var totalHeight = 60

        // ✅ 在模块列表顶部添加 GUI 按键设置行
        drawGuiKeySetting(startX, currentY, mouseX, mouseY)
        currentY += panelHeight + padding
        totalHeight = currentY

        modules.forEach { module ->
            val y = currentY
            drawModulePanel(module, startX, y, mouseX, mouseY)
            currentY += panelHeight + padding
            if (expandedModule == module) {
                currentY += drawModuleSettings(module, startX, y + panelHeight, mouseX, mouseY, partialTicks)
            }
            totalHeight = currentY
        }

        GlStateManager.popMatrix()

        val scroll = Mouse.getDWheel()
        if (scroll != 0) {
            val maxScroll = (totalHeight - height + 50).coerceAtLeast(0)
            scrollOffset = (scrollOffset - scroll * 0.5f).coerceIn(-maxScroll.toFloat(), 0f)
        }

        if (bindingModule != null || bindingGuiKey) {
            val msg = if (bindingGuiKey) "§e按任意键设置 GUI 打开按键，按 ESC 取消"
            else "§e按任意键绑定到 ${bindingModule!!.name}，按 ESC 取消"
            drawCenteredString(fontRenderer, msg, width / 2, height - 20, 0xFFFFFF)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    // ✅ GUI 按键设置面板
    private fun drawGuiKeySetting(x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX in x..x + panelWidth && mouseY in y..y + panelHeight
        val keyWidth = 80
        val keyX = x + panelWidth - keyWidth
        val isKeyHovered = mouseX in keyX..keyX + keyWidth && mouseY in y..y + panelHeight

        val bgColor = when {
            isKeyHovered -> 0xCCFF8800.toInt()
            isHovered -> 0xCC555555.toInt()
            else -> 0xCC333333.toInt()
        }
        drawRect(x, y, x + panelWidth, y + panelHeight, bgColor)

        fontRenderer.drawStringWithShadow("§l[Global]", x + 4f, y + 6f, 0xFFAAAAAA.toInt())

        val keyText = if (bindingGuiKey) "§e按任意键..." else "§7${Keyboard.getKeyName(SimpleMod.guiKey)}"
        val keyColor = if (bindingGuiKey) 0xFFFFFF00.toInt() else 0xFFAAAAAA.toInt()
        fontRenderer.drawStringWithShadow(keyText, (keyX + 4).toFloat(), y + 6f, keyColor)

        if (Mouse.isButtonDown(0) && isKeyHovered) {
            bindingGuiKey = true
            bindingModule = null
        }
    }

    private fun drawModulePanel(module: Module, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val isExpanded = expandedModule == module
        val isHovered = mouseX in x..x + panelWidth && mouseY in y..y + panelHeight
        val keyWidth = 50
        val keyX = x + panelWidth - keyWidth
        val isKeyHovered = mouseX in keyX..keyX + keyWidth && mouseY in y..y + panelHeight

        val bgColor = when {
            isKeyHovered -> 0xCCFF8800.toInt()
            isHovered -> 0xCC555555.toInt()
            else -> 0xCC333333.toInt()
        }
        drawRect(x, y, x + panelWidth, y + panelHeight, bgColor)

        if (isExpanded) {
            drawRect(x, y, x + panelWidth, y + 1, 0xFF00AAFF.toInt())
        }

        val dotColor = if (module.isEnabled()) 0xFF00FF00.toInt() else 0xFFFF0000.toInt()
        drawRect(x + 4, y + 4, x + 8, y + 8, dotColor)

        val nameColor = if (module.isEnabled()) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()
        fontRenderer.drawStringWithShadow(module.name, x + 14f, y + 6f, nameColor)

        val keyText = if (bindingModule == module) "§ePress key..." else "§7${module.getKeyName()}"
        val keyColor = if (bindingModule == module) 0xFFFFFF00.toInt() else 0xFFAAAAAA.toInt()
        fontRenderer.drawStringWithShadow(keyText, (keyX + 4).toFloat(), y + 6f, keyColor)

        if (isHovered && module.description.isNotEmpty()) {
            drawHoveringText(listOf("§7${module.description}"), mouseX, mouseY)
        }

        if (Mouse.isButtonDown(0)) {
            if (isKeyHovered) {
                bindingModule = if (bindingModule == module) null else module
                bindingGuiKey = false
                return
            }
            if (isHovered && mouseX < keyX) {
                module.toggle()
                return
            }
        }
        if (isHovered && Mouse.isButtonDown(1)) {
            expandedModule = if (isExpanded) null else module
        }
    }

    private fun drawModuleSettings(
        module: Module,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ): Int {
        var currentY = y + 4
        val settingsX = x + settingsPadding
        val settingsWidth = panelWidth - settingsPadding * 2

        val settingsHeight = module.values.size * 26 + 8
        drawRect(x, y, x + panelWidth, y + settingsHeight, 0xCC222222.toInt())

        module.values.forEach { value ->
            Component.draw(
                value = value,
                x = settingsX,
                y = currentY,
                width = settingsWidth,
                mouseX = mouseX,
                mouseY = mouseY,
                partialTicks = partialTicks
            )
            currentY += 24 + 2
        }

        return currentY - y + 4
    }

    override fun handleKeyboardInput() {
        if (bindingGuiKey) {
            val key = Keyboard.getEventKey()
            if (key == Keyboard.KEY_ESCAPE) {
                bindingGuiKey = false
            } else if (key != Keyboard.KEY_NONE) {
                SimpleMod.setGuiKey(key)
                bindingGuiKey = false
            }
            return
        }

        if (bindingModule != null) {
            val key = Keyboard.getEventKey()
            if (key == Keyboard.KEY_ESCAPE) {
                bindingModule = null
            } else if (key != Keyboard.KEY_NONE) {
                bindingModule!!.keyBind = key
                bindingModule = null
            }
            return
        }
        super.handleKeyboardInput()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
        ModuleManager.getAll().forEach { it.save() }
    }

    override fun doesGuiPauseGame(): Boolean = false
}