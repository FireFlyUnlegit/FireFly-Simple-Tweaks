package dev.firefly.simpletweaks.gui

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.resources.I18n
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentData
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.ItemEnchantedBook
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import org.lwjgl.input.Mouse

class GuiEnchantInfo : GuiScreen() {

    private val BACKGROUND = ResourceLocation("textures/gui/container/generic_54.png")

    private val CATEGORIES = listOf(
        EnchantmentCategories.UNIQUE,
        EnchantmentCategories.COMMON,
        EnchantmentCategories.UNCOMMON,
        EnchantmentCategories.RARE,
        EnchantmentCategories.EPIC,
        EnchantmentCategories.LEGENDARY,
        EnchantmentCategories.MYTHIC,
        EnchantmentCategories.MYSTERY,
    )

    private val CATEGORY_WOOL_META = mapOf(
        EnchantmentCategories.UNIQUE to 0,
        EnchantmentCategories.COMMON to 7,
        EnchantmentCategories.UNCOMMON to 13,
        EnchantmentCategories.RARE to 11,
        EnchantmentCategories.EPIC to 2,
        EnchantmentCategories.LEGENDARY to 4,
        EnchantmentCategories.MYTHIC to 14,
        EnchantmentCategories.MYSTERY to 3,
    )

    private var selected: EnchantmentCategories? = null
    private var page = 0

    private var guiLeft = 0
    private var guiTop = 0

    private val mainCols = 4
    private val mainWidth = 176
    private val mainHeight = 17 + 3 * 18 + 4

    private val listCols = 9
    private val listRows = 5
    private val listWidth = 176
    private val listHeight = 17 + 6 * 18 + 4
    private val perPage = listCols * listRows

    override fun initGui() {
        super.initGui()
        val w = if (selected == null) mainWidth else listWidth
        val h = if (selected == null) mainHeight else listHeight
        guiLeft = (width - w) / 2
        guiTop = (height - h) / 2
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        if (selected == null) drawMainMenu(mouseX, mouseY) else drawEnchantList(mouseX, mouseY)
    }

    private fun drawMainMenu(mouseX: Int, mouseY: Int) {
        mc.renderEngine.bindTexture(BACKGROUND)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, mainWidth, 17)
        for (i in 0 until 3) {
            drawTexturedModalRect(guiLeft, guiTop + 17 + i * 18, 0, 17 + i * 18, mainWidth, 18)
        }
        drawTexturedModalRect(guiLeft, guiTop + 17 + 3 * 18, 0, 17 + 3 * 18, mainWidth, 4)

        drawCenteredString(
            mc.fontRenderer,
            TextFormatting.GOLD.toString() + I18n.format("gui.simple_tweaks.enchant_info.title"),
            guiLeft + mainWidth / 2, guiTop + 6, 0xFFFFFF
        )

        var hoveredCategory: EnchantmentCategories? = null

        CATEGORIES.forEachIndexed { idx, cat ->
            val col = idx % mainCols
            val row = idx / mainCols
            val x = guiLeft + 8 + 18 + col * 18 + 18
            val y = guiTop + 18 + row * 18 + 18

            val meta = CATEGORY_WOOL_META[cat] ?: 0
            val stack = ItemStack(Blocks.WOOL, 1, meta)
            itemRender.renderItemAndEffectIntoGUI(stack, x, y)
            itemRender.renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null)

            if (mouseX in x until x + 16 && mouseY in y until y + 16) hoveredCategory = cat
        }

        if (hoveredCategory != null) {
            val cat = hoveredCategory
            val count = countEnchants(cat)
            val name = cat.color.toString() + I18n.format("gui.simple_tweaks.category.${cat.name.lowercase()}")
            drawHoveringText(
                listOf(
                    name,
                    TextFormatting.GRAY.toString() + I18n.format("gui.simple_tweaks.enchant_info.count", count),
                    "",
                    TextFormatting.YELLOW.toString() + I18n.format("gui.simple_tweaks.enchant_info.click_hint"),
                ),
                mouseX, mouseY
            )
        }
    }

    private fun drawEnchantList(mouseX: Int, mouseY: Int) {
        mc.renderEngine.bindTexture(BACKGROUND)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, listWidth, 17)
        for (i in 0 until 6) {
            drawTexturedModalRect(guiLeft, guiTop + 17 + i * 18, 0, 17 + i * 18, listWidth, 18)
        }
        drawTexturedModalRect(guiLeft, guiTop + 17 + 6 * 18, 0, 17 + 6 * 18, listWidth, 4)

        val cat = selected ?: return
        val title = cat.color.toString() + I18n.format("gui.simple_tweaks.category.${cat.name.lowercase()}")
        drawCenteredString(mc.fontRenderer, title, guiLeft + listWidth / 2, guiTop + 6, 0xFFFFFF)

        val enchants = enchantsOf(cat)
        val maxPage = ((enchants.size - 1).coerceAtLeast(0)) / perPage
        page = page.coerceIn(0, maxPage)

        val start = page * perPage
        var hoveredEnch: Enchantment? = null

        for (i in 0 until perPage) {
            val idx = start + i
            if (idx >= enchants.size) break
            val ench = enchants[idx]
            val col = i % listCols
            val row = i / listCols
            val x = guiLeft + 8 + col * 18
            val y = guiTop + 18 + row * 18

            val book = ItemStack(Items.ENCHANTED_BOOK)
            ItemEnchantedBook.addEnchantment(book, EnchantmentData(ench, 1))

            itemRender.renderItemAndEffectIntoGUI(book, x, y)
            itemRender.renderItemOverlayIntoGUI(mc.fontRenderer, book, x, y, null)

            if (mouseX in x until x + 16 && mouseY in y until y + 16) hoveredEnch = ench
        }

        val backX = guiLeft + 8
        val backY = guiTop + 18 + listRows * 18
        itemRender.renderItemAndEffectIntoGUI(ItemStack(Items.ARROW), backX, backY)
        if (mouseX in backX until backX + 16 && mouseY in backY until backY + 16) {
            drawHoveringText(
                listOf(TextFormatting.YELLOW.toString() + I18n.format("gui.simple_tweaks.enchant_info.back")),
                mouseX, mouseY
            )
        }

        if (maxPage > 0) {
            val pageText = "${page + 1} / ${maxPage + 1}"
            drawString(
                mc.fontRenderer, pageText,
                guiLeft + listWidth - 8 - mc.fontRenderer.getStringWidth(pageText),
                guiTop + 6, 0xCCCCCC
            )
        }

        if (hoveredEnch != null) {
            drawHoveringText(buildTooltip(hoveredEnch), mouseX, mouseY)
        }
    }

    private fun buildTooltip(ench: Enchantment): List<String> {
        val lines = mutableListOf<String>()
        lines.add(ench.getTranslatedName(1))

        val mod = ench as? ModEnchantments
        if (mod != null) {
            lines.add(
                mod.textColor.toString() + I18n.format(
                    "gui.simple_tweaks.enchant_info.category",
                    I18n.format("gui.simple_tweaks.category.${mod.category.name.lowercase()}")
                )
            )
            lines.add(
                TextFormatting.AQUA.toString() + I18n.format(
                    "gui.simple_tweaks.enchant_info.applies_to",
                    appliesToText(mod.modType)
                )
            )
        }

        lines.add(
            TextFormatting.GRAY.toString() + I18n.format(
                "gui.simple_tweaks.enchant_info.max_level",
                ench.maxLevel
            )
        )
        lines.add("")

        val descKey = "${ench.name}.desc"
        val desc = I18n.format(descKey)
        if (desc != descKey) {
            for (line in wrapText(desc, 200)) {
                lines.add(TextFormatting.WHITE.toString() + line)
            }
        }
        return lines
    }
    private fun appliesToText(modType: ModEnchantmentType): String {
        val key = when (modType) {
            ModEnchantmentType.SWORD -> "sword"
            ModEnchantmentType.WEAPON -> "sword_bow"
            ModEnchantmentType.BOW -> "bow"
            ModEnchantmentType.ARMOR -> "armor"
            ModEnchantmentType.HELMET -> "helmet"
            ModEnchantmentType.CHESTPLATE -> "chestplate"
            ModEnchantmentType.LEGGINGS -> "leggings"
            ModEnchantmentType.BOOTS -> "boots"
            ModEnchantmentType.BREAKABLE -> "breakable"
            ModEnchantmentType.TOOL -> "tool"
            ModEnchantmentType.HELD -> "held"
        }
        return I18n.format("gui.simple_tweaks.enchant_info.applies_to.$key")
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val fr = mc.fontRenderer
        val words = text.split(" ")
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (w in words) {
            val test = if (sb.isEmpty()) w else "$sb $w"
            if (fr.getStringWidth(test) > maxWidth && sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
                sb.append(w)
            } else {
                sb.setLength(0)
                sb.append(test)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return

        if (selected == null) {
            CATEGORIES.forEachIndexed { idx, cat ->
                val col = idx % mainCols
                val row = idx / mainCols
                val x = guiLeft + 8 + 18 + col * 18 + 18
                val y = guiTop + 18 + row * 18 + 18
                if (mouseX in x until x + 16 && mouseY in y until y + 16) {
                    selected = cat
                    page = 0
                    initGui()
                    return
                }
            }
        } else {
            val backX = guiLeft + 8
            val backY = guiTop + 18 + listRows * 18
            if (mouseX in backX until backX + 16 && mouseY in backY until backY + 16) {
                selected = null
                initGui()
                return
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        if (selected == null) return
        val dWheel = Mouse.getDWheel()
        if (dWheel == 0) return

        val maxPage = ((enchantsOf(selected!!).size - 1).coerceAtLeast(0)) / perPage
        if (dWheel < 0 && page < maxPage) page++
        else if (dWheel > 0 && page > 0) page--
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == 1 || mc.gameSettings.keyBindInventory.keyCode == keyCode) {
            if (selected != null) {
                selected = null
                initGui()
                return
            }
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun doesGuiPauseGame() = false

    private fun enchantsOf(cat: EnchantmentCategories): List<Enchantment> {
        val list = mutableListOf<Enchantment>()
        for (ench in Enchantment.REGISTRY) {
            val mod = ench as? ModEnchantments ?: continue
            if (mod.category == cat) list.add(ench)
        }
        return list.sortedBy { it.name }
    }

    private fun countEnchants(cat: EnchantmentCategories): Int = enchantsOf(cat).size
}