package dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower

import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Slot
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.input.Mouse
import java.util.*

@SideOnly(Side.CLIENT)
class GuiInfiniteBag(player: EntityPlayer, val inventory: InfiniteBagInventory) : GuiContainer(ContainerInfiniteBag(player, inventory)) {
    private val TEXTURE = ResourceLocation("textures/gui/container/generic_54.png")
    private val VISIBLE_ROWS = 6
    private var scrollOffset = 0
    private var searchText = ""
    private lateinit var searchField: GuiTextField
    private val container = inventorySlots as ContainerInfiniteBag

    init {
        xSize = 176
        ySize = 114 + VISIBLE_ROWS * 18
    }

    override fun initGui() {
        super.initGui()
        searchField = GuiTextField(0, fontRenderer, guiLeft + 8, guiTop - 12, 80, 12)
        searchField.setFocused(false)
        searchField.setMaxStringLength(32)
    }

    override fun drawGuiContainerBackgroundLayer(partialTicks: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        mc.textureManager.bindTexture(TEXTURE)
        val x = (width - xSize) / 2
        val y = (height - ySize) / 2

        val usedRows = container.getUsedRows()
        val maxRows = container.getMaxRows()
        val visibleRows = VISIBLE_ROWS.coerceAtMost(maxRows)
        val offset = scrollOffset.coerceIn(0, (usedRows - visibleRows).coerceAtLeast(0))

        val containerHeight = visibleRows * 18 + 17
        drawTexturedModalRect(x, y, 0, 0, xSize, containerHeight)
        drawTexturedModalRect(x, y + containerHeight, 0, 126, xSize, 96)

        val startSlot = offset * 9
        val endSlot = (offset + visibleRows) * 9
        val allSlots = container.inventorySlots
        for (i in allSlots.indices) {
            val slot = allSlots[i]
            if (i < container.TOTAL_SLOTS) {
                val visible = i >= startSlot && i < endSlot && i < inventory.getSizeInventory()
                var matchSearch = true
                if (visible && searchText.isNotEmpty()) {
                    val stack = inventory.getStackInSlot(i)
                    if (!stack.isEmpty) {
                        val name = stack.displayName.lowercase(Locale.ROOT)
                        matchSearch = name.contains(searchText.lowercase(Locale.ROOT))
                    } else {
                        matchSearch = true
                    }
                }
                if (visible && matchSearch) {
                    val row = i / 9 - offset
                    val col = i % 9
                    slot.xPos = 8 + col * 18
                    slot.yPos = 18 + row * 18
                } else {
                    slot.xPos = -1000
                    slot.yPos = -1000
                }
            }
        }

        drawScrollBar(x, y, usedRows, visibleRows, maxRows)
        searchField.drawTextBox()
    }

    private fun drawScrollBar(x: Int, y: Int, usedRows: Int, visibleRows: Int, maxRows: Int) {
        val containerHeight = visibleRows * 18 + 17
        val scrollBarX = x + xSize
        val scrollBarY = y + 18
        val scrollBarHeight = containerHeight - 18
        val maxOffset = (usedRows - visibleRows).coerceAtLeast(0)
        if (maxOffset > 0) {
            drawRect(scrollBarX, scrollBarY, scrollBarX + 8, scrollBarY + scrollBarHeight, 0xFF888888.toInt())
            val sliderHeight = (scrollBarHeight * visibleRows / usedRows.toFloat()).toInt().coerceAtLeast(10)
            val sliderY = scrollBarY + (scrollBarHeight - sliderHeight) * (scrollOffset.toFloat() / maxOffset)
            drawRect(scrollBarX, sliderY.toInt(), scrollBarX + 8, sliderY.toInt() + sliderHeight, 0xFFAAAAAA.toInt())
        }
    }

    override fun drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) {
        fontRenderer.drawString("Infinite Bag", 8, 6, 0x404040)
        fontRenderer.drawString("Inventory", 8, ySize - 96 + 2, 0x404040)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.drawScreen(mouseX, mouseY, partialTicks)
        searchField.drawTextBox()
    }

    override fun renderHoveredToolTip(mouseX: Int, mouseY: Int) {
        val slot = getSlotUnderMouse()
        if (slot != null && slot.hasStack) {
            val stack = slot.stack
            if (!stack.isEmpty) {
                val name = stack.displayName.lowercase(Locale.ROOT)
                if (searchText.isEmpty() || name.contains(searchText.lowercase(Locale.ROOT))) {
                    renderToolTip(stack, mouseX, mouseY)
                }
            }
        }
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel != 0) {
            val usedRows = container.getUsedRows()
            val visibleRows = VISIBLE_ROWS.coerceAtMost(container.getMaxRows())
            val maxOffset = (usedRows - visibleRows).coerceAtLeast(0)
            if (maxOffset > 0) {
                scrollOffset = (scrollOffset - wheel / 120).coerceIn(0, maxOffset)
            }
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (searchField.isFocused) {
            searchField.textboxKeyTyped(typedChar, keyCode)
            searchText = searchField.text
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val x = (width - xSize) / 2
        val y = (height - ySize) / 2
        val visibleRows = VISIBLE_ROWS.coerceAtMost(container.getMaxRows())
        val usedRows = container.getUsedRows()
        val maxOffset = (usedRows - visibleRows).coerceAtLeast(0)
        if (maxOffset > 0) {
            val scrollBarX = x + xSize
            val scrollBarY = y + 18
            val scrollBarHeight = (visibleRows * 18 + 17) - 18
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 8 && mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                val ratio = (mouseY - scrollBarY).toFloat() / scrollBarHeight
                scrollOffset = (ratio * maxOffset).toInt().coerceIn(0, maxOffset)
                return
            }
        }
        searchField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun getSlotUnderMouse(): Slot? {
        val x = (width - xSize) / 2
        val y = (height - ySize) / 2
        val mouseX = Mouse.getX() * width / mc.displayWidth
        val mouseYPos = height - Mouse.getY() * height / mc.displayHeight - 1
        val visibleRows = VISIBLE_ROWS.coerceAtMost(container.getMaxRows())
        val offset = scrollOffset
        val startSlot = offset * 9
        val endSlot = (offset + visibleRows) * 9

        for (i in startSlot until endSlot.coerceAtMost(inventory.getSizeInventory())) {
            val row = i / 9 - offset
            val col = i % 9
            val slotX = x + 8 + col * 18
            val slotY = y + 18 + row * 18
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseYPos >= slotY && mouseYPos < slotY + 16) {
                val slot = container.getSlot(i)
                if (slot != null && slot.isEnabled) {
                    val stack = slot.stack
                    if (searchText.isNotEmpty()) {
                        val name = stack.displayName.lowercase(Locale.ROOT)
                        return if (name.contains(searchText.lowercase(Locale.ROOT))) {
                            slot
                        } else {
                            null
                        }
                    }
                    return slot
                }
            }
        }

        return super.getSlotUnderMouse()
    }

    companion object {
        private const val TOTAL_SLOTS = 486
    }
}