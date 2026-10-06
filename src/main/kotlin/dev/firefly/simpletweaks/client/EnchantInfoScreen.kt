package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.enchantments.EnchantmentMeta
import dev.firefly.simpletweaks.enchantments.EnchantmentNameColors
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW

/**
 * In-game enchantment index, opened by `/enchantinfo`. Port of 1.12.2 `gui/GuiEnchantInfo.kt`
 * (317 lines).
 *
 * <h2>Layout, copied from 1.12.2</h2>
 * Two views in one screen:
 * <ol>
 *   <li><b>Category menu</b> — a 4-column grid of coloured wool icons, one per 1.12.2 tier. Hovering
 *       shows the tier name, how many enchantments it holds, and a click hint.</li>
 *   <li><b>Enchantment list</b> — a 9x5 grid of glinting enchanted books for the selected tier, paged
 *       with the mouse wheel (a 1.12.2 screen has up to 45 entries per page), with a back arrow.
 *       Hovering shows name / category / applies-to / max level / description.</li>
 * </ol>
 * All the geometry constants below are 1.12.2's, **except the category menu's arrangement**: the
 * original laid the eight categories out with `idx % 4` into a 4x2 grid, while this port uses two
 * explicit centred rows (see [categoryRows]). The list view's grid, the back-arrow position and the
 * page indicator are unchanged.
 *
 * <h2>What had to change, and why</h2>
 * <table>
 *   <tr><th>1.12.2</th><th>here</th></tr>
 *   <tr><td>`EnchantmentCategories` enum</td><td>[EnchantmentMeta.category]</td></tr>
 *   <tr><td>`ModEnchantmentType` enum</td><td>[EnchantmentMeta.type]</td></tr>
 *   <tr><td>`ench.getMaxLevel()`</td><td>[EnchantmentMeta.maxLevel]</td></tr>
 *   <tr><td>iterate `Enchantment.REGISTRY`</td><td>iterate `EnchantmentMeta.allKeys()` — the legacy table **plus** every `@ModEnchantment` declaration</td></tr>
 *   <tr><td>`ItemStack(Blocks.WOOL, 1, meta)`</td><td>the matching 1.21 `Items.*_WOOL`</td></tr>
 *   <tr><td>`ItemEnchantedBook.addEnchantment(book, ...)`</td><td>`ENCHANTMENT_GLINT_OVERRIDE`</td></tr>
 *   <tr><td>`drawTexturedModalRect(generic_54.png)`</td><td>`DrawContext.fill` panels</td></tr>
 * </table>
 *
 * <p><b>Why the tier/type/max-level tables are generated rather than read from the registry:</b> 1.21
 * enchantments are data-driven, so none of the three concepts exists in code — and reading them back
 * from the client's registry would add a "is the registry synced yet" failure mode for information
 * that is fixed at build time. The whole screen is therefore static data plus rendering.
 *
 * <p><b>Two deliberate cosmetic deviations</b>, both recorded in `docs/phase6-client-notes.md`:
 * <ul>
 *   <li>The panel is drawn as flat fills instead of blitting the vanilla double-chest texture
 *       (`generic_54.png`). 1.21's `DrawContext.drawTexture` signature is considerably more involved
 *       than 1.12.2's `drawTexturedModalRect`, and the panel is decorative.</li>
 *   <li>Books get their glint from `ENCHANTMENT_GLINT_OVERRIDE` rather than from a real
 *       `STORED_ENCHANTMENTS` component. Building that component needs an
 *       `ItemEnchantmentsComponent.Builder`, whose only constructor takes an existing component, and a
 *       `RegistryEntry<Enchantment>` — `Registry` has no `getEntry(RegistryKey)` in 1.21.1 (verified
 *       from the jar). The override component gives the same visual with none of that. Consequence:
 *       the books are not real enchanted books, so if a future feature inspects them it must not rely
 *       on the component.</li>
 * </ul>
 *
 * <p>The tooltip name is built from the lang key plus the generated tier colour rather than from
 * `Enchantment.getName`, for the same "no registry" reason. One visible consequence: `infinite_power`
 * shows its tier colour here instead of its animated rainbow name.
 */
class EnchantInfoScreen(private val parent: Screen?) :
    Screen(Text.translatable("gui.simple_tweaks.enchant_info.title")) {

    private companion object {
        const val MAIN_WIDTH = 176
        const val MAIN_HEIGHT = 17 + 3 * 18 + 4
        const val LIST_COLS = 9
        const val LIST_ROWS = 5
        const val LIST_WIDTH = 176
        const val LIST_HEIGHT = 17 + 6 * 18 + 4
        const val PER_PAGE = LIST_COLS * LIST_ROWS
        const val PANEL_BG = 0xC0101010.toInt()
        const val PANEL_BORDER = 0xFF555555.toInt()
        const val WRAP_WIDTH = 200
    }

    /**
     * Category order, laid out as **two explicit rows** rather than 1.12.2's 4-column grid.
     *
     * <p>Requested by the author as a readability improvement: the main rarity ladder reads left to
     * right on the top row, and the three special tiers sit below it. This is a deliberate divergence
     * from 1.12.2's `CATEGORIES` list + `idx % 4` layout, not a porting mistake.
     *
     * <p>Rows are centred independently, so the 3-item row is not left-aligned under the 5-item one.
     */
    private val categoryRows: List<List<String>> = listOf(
        listOf("common", "uncommon", "rare", "epic", "legendary"),
        listOf("unique", "mythic", "mystery"),
    )

    /**
     * Icon and colour per category. The wool items match the 1.12.2 dye metadata the original used
     * (0 white, 7 gray, 13 green, 11 blue, 2 magenta, 4 yellow, 14 red, 3 light blue); the
     * [Formatting] values are 1.12.2 `EnchantmentCategories.color`.
     */
    private val categoryIcons: Map<String, Pair<Item, Formatting>> = mapOf(
        "unique" to (Items.WHITE_WOOL to Formatting.WHITE),
        "common" to (Items.GRAY_WOOL to Formatting.GRAY),
        "uncommon" to (Items.GREEN_WOOL to Formatting.GREEN),
        "rare" to (Items.BLUE_WOOL to Formatting.BLUE),
        "epic" to (Items.MAGENTA_WOOL to Formatting.LIGHT_PURPLE),
        "legendary" to (Items.YELLOW_WOOL to Formatting.GOLD),
        "mythic" to (Items.RED_WOOL to Formatting.DARK_RED),
        "mystery" to (Items.LIGHT_BLUE_WOOL to Formatting.AQUA),
    )

    /** One category icon's screen position, computed once so rendering and hit-testing cannot drift. */
    private data class Slot(val category: String, val x: Int, val y: Int)

    private var slots: List<Slot> = emptyList()

    private fun buildSlots(): List<Slot> {
        val out = mutableListOf<Slot>()
        categoryRows.forEachIndexed { rowIndex, row ->
            val rowWidth = row.size * 18
            val startX = guiLeft + (MAIN_WIDTH - rowWidth) / 2
            // 1.12.2's first row also began at guiTop + 36; the row pitch stays 18.
            val y = guiTop + 36 + rowIndex * 18
            row.forEachIndexed { col, category -> out.add(Slot(category, startX + col * 18, y)) }
        }
        return out
    }

    /**
     * id -> tier, and the reverse, both derived once from the merged key set.
     *
     * The keys come from [EnchantmentMeta], not `ModEnchantmentKeys.ALL`: the latter is generated from
     * the 1.12.2 sources and by definition cannot know about an enchantment declared with
     * `@ModEnchantment`. Reading it directly is exactly how `fast_bow` went missing from this screen.
     */
    private val idsByCategory: Map<String, List<String>> =
        EnchantmentMeta.allKeys()
            .map { it.value.path }
            .sorted()
            .groupBy { EnchantmentMeta.category(it) ?: "common" }

    private var selected: String? = null
    private var page = 0
    private var guiLeft = 0
    private var guiTop = 0

    override fun init() {
        val w = if (selected == null) MAIN_WIDTH else LIST_WIDTH
        val h = if (selected == null) MAIN_HEIGHT else LIST_HEIGHT
        guiLeft = (this.width - w) / 2
        guiTop = (this.height - h) / 2
        slots = if (selected == null) buildSlots() else emptyList()
    }

    // ------------------------------------------------------------------ rendering

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        if (selected == null) drawMainMenu(context, mouseX, mouseY) else drawList(context, mouseX, mouseY)
    }

    private fun drawPanel(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER)
        context.fill(x, y, x + w, y + h, PANEL_BG)
    }

    private fun drawMainMenu(context: DrawContext, mouseX: Int, mouseY: Int) {
        drawPanel(context, guiLeft, guiTop, MAIN_WIDTH, MAIN_HEIGHT)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("gui.simple_tweaks.enchant_info.title").formatted(Formatting.GOLD),
            guiLeft + MAIN_WIDTH / 2, guiTop + 6, 0xFFFFFF,
        )

        var hovered: Slot? = null
        for (slot in slots) {
            val icon = categoryIcons[slot.category] ?: continue
            val stack = ItemStack(icon.first)
            context.drawItem(stack, slot.x, slot.y)
            context.drawItemInSlot(this.textRenderer, stack, slot.x, slot.y)

            if (mouseX in slot.x until slot.x + 16 && mouseY in slot.y until slot.y + 16) {
                hovered = slot
            }
        }

        hovered?.let { slot ->
            val count = idsByCategory[slot.category]?.size ?: 0
            val colour = categoryIcons[slot.category]?.second ?: Formatting.WHITE
            context.drawTooltip(
                this.textRenderer,
                listOf(
                    categoryText(slot.category).formatted(colour),
                    Text.translatable("gui.simple_tweaks.enchant_info.count", count)
                        .formatted(Formatting.GRAY),
                    Text.empty(),
                    Text.translatable("gui.simple_tweaks.enchant_info.click_hint")
                        .formatted(Formatting.YELLOW),
                ),
                mouseX, mouseY,
            )
        }
    }

    private fun drawList(context: DrawContext, mouseX: Int, mouseY: Int) {
        drawPanel(context, guiLeft, guiTop, LIST_WIDTH, LIST_HEIGHT)

        val cat = selected ?: return
        val colour = categoryIcons[cat]?.second ?: Formatting.WHITE
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            categoryText(cat).formatted(colour),
            guiLeft + LIST_WIDTH / 2, guiTop + 6, 0xFFFFFF,
        )

        val ids = idsByCategory[cat].orEmpty()
        val maxPage = ((ids.size - 1).coerceAtLeast(0)) / PER_PAGE
        page = page.coerceIn(0, maxPage)
        val start = page * PER_PAGE

        var hovered: String? = null
        for (i in 0 until PER_PAGE) {
            val idx = start + i
            if (idx >= ids.size) break
            val id = ids[idx]
            val col = i % LIST_COLS
            val row = i / LIST_COLS
            val x = guiLeft + 8 + col * 18
            val y = guiTop + 18 + row * 18

            val stack = glintingBook()
            context.drawItem(stack, x, y)
            context.drawItemInSlot(this.textRenderer, stack, x, y)

            if (mouseX in x until x + 16 && mouseY in y until y + 16) hovered = id
        }

        // Back arrow, where 1.12.2 put it: first column, just under the grid.
        val backX = guiLeft + 8
        val backY = guiTop + 18 + LIST_ROWS * 18
        val back = ItemStack(Items.ARROW)
        context.drawItem(back, backX, backY)
        context.drawItemInSlot(this.textRenderer, back, backX, backY)
        val overBack = mouseX in backX until backX + 16 && mouseY in backY until backY + 16
        if (overBack) {
            context.drawTooltip(
                this.textRenderer,
                listOf(Text.translatable("gui.simple_tweaks.enchant_info.back").formatted(Formatting.YELLOW)),
                mouseX, mouseY,
            )
        }

        if (maxPage > 0) {
            val pageText = Text.literal("${page + 1} / ${maxPage + 1}")
            context.drawTextWithShadow(
                this.textRenderer, pageText,
                guiLeft + LIST_WIDTH - 8 - this.textRenderer.getWidth(pageText),
                guiTop + 6, 0xCCCCCC,
            )
        }

        if (!overBack) {
            hovered?.let {
                context.drawTooltip(this.textRenderer, buildTooltip(it), mouseX, mouseY)
            }
        }
    }

    /** A book with the glint forced on — see the class KDoc for why not a real enchanted book. */
    private fun glintingBook(): ItemStack =
        ItemStack(Items.ENCHANTED_BOOK).apply {
            set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

    private fun categoryText(category: String): MutableText =
        Text.translatable("gui.simple_tweaks.category.$category")

    // ------------------------------------------------------------------ tooltip

    private fun buildTooltip(id: String): List<Text> {
        val lines = mutableListOf<Text>()

        val colour = EnchantmentNameColors.of(id)
        val name = Text.translatable("enchantment.simple_tweaks.$id")
        lines.add(if (colour != null) name.formatted(colour) else name)

        // All three lookups go through EnchantmentMeta so an enchantment declared with
        // @ModEnchantment shows its real tier / "applies to" / max level instead of the "common"
        // fallback and a bogus max level of 1.
        val cat = EnchantmentMeta.category(id)
        if (cat != null) {
            lines.add(
                Text.translatable("gui.simple_tweaks.enchant_info.category", categoryText(cat))
                    .formatted(EnchantmentNameColors.of(id) ?: Formatting.WHITE)
            )
        }

        val typeKey = appliesToKey(EnchantmentMeta.type(id))
        if (typeKey != null) {
            lines.add(
                Text.translatable(
                    "gui.simple_tweaks.enchant_info.applies_to",
                    Text.translatable("gui.simple_tweaks.enchant_info.applies_to.$typeKey"),
                ).formatted(Formatting.AQUA)
            )
        }

        lines.add(
            Text.translatable(
                "gui.simple_tweaks.enchant_info.max_level",
                EnchantmentMeta.maxLevel(id) ?: 1,
            ).formatted(Formatting.GRAY)
        )
        lines.add(Text.empty())

        val descKey = "enchantment.simple_tweaks.$id.desc"
        val desc = Text.translatable(descKey)
        // 1.12.2 checked `desc != descKey` for the same reason: an unresolved key comes back as itself.
        if (desc.string != descKey) {
            for (line in wrap(desc.string, WRAP_WIDTH)) {
                lines.add(Text.literal(line).formatted(Formatting.WHITE))
            }
        }
        return lines
    }

    /** 1.12.2 spelled the WEAPON case `sword_bow` in the lang file; every other case is the name. */
    private fun appliesToKey(type: String?): String? = when (type) {
        null, "" -> null
        "weapon" -> "sword_bow"
        else -> type
    }

    /** 1.12.2's greedy word wrap, with the same 200-unit limit. */
    private fun wrap(text: String, maxWidth: Int): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (sb.isEmpty()) word else "$sb $word"
            if (this.textRenderer.getWidth(candidate) > maxWidth && sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
                sb.append(word)
            } else {
                sb.setLength(0)
                sb.append(candidate)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    // ------------------------------------------------------------------ input

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (selected == null) {
            for (slot in slots) {
                if (mx in slot.x until slot.x + 16 && my in slot.y until slot.y + 16) {
                    selected = slot.category
                    page = 0
                    clearAndInit()
                    return true
                }
            }
        } else {
            val backX = guiLeft + 8
            val backY = guiTop + 18 + LIST_ROWS * 18
            if (mx in backX until backX + 16 && my in backY until backY + 16) {
                selected = null
                clearAndInit()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double,
    ): Boolean {
        val cat = selected ?: return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        if (verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val maxPage = ((idsByCategory[cat].orEmpty().size - 1).coerceAtLeast(0)) / PER_PAGE
        if (verticalAmount < 0 && page < maxPage) page++ else if (verticalAmount > 0 && page > 0) page--
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // 1.12.2: the inventory key left the list view the same way Escape does.
        val inventoryKey = this.client?.options?.inventoryKey?.matchesKey(keyCode, scanCode) == true
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || inventoryKey) {
            if (selected != null) {
                selected = null
                clearAndInit()
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        this.client?.setScreen(parent)
    }
}
