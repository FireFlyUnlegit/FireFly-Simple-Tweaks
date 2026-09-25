package dev.firefly.simplemod.enchantments.handlers.rare

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.rare.EnchantItemFixer
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.random.Random.Default.nextFloat

object EnchantItemFixerHandler : Listenable {
    @SubscribeEvent
    fun onPlayerTick(e:TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        val stacks = listOf<ItemStack>(
            *p.inventory.armorInventory.toTypedArray(),
            p.heldItemMainhand
        )
        stacks.forEach {
            val level = getItemSpecificEnchantLevel(it, EnchantItemFixer)
            if (level > 0) {
                if (nextFloat() <= 0.02 * level && it.itemDamage > 0) it.itemDamage -= (1 + level / 2).coerceAtMost(it.itemDamage)
            }
        }
    }
}