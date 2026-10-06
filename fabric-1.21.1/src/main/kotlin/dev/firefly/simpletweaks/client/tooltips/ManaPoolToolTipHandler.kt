package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

/**
 * Adds a `ManaPool: N` line to the tooltip of an item carrying `celestial_blessing`.
 *
 * Port of 1.12.2 `client/tooltips/ManaPoolToolTipHandler.kt` (33 lines).
 *
 * <h2>What changed</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | Forge `ItemTooltipEvent` | Fabric `ItemTooltipCallback` |
 * | `event.itemStack` / `event.toolTip` | the callback's `stack` / `lines` |
 * | `event.entityPlayer` | `MinecraftClient#player` — the callback has no player parameter, and it only fires client-side anyway |
 * | `"§bManaPool: §f$display"` | two styled [Text] parts joined with `append` |
 * | `player.uniqueID` | `player.uuid` |
 *
 * <p><b>The `§` codes had to become styles.</b> 1.12.2 built one string containing legacy colour codes
 * and the font renderer parsed them. In 1.21 a `Text.literal` containing `§b` renders those characters
 * **verbatim** — the codes are not parsed — so the original line would have shown a literal `§b`. The
 * port therefore builds `AQUA "ManaPool: "` + `WHITE display`, which is what the legacy codes meant.
 *
 * <p>1.12.2 also guarded with `if (!player.world.isRemote) return`. That check is dropped: Fabric's
 * callback is client-only, so it can never be false here, and keeping it would be dead code that reads
 * like a safety check.
 */
object ManaPoolToolTipHandler {

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register

            val player = MinecraftClient.getInstance().player ?: return@register

            val lvl = getItemSpecificEnchantLevel(stack, ModEnchantmentKeys.CELESTIAL_BLESSING)
            if (lvl <= 0) return@register

            val pool = ClientManaPoolCache.get(player.uuid)
            val display = if (pool % 1f == 0f) {
                pool.roundToInt().toString()
            } else {
                "%.2f".format(pool)
            }

            lines.add(
                Text.literal("ManaPool: ").formatted(Formatting.AQUA)
                    .append(Text.literal(display).formatted(Formatting.WHITE))
            )
        }
    }
}
