package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.core.config.GeneralConfig
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/** Shared rules for the pre-1.9 sword block: which items can block, and how much they stop. */
object SwordBlocking {

    const val BLOCK_USE_TIME = 72000
    const val BASE_DAMAGE_REDUCTION = 0.5f
    const val ADVANCED_DAMAGE_REDUCTION_PER_LEVEL = 0.08f
    const val ADVANCED_KNOCKBACK_REDUCTION_PER_LEVEL = 0.18f
    const val MAX_REDUCTION = 0.9f

    private val SWORD_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("enchantable/sword"))

    @JvmStatic
    fun isBlockingSword(stack: ItemStack): Boolean {
        if (!GeneralConfig.enableSwordBlocking) return false
        if (stack.isEmpty || !stack.isIn(SWORD_TAG)) return false
        return getItemSpecificEnchantLevel(stack, GeneratedEnchantments.BLOCKING) > 0 ||
            getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ADVANCED_BLOCKING) > 0
    }

    /** Instant check, unlike [LivingEntity.isBlocking]'s five-tick wind-up. */
    @JvmStatic
    fun isBlocking(player: PlayerEntity): Boolean =
        player.isUsingItem() && isBlockingSword(player.activeItem)

    /** Uses the active-hand stack, which is visible on remote clients; [activeItem] is not synced. */
    @JvmStatic
    fun isBlockingVisual(player: PlayerEntity): Boolean =
        player.isUsingItem() && isBlockingSword(player.getStackInHand(player.getActiveHand()))

    @JvmStatic
    fun advancedLevel(stack: ItemStack): Int =
        getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ADVANCED_BLOCKING)

    @JvmStatic
    fun damageReduction(player: PlayerEntity): Float {
        if (!isBlocking(player)) return 0f
        val level = advancedLevel(player.activeItem)
        val reduction = BASE_DAMAGE_REDUCTION + ADVANCED_DAMAGE_REDUCTION_PER_LEVEL * level
        return reduction.coerceIn(0f, MAX_REDUCTION)
    }

    @JvmStatic
    fun knockbackMultiplier(player: PlayerEntity): Float {
        if (!isBlocking(player)) return 1f
        val level = advancedLevel(player.activeItem)
        if (level <= 0) return 1f
        return (1f - ADVANCED_KNOCKBACK_REDUCTION_PER_LEVEL * level).coerceIn(0f, 1f)
    }
}
