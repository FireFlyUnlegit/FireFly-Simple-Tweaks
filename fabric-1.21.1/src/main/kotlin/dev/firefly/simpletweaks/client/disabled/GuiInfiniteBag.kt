package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.ContainerInfiniteBag
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.Locale

/**
 * Client GUI for the `infinite_power` bag: a 6-row scrolling window onto a growable 54-row inventory,
 * with a search box.
 *
 * Port of 1.12.2 `infinitepower/GuiInfiniteBag.kt` (200 lines), which extended `GuiContainer`.
 *
 * <h2>Yarn 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `GuiContainer` | `HandledScreen<T>` |
 * | `drawGuiContainerBackgroundLayer` | `drawBackground(DrawContext, float, int, int)` |
 * | `drawGuiContainerForegroundLayer` | `drawForeground(DrawContext, int, int)` |
 * | `GlStateManager.color` + `bindTexture` + `drawTexturedModalRect` | `DrawContext#drawTexture` |
 * | `drawRect` | `DrawContext#fill` |
 * | `GuiTextField` | `TextFieldWidget` |
 * | `Mouse.getEventDWheel()` in `handleMouseInput` | `mouseScrolled(...)` |
 * | `keyTyped(char, int)` | `keyPressed(int, int, int)` |
 * | `mc`, `fontRenderer`, `guiLeft`/`guiTop` | `client`, `textRenderer`, `x`/`y` |
 * | `xSize`/`ySize`, `inventorySlots`, `container` | `backgroundWidth`/`backgroundHeight`, `handler` |
 *
 * <h2>Three parts of 1.12.2's version are deliberately NOT ported</h2>
 * <ol>
 *   <li><b>`getSlotUnderMouse` (35 lines)</b> — it re-implemented the hit test solely so that slots
 *       filtered out by the search box could not be hovered. Here those slots are parked at
 *       `(-1000, -1000)`, and `HandledScreen#getSlotAt` already bounds-checks every slot, so they are
 *       skipped by the vanilla path. Re-implementing it would be a second copy of the same rule.</li>
 *   <li><b>`renderHoveredToolTip`</b> — the override only re-applied the search filter before calling
 *       the superclass; with (1) gone, the vanilla tooltip path is already correct.</li>
 *   <li><b>The second `searchField.drawTextBox()` in `drawScreen`</b> — 1.12.2 drew the field twice per
 *       frame (once from the background layer, once after `super.drawScreen`). Drawing it once from
 *       `drawBackground` is what the first call was for; the duplicate was an artefact of the field
 *       living outside the slot list, not a feature.</li>
 * </ol>
 *
 * <h2>Slot repositioning, kept as-is</h2>
 * The scroll window is implemented the way 1.12.2 did it: every frame, slots inside the window are
 * moved to their visible coordinates and everything else is parked at `-1000`. That keeps the
 * **handler's** slot indices fixed (bag slot `i` is bag slot `i` on both sides), which is what makes
 * the server agree with the client about what was clicked. The cost is 531 coordinate writes per
 * frame, which is what the original paid too.
 */
class GuiInfiniteBag(
    handler: ContainerInfiniteBag,
    playerInventory: PlayerInventory,
    title: Text,
) : HandledScreen<ContainerInfiniteBag>(handler, playerInventory, title) {

    private val container = handler
    private val inventory = handler.inventory

    private val visibleRows = VISIBLE_ROWS
    private var scrollOffset = 0
    private var searchText = ""
    private lateinit var searchField: TextFieldWidget

    init {
        backgroundWidth = 176
        backgroundHeight = 114 + VISIBLE_ROWS * 18
    }

    override fun init() {
        super.init()
        searchField = TextFieldWidget(textRenderer, x + 8, y - 12, 80, 12, Text.empty())
        searchField.setMaxLength(32)
        searchField.setDrawsBackground(true)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val usedRows = container.inventory.usedRows()
        val maxRows = container.inventory.maxRows()
        val rows = visibleRows.coerceAtMost(maxRows)
        val offset = scrollOffset.coerceIn(0, (usedRows - rows).coerceAtLeast(0))

        val containerHeight = rows * 18 + 17
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, containerHeight)
        context.drawTexture(TEXTURE, x, y + containerHeight, 0, 126, backgroundWidth, 96)

        val startSlot = offset * 9
        val endSlot = (offset + rows) * 9
        for (i in container.slots.indices) {
            if (i >= ContainerInfiniteBag.TOTAL_SLOTS) continue
            val slot = container.slots[i]
            val visible = i in startSlot until endSlot && i < inventory.size()
            if (visible && matchesSearch(i)) {
                slot.x = 8 + (i % 9) * 18
                slot.y = 18 + (i / 9 - offset) * 18
            } else {
                slot.x = PARKED
                slot.y = PARKED
            }
        }

        drawScrollBar(context, usedRows, rows)
        searchField.render(context, mouseX, mouseY, delta)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        // Hardcoded in 1.12.2 as well (`fontRenderer.drawString("Infinite Bag", 8, 6, 0x404040)`).
        context.drawText(textRenderer, "Infinite Bag", 8, 6, 0x404040, false)
        context.drawText(textRenderer, "Inventory", 8, backgroundHeight - 94, 0x404040, false)
    }

    private fun matchesSearch(index: Int): Boolean {
        if (searchText.isEmpty()) return true
        val stack = inventory.getStack(index)
        if (stack.isEmpty) return true
        return stack.name.string.lowercase(Locale.ROOT).contains(searchText.lowercase(Locale.ROOT))
    }

    private fun drawScrollBar(context: DrawContext, usedRows: Int, rows: Int) {
        val containerHeight = rows * 18 + 17
        val barX = x + backgroundWidth
        val barY = y + 18
        val barHeight = containerHeight - 18
        val maxOffset = (usedRows - rows).coerceAtLeast(0)
        if (maxOffset <= 0) return

        context.fill(barX, barY, barX + 8, barY + barHeight, 0xFF888888.toInt())
        val sliderHeight = (barHeight * rows / usedRows.toFloat()).toInt().coerceAtLeast(10)
        val sliderY = barY + ((barHeight - sliderHeight) * (scrollOffset.toFloat() / maxOffset)).toInt()
        context.fill(barX, sliderY, barX + 8, sliderY + sliderHeight, 0xFFAAAAAA.toInt())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val maxOffset = maxOffset()
        if (maxOffset > 0) {
            scrollOffset = (scrollOffset - vertical.toInt()).coerceIn(0, maxOffset)
        }
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val maxOffset = maxOffset()
        if (maxOffset > 0) {
            val barX = x + backgroundWidth
            val barY = y + 18
            val barHeight = (visibleRows * 18 + 17) - 18
            if (mouseX >= barX && mouseX <= barX + 8 && mouseY >= barY && mouseY <= barY + barHeight) {
                val ratio = (mouseY - barY) / barHeight
                scrollOffset = (ratio * maxOffset).toInt().coerceIn(0, maxOffset)
                return true
            }
        }
        if (searchField.mouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchField.isFocused) {
            if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
                searchText = searchField.text
                return true
            }
            // Fall through for keys the field does not consume (Escape closing the screen, …).
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun maxOffset(): Int {
        val rows = visibleRows.coerceAtMost(container.inventory.maxRows())
        return (container.inventory.usedRows() - rows).coerceAtLeast(0)
    }

    companion object {

        private const val VISIBLE_ROWS = 6

        /** Where out-of-window slots are parked so vanilla's own hit test skips them. */
        private const val PARKED = -1000

        /** 1.12.2 `ResourceLocation("textures/gui/container/generic_54.png")`. */
        private val TEXTURE: Identifier = Identifier.ofVanilla("textures/gui/container/generic_54.png")
    }
}
