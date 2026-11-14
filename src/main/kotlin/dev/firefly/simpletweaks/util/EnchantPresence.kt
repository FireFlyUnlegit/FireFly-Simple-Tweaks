package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import java.util.Collections
import java.util.WeakHashMap

/**
 * Cached "what are this entity's enchantments", per equipment slot.
 *
 * Handlers ask the same question dozens of times per hit; the answer only changes when equipment does.
 * Validated by stack identity (cheap, no tick timer); in-place enchantment edits must call [invalidate].
 */
object EnchantPresence {

    /** Slots the snapshot covers, in aggregate-index order: hands first, then armour. */
    private val SLOTS = arrayOf(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    private const val MAIN = 0
    private const val OFF = 1
    private const val ARMOR_FROM = 2

    /** Every slot, for "does this entity carry it anywhere". */
    private val ALL_SLOTS = IntArray(SLOTS.size) { it }

    /** Armour only, for the four-piece aggregates. */
    private val ARMOR_SLOTS =
        intArrayOf(ARMOR_FROM, ARMOR_FROM + 1, ARMOR_FROM + 2, ARMOR_FROM + 3)

    /** Upper bound on distinct enchantments one entity can carry; the table has 128, six slots hold far fewer. */
    private const val MAX_TRACKED = 64

    private val INDEX: Map<RegistryKey<Enchantment>, Int> by lazy {
        GeneratedEnchantments.KEYS.withIndex().associate { (index, key) -> key to index }
    }

    private val CACHE: MutableMap<LivingEntity, Profile> =
        Collections.synchronizedMap(WeakHashMap<LivingEntity, Profile>())

    /**
     * Drops [entity]'s snapshot. Call after rewriting enchantments on an equipped stack in place, which
     * no equipment event sees.
     */
    @JvmStatic
    fun invalidate(entity: LivingEntity) {
        CACHE.remove(entity)
    }

    /** Drops every snapshot, e.g. when leaving a world. */
    @JvmStatic
    fun clear() {
        CACHE.clear()
    }

    /**
     * Whether [key] is one of ours, and therefore visible in a snapshot. Callers that must also answer
     * for vanilla enchantments have to fall back to reading the stacks.
     */
    @JvmStatic
    fun tracks(key: RegistryKey<Enchantment>): Boolean = INDEX.containsKey(key)

    /** Whether [entity] carries [key] in any slot. */
    @JvmStatic
    fun has(entity: LivingEntity, key: RegistryKey<Enchantment>): Boolean =
        totalLevel(entity, key) > 0

    /** Level of [key] summed over all six slots. */
    @JvmStatic
    fun totalLevel(entity: LivingEntity, key: RegistryKey<Enchantment>): Int {
        val index = INDEX[key] ?: return 0
        return profileFor(entity, ALL_SLOTS).totalLevel(index)
    }

    /** Level of [key] on the main hand slot. */
    @JvmStatic
    fun mainHandLevelOf(entity: LivingEntity, key: RegistryKey<Enchantment>): Int {
        val index = INDEX[key] ?: return 0
        return profileFor(entity, intArrayOf(MAIN)).mainLevel(index)
    }

    /** Level of [key] on the off hand slot. */
    @JvmStatic
    fun offHandLevelOf(entity: LivingEntity, key: RegistryKey<Enchantment>): Int {
        val index = INDEX[key] ?: return 0
        return profileFor(entity, intArrayOf(OFF)).offLevel(index)
    }

    /** Level of [key] summed over the four armour slots. */
    @JvmStatic
    fun armorLevelOf(entity: LivingEntity, key: RegistryKey<Enchantment>): Int {
        val index = INDEX[key] ?: return 0
        return profileFor(entity, ARMOR_SLOTS).armorLevel(index)
    }

    /**
     * Sum of [keys] over the four armour slots, from a single snapshot.
     *
     * Same answer as calling [armorLevelOf] in a loop, but one validity check instead of N.
     */
    @JvmStatic
    fun armorSum(entity: LivingEntity, keys: Array<RegistryKey<Enchantment>>): Int {
        val profile = profileFor(entity, ARMOR_SLOTS)
        var total = 0
        for (key in keys) {
            val index = INDEX[key] ?: continue
            total += profile.armorLevel(index)
        }
        return total
    }

    /** [sum] of [keys] over all six slots, from a single snapshot. */
    @JvmStatic
    fun totalSum(entity: LivingEntity, keys: Array<RegistryKey<Enchantment>>): Int {
        val profile = profileFor(entity, ALL_SLOTS)
        var total = 0
        for (key in keys) {
            val index = INDEX[key] ?: continue
            total += profile.totalLevel(index)
        }
        return total
    }

    /** Highest single level of [key] across the four armour slots. */
    @JvmStatic
    fun armorMax(entity: LivingEntity, key: RegistryKey<Enchantment>): Int {
        val index = INDEX[key] ?: return 0
        val profile = profileFor(entity, ARMOR_SLOTS)
        var best = 0
        for (slot in ARMOR_SLOTS) {
            val level = profile.slotLevel(index, slot)
            if (level > best) best = level
        }
        return best
    }

    // ------------------------------------------------------------------ internals

    /**
     * Returns [entity]'s snapshot, rebuilding it when [slots] no longer hold the stacks it was built from.
     */
    private fun profileFor(entity: LivingEntity, slots: IntArray): Profile {
        val cached = CACHE[entity]
        if (cached != null && cached.stillMatches(entity, slots)) return cached
        val built = build(entity)
        CACHE[entity] = built
        return built
    }

    private fun build(entity: LivingEntity): Profile {
        val stacks = Array(SLOTS.size) { entity.getEquippedStack(SLOTS[it]) }
        val indices = IntArray(MAX_TRACKED)
        val all = IntArray(MAX_TRACKED)
        val main = IntArray(MAX_TRACKED)
        val off = IntArray(MAX_TRACKED)
        val armor = IntArray(MAX_TRACKED)
        val shares = Array(SLOTS.size) { IntArray(MAX_TRACKED) }
        var tracked = 0

        for (slot in SLOTS.indices) {
            val component = EnchantmentHelper.getEnchantments(stacks[slot])
            if (component.isEmpty) continue
            for (entry in component.enchantments) {
                val key = entry.key.orElse(null) ?: continue
                val index = INDEX[key] ?: continue
                val level = component.getLevel(entry)
                if (level <= 0) continue

                var position = -1
                for (existing in 0 until tracked) {
                    if (indices[existing] == index) {
                        position = existing
                        break
                    }
                }
                if (position < 0) {
                    if (tracked == MAX_TRACKED) continue
                    position = tracked++
                    indices[position] = index
                }

                all[position] += level
                shares[slot][position] += level
                when (slot) {
                    MAIN -> main[position] += level
                    OFF -> off[position] += level
                    else -> armor[position] += level
                }
            }
        }

        return Profile(
            stacks = stacks,
            indices = indices.copyOf(tracked),
            all = all.copyOf(tracked),
            main = main.copyOf(tracked),
            off = off.copyOf(tracked),
            armor = armor.copyOf(tracked),
            perSlot = Array(SLOTS.size) { shares[it].copyOf(tracked) },
        )
    }

    private class Profile(
        private val stacks: Array<ItemStack>,
        private val indices: IntArray,
        private val all: IntArray,
        private val main: IntArray,
        private val off: IntArray,
        private val armor: IntArray,
        /** Per-slot levels, indexed `[slot][position]`; only the armour rows are filled after build. */
        private val perSlot: Array<IntArray>,
    ) {

        fun totalLevel(index: Int): Int = levelOf(index, all)

        fun mainLevel(index: Int): Int = levelOf(index, main)

        fun offLevel(index: Int): Int = levelOf(index, off)

        fun armorLevel(index: Int): Int = levelOf(index, armor)

        /** Level of one armour slot; [slot] is an index into [SLOTS] (2..5). */
        fun slotLevel(index: Int, slot: Int): Int {
            for (position in indices.indices) {
                if (indices[position] == index) return perSlot[slot][position]
            }
            return 0
        }

        /** True while [slots] still hold the exact stacks this snapshot was built from. */
        fun stillMatches(entity: LivingEntity, slots: IntArray): Boolean {
            for (slot in slots) {
                if (entity.getEquippedStack(SLOTS[slot]) !== stacks[slot]) return false
            }
            return true
        }

        private fun levelOf(index: Int, levels: IntArray): Int {
            for (position in indices.indices) {
                if (indices[position] == index) return levels[position]
            }
            return 0
        }
    }
}
