package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.core.config.AttributeCapConfig
import dev.firefly.simpletweaks.core.config.SimpleTweaksConfig
import dev.firefly.simpletweaks.util.AttributeCaps
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Min/max editor for every registered attribute.
 *
 * A row is `name | min | max | ∞ | ↺`: the fields are plain numbers (`null`/empty = keep vanilla), ∞ sets
 * only the maximum to [Float.MAX_VALUE], ↺ drops the whole override for that attribute. Changes apply on
 * the keystroke — the clamp reads the config directly, so no restart is involved.
 */
class AttributeCapsScreen(private val parent: Screen?) : Screen(Text.translatable(TITLE)) {

    private companion object {
        const val TITLE = "gui.simple_tweaks.attribute_caps.title"
        const val ROW_H = 20
        const val ROW_W = 300
        const val NAME_W = 128
        const val FIELD_W = 52
        const val PAGE_SIZE = 9
        const val TOP = 52
    }

    private var page = 0
    private val labels = mutableListOf<Pair<Text, Int>>()

    private val attributes: List<EntityAttribute>
        get() = Registries.ATTRIBUTE
            .sortedBy { Registries.ATTRIBUTE.getId(it)?.toString() ?: "" }
            .toList()

    private fun pages(): Int = maxOf(1, (attributes.size + PAGE_SIZE - 1) / PAGE_SIZE)

    override fun init() {
        labels.clear()
        val x0 = width / 2 - ROW_W / 2
        var y = TOP

        fun apply(attribute: EntityAttribute, min: Double?, max: Double?) {
            val id = AttributeCaps.idOf(attribute) ?: return
            AttributeCapConfig.put(id, AttributeCapConfig.Cap(min, max))
            SimpleTweaksConfig.save()
        }

        fun fieldRow(attribute: EntityAttribute) {
            val id = AttributeCaps.idOf(attribute) ?: return
            val cap = AttributeCapConfig.of(id)
            val minX = x0 + NAME_W
            val maxX = minX + FIELD_W + 4

            fun makeField(x: Int, value: Double?, commit: (Double?) -> Unit): TextFieldWidget =
                TextFieldWidget(textRenderer, x, y + 1, FIELD_W, ROW_H - 6, Text.empty()).apply {
                    setMaxLength(24)
                    text = value?.let(::raw) ?: ""
                    setChangedListener { input ->
                        val trimmed = input.trim().replace(',', '.')
                        if (trimmed.isEmpty()) {
                            commit(null)
                            return@setChangedListener
                        }
                        val parsed = trimmed.toDoubleOrNull() ?: return@setChangedListener
                        if (!parsed.isFinite()) return@setChangedListener
                        commit(parsed)
                    }
                }

            addDrawableChild(makeField(minX, cap?.min) { apply(attribute, it, cap?.max) })
            addDrawableChild(makeField(maxX, cap?.max) { apply(attribute, cap?.min, it) })

            addDrawableChild(
                ButtonWidget.builder(Text.literal("∞")) {
                    apply(attribute, cap?.min, Float.MAX_VALUE.toDouble())
                    clearAndInit()
                }.dimensions(maxX + FIELD_W + 4, y + 1, 18, ROW_H - 6).build(),
            )
            addDrawableChild(
                ButtonWidget.builder(Text.literal("↺")) {
                    apply(attribute, null, null)
                    clearAndInit()
                }.dimensions(maxX + FIELD_W + 26, y + 1, 18, ROW_H - 6).build(),
            )

            labels.add(Text.translatable(attribute.translationKey) to y)
            y += ROW_H
        }

        val list = attributes
        val start = page * PAGE_SIZE
        for (index in start until minOf(start + PAGE_SIZE, list.size)) {
            fieldRow(list[index])
        }

        // Page controls and the two bulk actions share the footer so the list keeps all nine rows.
        val footY = TOP + PAGE_SIZE * ROW_H + 6
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simple_tweaks.attribute_caps.prev")) {
                page = (page - 1 + pages()) % pages()
                clearAndInit()
            }.dimensions(x0, footY, 60, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simple_tweaks.attribute_caps.next")) {
                page = (page + 1) % pages()
                clearAndInit()
            }.dimensions(x0 + 64, footY, 60, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simple_tweaks.attribute_caps.unlimited_all")) {
                for (attribute in list) {
                    val id = AttributeCaps.idOf(attribute) ?: continue
                    val cap = AttributeCapConfig.of(id)
                    AttributeCapConfig.put(id, AttributeCapConfig.Cap(cap?.min, Float.MAX_VALUE.toDouble()))
                }
                SimpleTweaksConfig.save()
                clearAndInit()
            }.dimensions(x0 + ROW_W - 132, footY, 64, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simple_tweaks.attribute_caps.reset_all")) {
                AttributeCapConfig.clear()
                SimpleTweaksConfig.save()
                clearAndInit()
            }.dimensions(x0 + ROW_W - 64, footY, 64, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simple_tweaks.config.done")) {
                close()
            }.dimensions(width / 2 - 50, footY + 24, 100, 18).build(),
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF)
        val hint = Text.translatable("gui.simple_tweaks.attribute_caps.hint")
            .formatted(Formatting.GRAY)
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, 32, 0xA0A0A0)
        for ((label, y) in labels) {
            context.drawTextWithShadow(textRenderer, label, width / 2 - ROW_W / 2, y + 5, 0xE0E0E0)
        }
        val pageText = Text.translatable("gui.simple_tweaks.config.page", page + 1, pages())
        context.drawTextWithShadow(textRenderer, pageText, width / 2 - ROW_W / 2 + 128, TOP + PAGE_SIZE * ROW_H + 11, 0x808080)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    private fun raw(value: Double): String {
        if (value == Float.MAX_VALUE.toDouble()) return "3.4028235E38"
        val asLong = value.toLong()
        return if (asLong.toDouble() == value) asLong.toString() else value.toString()
    }
}
