package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects

@ModEnchantment(
    id = "clear_sight",
    category = EnchantCategory.RARE,
    type = EnchantType.HELMET,
    maxLevel = 1,
    weight = EnchantItemWeights.RARE,
    anvilCost = 4,
    minCostBase = 20,
    minCostPerLevel = 0,
    supportedItems = EnchantSupportedItemTags.HEAD_ARMOR,
    slots = [EnchantSlot.HEAD],
)
object EnchantClearSightHandler : Listenable {

    /**
     * **30 minutes, granted in one go.** The previous 20 s grant meant the effect had to be re-sent
     * to the client every ~10 s, and every re-send makes the client re-evaluate its lighting. One
     * long grant turns that into a single event per half hour.
     *
     * Night vision with a large duration is harmless: it is a rendering flag, not a timer the client
     * acts on differently.
     */
    private const val NIGHT_VISION_DURATION = 36_000

    /**
     * Renew inside the last 10 s of that half hour. The check runs every tick, so the effect never
     * lapses -- it just costs one packet per 30 minutes instead of one every 10 seconds.
     */
    private const val RENEW_THRESHOLD = 200

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (p.world.isClient) return

        val lvl = p.getArmorEnchantLevel(GeneratedEnchantments.CLEAR_SIGHT)
        if (lvl <= 0) return

        p.removeStatusEffect(StatusEffects.BLINDNESS)
        p.removeStatusEffect(StatusEffects.DARKNESS)

        val current = p.getStatusEffect(StatusEffects.NIGHT_VISION)
        if (current == null || current.duration < RENEW_THRESHOLD) {
            p.addStatusEffect(
                StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    NIGHT_VISION_DURATION,
                    0,
                    false,
                    false,
                    false,
                )
            )
        }
    }
}