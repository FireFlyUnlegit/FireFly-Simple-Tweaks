package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.core.config.GeneralConfig
import dev.firefly.simpletweaks.core.config.SimpleTweaksConfig
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * In-game config screen, opened by the client command `/stconfig`.
 *
 * <h2>Why this shape</h2>
 * 1.12.2's config GUI was a 673-line screen built on a 229-line `Module`/`Configurable`/`Value`
 * framework, and phase-6 batch 5 (replicating it) was **cut** as poor value. This screen is a
 * purpose-built alternative: a plain vanilla [Screen] driven directly off the two config objects,
 * with **no** framework and **no** new dependency (Cloth Config / ModMenu were considered and are not
 * needed for something this small).
 *
 * <p>It is **client-only**, and it opens from a **client command**, so it never has to send anything
 * over the network — unlike 1.12.2, which had to register a `IGuiHandler` on both sides.
 *
 * <h2>Layout</h2>
 * Options are split across three pages rather than put in a scrolling list, because the whole live
 * option set is 19 entries and a fixed three-page layout fits any GUI scale without a custom list
 * widget. Rows are 20 px so a 9-row page plus header and footer stays under a 240 px logical height,
 * which is the smallest the game realistically uses.
 *
 * <h2>Labels</h2>
 * Every label is a translation key looked up in `gui.simple_tweaks.config.*`. Those keys are **not** in
 * the 1.12.2 `.lang` files (the config screen only exists in this port), so they are hand-written in
 * `assets/simple_tweaks/lang/{en_us,zh_cn}.json` alongside every enchantment name and description. Those
 * two files are the source of truth — no generator writes them.
 *
 * <h2>Persistence</h2>
 * Every widget writes through [SimpleTweaksConfig.save] immediately, so a change survives a crash and
 * closing the screen needs no bookkeeping. `clearAndInit()` is then called to rebuild the widgets so
 * the labels and values reflect the new state — cheap, and it removes any chance of a stale label.
 */
class SimpleTweaksConfigScreen(private val parent: Screen?) :
    Screen(Text.literal("FireFly's Simple Tweaks")) {

    private companion object {
        const val ROW_H = 20
        const val ROW_W = 220
        const val TOP = 40
        const val PAGES = 3
        const val KEY_PREFIX = "gui.simple_tweaks.config."

        fun key(name: String): Text = Text.translatable(KEY_PREFIX + name)
    }

    private var page = 0

    /** Numbers have no widget for their label/value, so they are drawn in [render]. */
    private data class NumRow(val label: Text, val value: Text, val y: Int)

    private val numRows = mutableListOf<NumRow>()

    override fun init() {
        numRows.clear()
        val x0 = this.width / 2 - ROW_W / 2
        var y = TOP

        fun boolRow(name: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            val state = Text.translatable(KEY_PREFIX + if (get()) "on" else "off")
            val label = Text.translatable(
                "${KEY_PREFIX}toggle", Text.translatable(KEY_PREFIX + name), state,
            )
            addDrawableChild(
                ButtonWidget.builder(label) {
                    set(!get())
                    SimpleTweaksConfig.save()
                    clearAndInit()
                }.dimensions(x0, y, ROW_W, ROW_H - 2).build()
            )
            y += ROW_H
        }

        fun numRow(
            name: String,
            get: () -> Double,
            set: (Double) -> Unit,
            step: Double,
            min: Double,
            max: Double,
            fmt: (Double) -> Text,
        ) {
            val minusX = x0 + ROW_W - 40
            addDrawableChild(
                ButtonWidget.builder(Text.literal("§c-§r")) {
                    set((get() - step).coerceIn(min, max))
                    SimpleTweaksConfig.save()
                    clearAndInit()
                }.dimensions(minusX, y, 18, ROW_H - 2).build()
            )
            addDrawableChild(
                ButtonWidget.builder(Text.literal("§a+§r")) {
                    set((get() + step).coerceIn(min, max))
                    SimpleTweaksConfig.save()
                    clearAndInit()
                }.dimensions(minusX + 20, y, 18, ROW_H - 2).build()
            )
            numRows.add(NumRow(key(name), fmt(get()), y))
            y += ROW_H
        }

        when (page) {
            0 -> {
                boolRow("cancelVanillaDamageIndicator", { GeneralConfig.cancelVanillaDamageIndicator }) {
                    GeneralConfig.cancelVanillaDamageIndicator = it
                }
                boolRow("disableAnvilCostLimit", { GeneralConfig.disableAnvilCostLimit }) {
                    GeneralConfig.disableAnvilCostLimit = it
                }
                numRow(
                    "maxAnvilCost", { GeneralConfig.maxAnvilCost.toDouble() },
                    { GeneralConfig.maxAnvilCost = it.toInt() },
                    step = 1000.0, min = 1.0, max = Int.MAX_VALUE.toDouble(),
                    fmt = {
                        if (it >= Int.MAX_VALUE.toDouble()) key("unlimited")
                        else Text.literal(it.toInt().toString())
                    },
                )
                boolRow("disableEnchantmentTableLimit", { GeneralConfig.disableEnchantmentTableLimit }) {
                    GeneralConfig.disableEnchantmentTableLimit = it
                }
                numRow(
                    "maxEnchantmentPower", { GeneralConfig.maxEnchantmentPower.toDouble() },
                    { GeneralConfig.maxEnchantmentPower = it.toInt() },
                    step = 1.0, min = 1.0, max = 255.0,
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                boolRow("enabledEnchantmentColor", { GeneralConfig.enabledEnchantmentColor }) {
                    GeneralConfig.enabledEnchantmentColor = it
                }
                boolRow("enabledSpecialParticles", { GeneralConfig.enabledSpecialParticles }) {
                    GeneralConfig.enabledSpecialParticles = it
                }
            }

            1 -> {
                boolRow("damageIndicator.enabled", { DamageIndicatorConfig.enabled }) {
                    DamageIndicatorConfig.enabled = it
                }
                boolRow("damageIndicator.symbol", { DamageIndicatorConfig.symbol }) {
                    DamageIndicatorConfig.symbol = it
                }
                boolRow("damageIndicator.percentageMode", { DamageIndicatorConfig.percentageMode }) {
                    DamageIndicatorConfig.percentageMode = it
                }
                boolRow("damageIndicator.showShadow", { DamageIndicatorConfig.showShadow }) {
                    DamageIndicatorConfig.showShadow = it
                }
                numRow(
                    "damageIndicator.duration", { DamageIndicatorConfig.duration.toDouble() },
                    { DamageIndicatorConfig.duration = it.toInt() },
                    step = 10.0, min = 10.0, max = 500.0,
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "damageIndicator.riseDuration", { DamageIndicatorConfig.riseDuration.toDouble() },
                    { DamageIndicatorConfig.riseDuration = it.toInt() },
                    step = 1.0, min = 5.0, max = 50.0,
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "damageIndicator.maxDistance", { DamageIndicatorConfig.maxDistance.toDouble() },
                    { DamageIndicatorConfig.maxDistance = it.toInt() },
                    step = 8.0, min = 16.0, max = 256.0,
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "damageIndicator.scale", { DamageIndicatorConfig.scale },
                    { DamageIndicatorConfig.scale = it },
                    step = 0.1, min = 0.5, max = 10.0,
                    fmt = { Text.literal("%.1f".format(it)) },
                )
                numRow(
                    "damageIndicator.yStartFactor", { DamageIndicatorConfig.yStartFactor },
                    { DamageIndicatorConfig.yStartFactor = it },
                    step = 0.1, min = 0.5, max = 2.0,
                    fmt = { Text.literal("%.1f".format(it)) },
                )
            }

            else -> {
                // Page 3: the AutoSprint module. On its own page so every page stays at 9 rows or
                // fewer, which is what keeps the screen inside a 240 px logical height.
                boolRow("autoSprintEnabled", { GeneralConfig.autoSprintEnabled }) {
                    GeneralConfig.autoSprintEnabled = it
                }
                boolRow("autoSprintOmniSprint", { GeneralConfig.autoSprintOmniSprint }) {
                    GeneralConfig.autoSprintOmniSprint = it
                }
            }
        }

        val bottom = this.height - 28
        addDrawableChild(
            ButtonWidget.builder(key("next_page")) {
                page = (page + 1) % PAGES
                clearAndInit()
            }.dimensions(this.width / 2 + 4, bottom, 106, ROW_H).build()
        )
        addDrawableChild(
            ButtonWidget.builder(key("done")) { close() }
                .dimensions(this.width / 2 - 110, bottom, 106, ROW_H).build()
        )
    }

    private fun sectionName(): String = when (page) {
        0 -> "section.general"
        1 -> "section.damage"
        else -> "section.movement"
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFF)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("${KEY_PREFIX}page", key(sectionName()), page + 1, PAGES),
            this.width / 2, 28, 0xA0A0A0,
        )

        // Numeric rows: label on the left, value right-aligned just before the [-] button.
        val valueRight = this.width / 2 + ROW_W / 2 - 46
        for (row in numRows) {
            context.drawTextWithShadow(this.textRenderer, row.label, valueRight - 108, row.y + 5, 0xFFFFFF)
            context.drawTextWithShadow(
                this.textRenderer, row.value,
                valueRight - this.textRenderer.getWidth(row.value), row.y + 5, 0xFFFF55,
            )
        }

        context.drawCenteredTextWithShadow(
            this.textRenderer, key("save_hint"), this.width / 2, this.height - 40, 0x707070,
        )
    }

    override fun close() {
        // Nothing to flush: every widget already saved. Just restore the previous screen.
        this.client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
