package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.ArcaneEcho
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Shows 秘术回响's live bonus block on the item's tooltip.
 *
 * The bonuses are runtime attribute modifiers, not a `minecraft:attributes` component, so vanilla renders
 * nothing for them: without this block the enchantment's numbers exist only inside the server.
 *
 * The block is computed from the **local player's** worn set, because the effective level is the lowest
 * `arcane_echo` level among the worn pieces — the hovered stack's own level only decides whether the
 * block appears at all. A stack in the inventory therefore shows what it would contribute once worn.
 */
object ArcaneEchoTooltipHandler {

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register
            val own = getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ARCANE_ECHO)
            if (own <= 0) return@register
            val player = MinecraftClient.getInstance().player ?: return@register

            val worn = ArcaneEcho.levelOf(player)
            val level = if (worn > 0) worn else own
            val stats = ArcaneEcho.statsOf(ArcaneEcho.unitsOf(player), level)

            lines.add(Text.translatable("gui.simple_tweaks.arcane_echo.header", num(stats.units.toDouble()), num(level.toDouble())).formatted(Formatting.AQUA))
            if (!stats.active) return@register

            lines.add(Text.translatable("gui.simple_tweaks.arcane_echo.vitals", num(stats.health), num(stats.armor), num(stats.toughness)).formatted(Formatting.GRAY))
            lines.add(Text.translatable("gui.simple_tweaks.arcane_echo.speed", num(stats.attack), num(stats.attackSpeed * 100), num(stats.moveSpeed * 100)).formatted(Formatting.GRAY))
            lines.add(Text.translatable("gui.simple_tweaks.arcane_echo.utility", num(stats.knockback), num(stats.luck)).formatted(Formatting.GRAY))
            lines.add(Text.translatable("gui.simple_tweaks.arcane_echo.damage", num(stats.damageReduction), num(stats.element * 100), num(stats.reaction * 100)).formatted(Formatting.GRAY))
            lines.add(Text.translatable("gui.simple_tweaks.arcane_echo.final", num(stats.finalDamage * 100), num(stats.finalReduction * 100)).formatted(Formatting.GRAY))
        }
    }

    /** `18`, `2.5` — never `18.0` / `2.4999999`. */
    private fun num(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
