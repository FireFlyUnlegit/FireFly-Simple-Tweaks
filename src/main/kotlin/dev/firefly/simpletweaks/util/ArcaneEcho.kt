package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import java.util.WeakHashMap

/**
 * `arcane_echo`: bonuses derived from the **total enchantment levels the player is wearing**.
 *
 * Two rules define the number, and both are the author's:
 * - the driving number is the sum of every enchantment level in the six visible slots, excluding
 *   `arcane_echo` itself (its own levels must not pay for themselves);
 * - the **effective level is the lowest** `arcane_echo` level among the pieces that carry it (chest I +
 *   sword II → level I), so a single high-level piece does not carry the enchantment alone.
 */
object ArcaneEcho {

    /** The six visible slots: hands plus armour. */
    private val SLOTS = arrayOf(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    // Per enchantment level × effective level. Only the two terms that change how the game *feels*
    // (attack rhythm, walking speed) carry a ceiling; everything else is allowed to grow.
    private const val HEALTH_PER_UNIT = 0.12
    private const val ARMOR_PER_UNIT = 0.08
    private const val TOUGHNESS_PER_UNIT = 0.03
    private const val ATTACK_PER_UNIT = 0.04
    private const val ATTACK_SPEED_PER_UNIT = 0.0012
    private const val MOVE_SPEED_PER_UNIT = 0.0008
    private const val KNOCKBACK_PER_UNIT = 0.0015
    private const val LUCK_PER_UNIT = 0.015
    private const val DAMAGE_REDUCTION_PER_UNIT = 0.05
    private const val ELEMENT_PER_UNIT = 0.0025
    private const val REACTION_PER_UNIT = 0.0035

    // Multipliers rather than flat numbers: final damage scales what is dealt, final reduction divides
    // what is taken (so it approaches immunity without ever reaching it and needs no ceiling).
    private const val FINAL_DAMAGE_PER_UNIT = 0.004
    private const val FINAL_REDUCTION_PER_UNIT = 0.003

    private const val MOVE_SPEED_CAP = 0.20

    private val HEALTH_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_health")
    private val ARMOR_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_armor")
    private val TOUGHNESS_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_toughness")
    private val ATTACK_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_attack")
    private val ATTACK_SPEED_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_attack_speed")
    private val MOVE_SPEED_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_move_speed")
    private val KNOCKBACK_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_knockback")
    private val LUCK_ID = Identifier.of(SimpleTweaks.MOD_ID, "arcane_echo_luck")

    data class Stats(
        val units: Int,
        val level: Int,
        val health: Double,
        val armor: Double,
        val toughness: Double,
        val attack: Double,
        val attackSpeed: Double,
        val moveSpeed: Double,
        val knockback: Double,
        val luck: Double,
        val damageReduction: Double,
        val element: Double,
        val reaction: Double,
        val finalDamage: Double,
        val finalReduction: Double,
    ) {
        val active: Boolean get() = level > 0
    }

    /** Last computed stats per player; refreshed by [refresh] once every 20 ticks. */
    private val CACHE = WeakHashMap<PlayerEntity, Stats>()

    @JvmStatic
    fun statsOf(player: PlayerEntity): Stats = CACHE[player] ?: compute(player).also { CACHE[player] = it }

    @JvmStatic
    fun statsOf(units: Int, level: Int): Stats {
        val scale = units.toDouble() * level
        return Stats(
            units = units,
            level = level,
            health = HEALTH_PER_UNIT * scale,
            armor = ARMOR_PER_UNIT * scale,
            toughness = TOUGHNESS_PER_UNIT * scale,
            attack = ATTACK_PER_UNIT * scale,
            attackSpeed = (ATTACK_SPEED_PER_UNIT * scale),
            moveSpeed = (MOVE_SPEED_PER_UNIT * scale).coerceAtMost(MOVE_SPEED_CAP),
            knockback = KNOCKBACK_PER_UNIT * scale,
            luck = LUCK_PER_UNIT * scale,
            damageReduction = DAMAGE_REDUCTION_PER_UNIT * scale,
            element = ELEMENT_PER_UNIT * scale,
            reaction = REACTION_PER_UNIT * scale,
            finalDamage = FINAL_DAMAGE_PER_UNIT * scale,
            finalReduction = FINAL_REDUCTION_PER_UNIT * scale,
        )
    }

    /** Sum of enchantment levels in the six visible slots, `arcane_echo` excluded. */
    @JvmStatic
    fun unitsOf(player: PlayerEntity): Int {
        var total = 0
        for (slot in SLOTS) {
            val stack = player.getEquippedStack(slot)
            if (stack.isEmpty) continue
            val component = stack.get(DataComponentTypes.ENCHANTMENTS) ?: continue
            for (entry in component.enchantments) {
                if (entry.matchesKey(GeneratedEnchantments.ARCANE_ECHO)) continue
                total += component.getLevel(entry)
            }
        }
        return total
    }

    /** Lowest `arcane_echo` level among the worn pieces that carry it; `0` when none does. */
    @JvmStatic
    fun levelOf(player: PlayerEntity): Int {
        var lowest = 0
        for (slot in SLOTS) {
            val level = getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.ARCANE_ECHO)
            if (level <= 0) continue
            lowest = if (lowest == 0) level else minOf(lowest, level)
        }
        return lowest
    }

    private fun compute(player: PlayerEntity): Stats = statsOf(unitsOf(player), levelOf(player))

    /**
     * Recomputes [player]'s stats and pushes the attribute part onto the entity.
     *
     * Persistent modifiers on purpose: vanilla reads the `attributes` tag of a player **before** the
     * `Health` tag (`LivingEntity#readCustomDataFromNbt`), and reading health clamps it to the max health
     * it sees at that moment. Only modifiers saved with the entity exist that early — a temporary one
     * would leave the player at 20 max health while loading, and vanilla would clamp a 40 hp save down to
     * 20 every single time they re-join. The stale-value risk is covered by [refresh] running every 20
     * ticks and by [clear] on clone.
     */
    fun refresh(player: PlayerEntity) {
        val stats = compute(player)
        CACHE[player] = stats

        var changed = apply(player, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID, stats.health, EntityAttributeModifier.Operation.ADD_VALUE)
        changed = apply(player, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, stats.armor, EntityAttributeModifier.Operation.ADD_VALUE) || changed
        changed = apply(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, TOUGHNESS_ID, stats.toughness, EntityAttributeModifier.Operation.ADD_VALUE) || changed
        changed = apply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, ATTACK_ID, stats.attack, EntityAttributeModifier.Operation.ADD_VALUE) || changed
        changed = apply(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED_ID, stats.attackSpeed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE) || changed
        changed = apply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOVE_SPEED_ID, stats.moveSpeed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE) || changed
        changed = apply(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, stats.knockback, EntityAttributeModifier.Operation.ADD_VALUE) || changed
        changed = apply(player, EntityAttributes.GENERIC_LUCK, LUCK_ID, stats.luck, EntityAttributeModifier.Operation.ADD_VALUE) || changed

        if (!changed) return
        player.syncAttributes()
        if (player.health > player.maxHealth) player.health = player.maxHealth
        FSTLog.log("ArcaneEcho") {
            "player=${player.name.string}, level=${stats.level}, units=${stats.units}, " +
                "health=${"%.2f".format(stats.health)}, armor=${"%.2f".format(stats.armor)}, " +
                "attack=${"%.2f".format(stats.attack)}, outcome=applied"
        }
    }

    fun clear(player: PlayerEntity) {
        CACHE.remove(player)
        remove(player, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID)
        remove(player, EntityAttributes.GENERIC_ARMOR, ARMOR_ID)
        remove(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, TOUGHNESS_ID)
        remove(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, ATTACK_ID)
        remove(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED_ID)
        remove(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOVE_SPEED_ID)
        remove(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID)
        remove(player, EntityAttributes.GENERIC_LUCK, LUCK_ID)
    }

    private fun apply(
        player: PlayerEntity,
        attribute: RegistryEntry<EntityAttribute>,
        id: Identifier,
        value: Double,
        operation: EntityAttributeModifier.Operation,
    ): Boolean {
        val instance = player.getAttributeInstance(attribute) ?: return false
        val existing = instance.getModifier(id)
        if (value <= 0.0) {
            if (existing == null) return false
            instance.removeModifier(id)
            return true
        }
        if (existing != null && existing.value == value && existing.operation == operation) return false
        instance.removeModifier(id)
        instance.addPersistentModifier(EntityAttributeModifier(id, value, operation))
        return true
    }

    /** Drops the cached numbers only; the modifiers stay so they are written to the player file. */
    fun clearCache(player: PlayerEntity) {
        CACHE.remove(player)
    }

    private fun remove(player: PlayerEntity, attribute: RegistryEntry<EntityAttribute>, id: Identifier) {
        player.getAttributeInstance(attribute)?.removeModifier(id)
    }
}
