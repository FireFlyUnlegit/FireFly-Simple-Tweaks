package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.core.config.GeneralConfig
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import dev.firefly.simpletweaks.core.config.ConfigBounds
import dev.firefly.simpletweaks.core.config.SimpleTweaksConfig
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting

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
 * Options are split across pages rather than put in a scrolling list, because the whole live option
 * set is small (20 entries as of `build=cleanup26`) and a fixed page layout fits any GUI scale without
 * a custom list widget. Rows are 20 px so a 9-row page plus header and footer stays under a 240 px
 * logical height, which is the smallest the game realistically uses — **that is why the damage
 * indicator got a second page when `minHealthChange` was added instead of a 10th row**.
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
        const val ROW_W = 260
        const val LABEL_W = 150
        const val FIELD_W = 90
        /** Height of the page-tab strip under the title. */
        const val TABS_H = 18
        const val TOP = 48
        const val PAGES = 5
        const val KEY_PREFIX = "gui.simple_tweaks.config."

        fun key(name: String): Text = Text.translatable(KEY_PREFIX + name)
    }

    private var page = 0

    /**
     * A row's label, drawn by [render]; the control is a widget placed on the same grid.
     *
     * Toggles and numbers share this so both columns line up. Before `build=cleanup45` a toggle was a
     * full-width button with its label inside, while a number was a text field — so the two never shared a
     * column, and a long toggle label pushed the control off the right edge.
     */
    private data class LabelRow(val label: Text, val y: Int)

    private val rows = mutableListOf<LabelRow>()

    override fun init() {
        rows.clear()
        val x0 = this.width / 2 - ROW_W / 2
        var y = TOP

        fun boolRow(name: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            val on = get()
            val state = Text.translatable(KEY_PREFIX + if (on) "on" else "off")
                .formatted(if (on) Formatting.GREEN else Formatting.DARK_GRAY)
            addDrawableChild(
                ButtonWidget.builder(state) {
                    set(!get())
                    SimpleTweaksConfig.save()
                    clearAndInit()
                }.dimensions(x0 + ROW_W - FIELD_W, y + 1, FIELD_W, ROW_H - 6).build()
            )
            rows.add(LabelRow(Text.translatable(KEY_PREFIX + name), y))
            y += ROW_H
        }

        /**
         * A numeric option as an **editable text field**.
         *
         * ## Why not the old [-] / [+] pair
         * Clicking was the only way to change a value, so every ceiling had to be reachable by clicking —
         * which is why the old ranges stopped at 500 ticks and a scale of 10. Typing removes that link: the
         * range is now whatever [ConfigBounds] says, and 1500 is two keystrokes instead of 149 clicks.
         *
         * ## Behaviour
         * - Applies live, clamped to `min..max`, and saves on every accepted keystroke. A partial entry like
         *   `-` or `.` parses to null and is neither applied nor rewritten, so the cursor never jumps.
         * - Text is rebuilt from [raw] whenever the widgets are recreated (page switch, screen open), which
         *   makes a clamped value visible as the value that was actually stored.
         *
         * `step` stays in the signature so the call sites did not need rewriting; keyboard entry has no step.
         */
        fun numRow(
            name: String,
            get: () -> Double,
            set: (Double) -> Unit,
            @Suppress("UNUSED_PARAMETER") step: Double,
            min: Double,
            max: Double,
            fmt: (Double) -> Text,
            raw: (Double) -> String = { value ->
                if (value == value.toInt().toDouble()) value.toInt().toString()
                else "%.2f".format(value)
            },
        ) {
            val fieldW = FIELD_W
            val field = TextFieldWidget(
                this.textRenderer,
                x0 + ROW_W - fieldW,
                y + 1,
                fieldW,
                ROW_H - 6,
                key(name),
            ).apply {
                setMaxLength(12)
                text = raw(get())
                setChangedListener { input ->
                    val parsed = input.trim().replace(',', '.').toDoubleOrNull() ?: return@setChangedListener
                    if (!parsed.isFinite()) return@setChangedListener
                    set(parsed.coerceIn(min, max))
                    SimpleTweaksConfig.save()
                }
            }
            // The range used to be discoverable only by hitting the clamp, which reads as a bug. A tooltip
            // shows both ends, and it is generated from the same values the clamp uses.
            field.setTooltip(
                Tooltip.of(
                    Text.translatable(
                        KEY_PREFIX + "range",
                        raw(min),
                        raw(max),
                    ),
                ),
            )
            addDrawableChild(field)
            rows.add(LabelRow(key(name), y))
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
                    step = 0.0, min = ConfigBounds.ANVIL_COST.first.toDouble(), max = ConfigBounds.ANVIL_COST.last.toDouble(),
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
                    step = 0.0, min = ConfigBounds.ENCHANT_POWER.first.toDouble(), max = ConfigBounds.ENCHANT_POWER.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                boolRow("enabledEnchantmentColor", { GeneralConfig.enabledEnchantmentColor }) {
                    GeneralConfig.enabledEnchantmentColor = it
                }
                boolRow("enabledSpecialParticles", { GeneralConfig.enabledSpecialParticles }) {
                    GeneralConfig.enabledSpecialParticles = it
                }
                boolRow("noHurtCam", { GeneralConfig.noHurtCam }) {
                    GeneralConfig.noHurtCam = it
                }
            }

            1 -> {
                boolRow("damageIndicator.enabled", { DamageIndicatorConfig.enabled }) {
                    DamageIndicatorConfig.enabled = it
                }
                // Split display sits next to the other indicator behaviour switches rather than on a
                // page of its own; page 1 goes from 6 to 7 rows, still inside the 9-row limit.
                boolRow("damageIndicator.splitElemental", { DamageIndicatorConfig.splitElemental }) {
                    DamageIndicatorConfig.splitElemental = it
                }
                numRow(
                    "damageIndicator.minHealthChange", { DamageIndicatorConfig.minHealthChange },
                    { DamageIndicatorConfig.minHealthChange = it },
                    step = 0.0, min = ConfigBounds.DAMAGE_MIN_HEALTH_CHANGE.start, max = ConfigBounds.DAMAGE_MIN_HEALTH_CHANGE.endInclusive,
                    fmt = {
                        if (it == it.toInt().toDouble()) Text.literal(it.toInt().toString())
                        else Text.literal("%.1f".format(it))
                    },
                )
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
                    step = 0.0, min = ConfigBounds.DAMAGE_DURATION.first.toDouble(), max = ConfigBounds.DAMAGE_DURATION.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
            }

            2 -> {
                // Second damage-indicator page: the layout options, split off so no page exceeds
                // 9 rows (a 10th row would overlap the footer buttons at the smallest GUI scales).
                numRow(
                    "damageIndicator.riseDuration", { DamageIndicatorConfig.riseDuration.toDouble() },
                    { DamageIndicatorConfig.riseDuration = it.toInt() },
                    step = 0.0, min = ConfigBounds.DAMAGE_RISE.first.toDouble(), max = ConfigBounds.DAMAGE_RISE.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "damageIndicator.maxDistance", { DamageIndicatorConfig.maxDistance.toDouble() },
                    { DamageIndicatorConfig.maxDistance = it.toInt() },
                    step = 0.0, min = ConfigBounds.DAMAGE_DISTANCE.first.toDouble(), max = ConfigBounds.DAMAGE_DISTANCE.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "damageIndicator.scale", { DamageIndicatorConfig.scale },
                    { DamageIndicatorConfig.scale = it },
                    step = 0.0, min = ConfigBounds.DAMAGE_SCALE.start, max = ConfigBounds.DAMAGE_SCALE.endInclusive,
                    fmt = { Text.literal("%.1f".format(it)) },
                )
                numRow(
                    "damageIndicator.yStartFactor", { DamageIndicatorConfig.yStartFactor },
                    { DamageIndicatorConfig.yStartFactor = it },
                    step = 0.0, min = ConfigBounds.DAMAGE_Y_START.start, max = ConfigBounds.DAMAGE_Y_START.endInclusive,
                    fmt = { Text.literal("%.1f".format(it)) },
                )
            }

            3 -> {
                // Mob enhancement. Its own page so the four existing pages keep their row counts (the
                // 9-row limit is what keeps the screen inside a 240 px logical height).
                boolRow("mobEnhancement.enabled", { MobEnhancementConfig.enabled }) {
                    MobEnhancementConfig.enabled = it
                }
                boolRow("mobEnhancement.enchantmentsEnabled", { MobEnhancementConfig.enchantmentsEnabled }) {
                    MobEnhancementConfig.enchantmentsEnabled = it
                }
                boolRow("mobEnhancement.levelsEnabled", { MobEnhancementConfig.levelsEnabled }) {
                    MobEnhancementConfig.levelsEnabled = it
                }
                boolRow("mobEnhancement.elementsEnabled", { MobEnhancementConfig.elementsEnabled }) {
                    MobEnhancementConfig.elementsEnabled = it
                }
                numRow(
                    "mobEnhancement.levelUpOnHitPlayer",
                    { MobEnhancementConfig.levelUpOnHitPlayer.toDouble() },
                    { MobEnhancementConfig.levelUpOnHitPlayer = it.toInt() },
                    step = 1.0,
                    min = ConfigBounds.LEVEL_UP_PERCENT.first.toDouble(),
                    max = ConfigBounds.LEVEL_UP_PERCENT.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "mobEnhancement.levelUpOnKill",
                    { MobEnhancementConfig.levelUpOnKill.toDouble() },
                    { MobEnhancementConfig.levelUpOnKill = it.toInt() },
                    step = 1.0,
                    min = ConfigBounds.LEVEL_UP_PERCENT.first.toDouble(),
                    max = ConfigBounds.LEVEL_UP_PERCENT.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
                numRow(
                    "mobEnhancement.levelUpWhenHurt",
                    { MobEnhancementConfig.levelUpWhenHurt.toDouble() },
                    { MobEnhancementConfig.levelUpWhenHurt = it.toInt() },
                    step = 1.0,
                    min = ConfigBounds.LEVEL_UP_PERCENT.first.toDouble(),
                    max = ConfigBounds.LEVEL_UP_PERCENT.last.toDouble(),
                    fmt = { Text.literal(it.toInt().toString()) },
                )
            }

            else -> {
                // Page 5: the AutoSprint module. On its own page so every page stays at 9 rows or
                // fewer, which is what keeps the screen inside a 240 px logical height.
                boolRow("autoSprintEnabled", { GeneralConfig.autoSprintEnabled }) {
                    GeneralConfig.autoSprintEnabled = it
                }
                boolRow("autoSprintOmniSprint", { GeneralConfig.autoSprintOmniSprint }) {
                    GeneralConfig.autoSprintOmniSprint = it
                }
            }
        }

        // Page tabs: one click per page instead of cycling with "next page".
        val tabW = 26
        val tabsX = this.width / 2 - (PAGES * tabW + (PAGES - 1) * 2) / 2
        for (index in 0 until PAGES) {
            val selected = index == page
            addDrawableChild(
                ButtonWidget.builder(Text.literal((index + 1).toString())) {
                    page = index
                    clearAndInit()
                }.dimensions(tabsX + index * (tabW + 2), 30, tabW, TABS_H - 4).build()
            )
            if (selected) selectedTab = index
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

    /** Last-built tab index, so [render] can mark it without re-deriving it from widgets. */
    private var selectedTab = 0

    /** Greedy truncation with an ellipsis, so a long label can never overlap the control on its right. */
    private fun truncate(text: Text, maxWidth: Int): Text {
        if (this.textRenderer.getWidth(text) <= maxWidth) return text
        val ellipsis = "..."
        val limit = maxWidth - this.textRenderer.getWidth(ellipsis)
        val builder = StringBuilder()
        for (ch in text.string) {
            if (this.textRenderer.getWidth(builder.toString() + ch) > limit) break
            builder.append(ch)
        }
        return Text.literal(builder.toString() + ellipsis)
    }

    private fun sectionName(): String = when (page) {
        0 -> "section.general"
        // Pages 1 and 2 are both the damage indicator (`build=cleanup26` split it in two); the page
        // indicator in the header carries "N/5", so sharing the section title is unambiguous.
        1, 2 -> "section.damage"
        3 -> "section.mob"
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

        // Selected page tab gets an underline, so "which page am I on" does not rely on the small "N/5" text.
        run {
            val tabW = 26
            val tabsX = this.width / 2 - (PAGES * tabW + (PAGES - 1) * 2) / 2
            val x = tabsX + selectedTab * (tabW + 2)
            context.fill(x, 30 + TABS_H - 4, x + tabW, 30 + TABS_H - 2, 0xFFFFFFFF.toInt())
        }

        // Rows: a panel behind them, alternating stripes for readability, a highlight on the hovered row,
        // then the label (truncated so it can never run into the control on the right).
        val left = this.width / 2 - ROW_W / 2
        val listTop = TOP - 6
        val listBottom = rows.lastOrNull()?.let { it.y + ROW_H } ?: listTop
        context.fill(left - 8, listTop, left + ROW_W + 8, listBottom, 0x66000000.toInt())
        context.fill(left - 8, listTop, left - 7, listBottom, 0x40FFFFFF)
        context.fill(left + ROW_W + 7, listTop, left + ROW_W + 8, listBottom, 0x40FFFFFF)

        val hoveredY = rows.firstOrNull { mouseY >= it.y && mouseY < it.y + ROW_H }?.y
        for (row in rows) {
            if (row.y == hoveredY) {
                context.fill(left - 8, row.y, left + ROW_W + 8, row.y + ROW_H - 1, 0x30FFFFFF)
            }
            context.drawTextWithShadow(
                this.textRenderer,
                truncate(row.label, LABEL_W),
                left,
                row.y + 5,
                0xFFFFFF,
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
