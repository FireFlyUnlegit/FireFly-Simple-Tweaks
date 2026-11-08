package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 巧手 — attack speed while the weapon is held.
 *
 * ## Why +8 % per level and not the "obvious" +3 %
 * Attack speed is not linear in feel: a sword's base is `4.0`, so the swing interval is
 * `1 / 4.0 = 0.625 s`. `+3 %` per level (the first draft) moved that to `0.573 s` at III — under one
 * frame of difference at 20 tps, i.e. invisible. `+8 %` per level gives `4.0 → 4.96` at III
 * (`0.625 s → 0.504 s`), which is the first step a player actually feels, and it stays well below a
 * vanilla Haste II potion.
 *
 * ## `ADD_MULTIPLIED_BASE` on the base attribute
 * Same reasoning as `fleetfoot`: multiplying the base keeps it a stable fraction of the weapon's own
 * speed, and it does not compound with `super_knockback`-style additions or with the `heavenly_punishment`
 * attack-speed suppression (which is `ADD_VALUE` on the same attribute).
 *
 * ## The tick check compares the stored value, not a flag
 * See `EnchantVitalityHandler`'s `build=cleanup63` note: comparing against the *requested* value let a
 * derived term make the comparison never equal, so the modifier was re-created every tick.
 */
@ModEnchantment(
    id = "deft",
    category = EnchantCategory.COMMON,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    anvilCost = 4,
    minCostBase = 24,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantDeftHandler : Listenable {

    /** Multiplier added to base attack speed per level. III = +24 %. */
    const val ATTACK_SPEED_PER_LEVEL = 0.08f

    private val MODIFIER_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "deft")

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.DEFT)
        val attr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED) ?: return
        val expected = if (level <= 0) 0.0 else level * ATTACK_SPEED_PER_LEVEL.toDouble()
        val current = attr.getModifier(MODIFIER_ID)?.value

        if (expected == 0.0) {
            if (current == null) return
            attr.removeModifier(MODIFIER_ID)
            player.syncAttributes()
            FSTLog.log("Deft") { "player=${player.name.string}, lvl=0, outcome=removed" }
            return
        }
        if (current != null && current == expected) return

        attr.removeModifier(MODIFIER_ID)
        attr.addPersistentModifier(
            EntityAttributeModifier(MODIFIER_ID, expected, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        )
        player.syncAttributes()
        FSTLog.log("Deft") {
            "player=${player.name.string}, lvl=$level, modifier=$expected, outcome=applied"
        }
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        clear(e.original)
        clear(e.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) = clear(e.player)

    private fun clear(player: PlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)?.removeModifier(MODIFIER_ID)
    }
}
