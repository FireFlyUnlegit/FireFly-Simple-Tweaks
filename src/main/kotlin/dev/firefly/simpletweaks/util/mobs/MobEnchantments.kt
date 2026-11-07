package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.mob.MobEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import java.util.WeakHashMap

/**
 * Gives hostile-spawned mobs **this mod's** enchantments, and makes sure those enchantments never
 * leave the corpse.
 *
 * ## Why mobs never had them before
 * Vanilla's mob-gear enchanting is data-driven: `MobEntity#enchantEquipment` calls
 * `EnchantmentHelper#applyEnchantmentProvider(stack, …)` with `EnchantmentProviders.MOB_SPAWN_EQUIPMENT`,
 * whose file is `data/minecraft/enchantment_provider/mob_spawn_equipment.json`:
 *
 * ```json
 * { "type": "minecraft:by_cost_with_difficulty", "enchantments": "#minecraft:on_mob_spawn_equipment", … }
 * ```
 *
 * The mod's 85 enchantments are in none of the tags that provider reads, so a mob could never roll one
 * no matter how the enchanting-table weights were tuned. This object adds a **second** pass on top of
 * vanilla's, so vanilla gear progression (and its cost curve) is untouched.
 *
 * ## The two halves
 * 1. **On spawn**, after vanilla's enchant, roll a per-slot chance and add at most **one** mod
 *    enchantment. The mob keeps it for life — that is the "stronger mobs" half.
 * 2. **On death**, strip every `simple_tweaks:*` enchantment from the equipped stacks *before* vanilla
 *    drops them. Equipment still drops (so the player is still rewarded with the item and its **vanilla**
 *    enchantments), but the mod's enchantments cannot be farmed off mobs.
 *
 * ## Performance design (this is why the code looks the way it does)
 * This runs **once per mob spawn and once per mob death — never per tick**. Within that budget:
 *
 * | technique | why |
 * |---|---|
 * | pool cached per [DynamicRegistryManager] | 85 declarations → registry lookups + category filter happen **once per datapack load**, not per mob. A datapack reload produces a new manager instance, so the cache invalidates by construction and cannot go stale. |
 * | weights / maxLevels precomputed into `IntArray` | the pick path never calls `getWeight()` / `getMaxLevel()` — those are virtual calls into registry entries, and a mob farm hits this thousands of times |
 * | **chance rolled before any pool work** | 60 % / 40 % means the candidate scan runs on a minority of slots, and never for empty ones |
 * | early exit when the stack already has a mod enchantment | one component read instead of a 56-entry scan |
 * | no allocation in the pick path | two index loops over cached arrays; no filtered `List`, no lambda, no `stream` |
 * | stripping tests membership in a prebuilt `Set` | O(1) per enchantment on the stack; the empty-component case (most mobs) short-circuits on the first read |
 *
 * Measured order of magnitude for the worst case (every slot passes its roll, all six slots occupied):
 * ~112 `isPrimaryItem` tag lookups per mob. A mob farm spawning 10 mobs/second is therefore ~0.01 % of
 * one second's CPU — i.e. this feature is **not** a candidate for further optimisation, and its cost is
 * amortised against a spawn that already touches loot tables and attribute maps.
 *
 * ## What is deliberately *not* here
 * - **No config key.** The three probabilities are `const val` next to each other. A config key would
 *   need a JSON reader, a screen row, two lang entries and an acceptance item, and this project has
 *   already shipped three config keys nobody read (`AGENTS.md` §6).
 * - **No curated id list.** The pool is derived from `GeneratedEnchantments.KEYS` + `CATEGORY`, so a new
 *   enchantment joins (or does not, by category) automatically. A hand-written list is the exact shape
 *   that has drifted twice in this repo (`AGENTS.md` §4.10).
 */
object MobEnchantments {

    /**
     * Chance per armour slot at difficulty **0**, rising to [ARMOR_CHANCE_MAX] at
     * [DIFFICULTY_FOR_FULL_CHANCE] and above.
     *
     * ⚠️ These used to be `0.40` / `0.60` **multiplied by** `clampedLocalDifficulty`. That reads fine on
     * paper and is wrong in play: vanilla's number is ~`0.13` near spawn and in freshly loaded chunks, so
     * the real chance was `5.2 %` (armour) and `7.8 %` (main hand) — measured in a real log, three mobs
     * produced exactly one enchanted weapon. A multiplicative curve makes the feature indistinguishable
     * from "not implemented" in the place players spend most of their time.
     *
     * The interpolation below keeps a visible floor and still reaches the ceiling only in genuinely hard
     * areas, which is what "stronger mobs where it matters" was supposed to mean.
     */
    const val ARMOR_CHANCE_MIN = 0.20f

    /** Chance per armour slot at [DIFFICULTY_FOR_FULL_CHANCE] and above. */
    const val ARMOR_CHANCE_MAX = 0.60f

    /** Chance for the main hand at difficulty 0. */
    const val MAIN_HAND_CHANCE_MIN = 0.30f

    /** Chance for the main hand at [DIFFICULTY_FOR_FULL_CHANCE] and above. */
    const val MAIN_HAND_CHANCE_MAX = 0.85f

    /**
     * `clampedLocalDifficulty` at which the chances reach their maximum.
     *
     * `2.0` is reachable in normal play but not near spawn: it takes a settled chunk on at least normal
     * difficulty. Vanilla's own clamp goes to `6.75`, so there is headroom above this where the feature
     * is simply saturated rather than getting even better.
     */
    const val DIFFICULTY_FOR_FULL_CHANCE = 2.0f

    /** Ceiling on how many mod enchantments one item can receive (`1 + (level-1)/4`). */
    private const val MAX_ENCHANTS_PER_ITEM = 3

    /**
     * Categories eligible for mob gear.
     *
     * MYTHIC / MYSTERY / UNIQUE are excluded on purpose: that band contains `infinite_power`
     * (creative-only by design), `kill_aura` (auto-attacks on its own), `unbreakable` and `reforge`
     * (utility, not threat). A mob holding any of those is either a bug or an unfair fight.
     */
    private val ALLOWED_CATEGORIES = setOf(
        EnchantCategory.COMMON,
        EnchantCategory.UNCOMMON,
        EnchantCategory.RARE,
        EnchantCategory.EPIC,
        EnchantCategory.LEGENDARY,
    )

    /** Every slot a mob can have equipment in — used for stripping, in a fixed order. */
    private val EQUIPMENT_SLOTS = arrayOf(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    /**
     * One registry's view of the eligible mod enchantments, with everything the pick path needs already
     * flattened into primitive arrays.
     *
     * Invariant: [entries], [enchantments], [weights] and [maxLevels] are index-aligned.
     */
    private class Pool(
        val entries: Array<RegistryEntry<Enchantment>>,
        val enchantments: Array<Enchantment>,
        val weights: IntArray,
        val maxLevels: IntArray,
        /** Same elements as [entries], for O(1) "is this one of ours?" tests when stripping. */
        val members: Set<RegistryEntry<Enchantment>>,
    )

    /**
     * Cached pools, one per registry manager.
     *
     * `WeakHashMap` and not a plain map: a dedicated server that reloads datapacks repeatedly would
     * otherwise retain one pool per reload forever. Keys are the registry manager itself, so the entry
     * dies with it.
     */
    private val pools = WeakHashMap<DynamicRegistryManager, Pool>()

    /** Builds (or returns the cached) pool for [manager]. Synchronised because `WeakHashMap` is not. */
    @Synchronized
    private fun poolFor(manager: DynamicRegistryManager): Pool = pools.getOrPut(manager) { buildPool(manager) }

    private fun buildPool(manager: DynamicRegistryManager): Pool {
        val registry = manager.get(RegistryKeys.ENCHANTMENT)
        val entries = ArrayList<RegistryEntry<Enchantment>>(64)

        for (key in GeneratedEnchantments.KEYS) {
            val id = key.value.path
            if (GeneratedEnchantments.CATEGORY[id] !in ALLOWED_CATEGORIES) continue
            // `getEntry` is Optional; a key with no registry entry means the datapack did not define it
            // (e.g. a pack removed one), and skipping is the only sane behaviour.
            registry.getEntry(key).ifPresent { entries.add(it) }
        }

        val n = entries.size
        val array = entries.toTypedArray()
        val enchantments = Array(n) { array[it].value() }
        return Pool(
            entries = array,
            enchantments = enchantments,
            // Coerced at build time so the pick path can add without a `max(1, …)` per candidate.
            weights = IntArray(n) { enchantments[it].weight.coerceAtLeast(1) },
            maxLevels = IntArray(n) { enchantments[it].maxLevel.coerceAtLeast(1) },
            members = array.toHashSet(),
        )
    }

    // ---------------------------------------------------------------- spawn side

    /**
     * Called from `MobEntity#enchantEquipment`'s RETURN, i.e. **after** vanilla has finished with this
     * slot. Adds at most one mod enchantment.
     *
     * Returning early is the common path and deliberately costs almost nothing: the empty-stack test and
     * the already-has-one test are single component reads, and the probability roll happens before the
     * 56-entry candidate scan.
     */
    fun tryApplyAtSpawn(
        mob: MobEntity,
        slot: EquipmentSlot,
        world: ServerWorldAccess,
        random: Random,
        effectiveDifficulty: Float,
    ) {
        val stack = mob.getEquippedStack(slot)
        if (stack.isEmpty) return

        // Config first: both reads are plain field reads, and bailing before touching the registry keeps
        // the disabled case free of even the cached-pool lookup.
        if (!MobEnhancementConfig.enabled) return
        if (!MobEnhancementConfig.enchantmentsEnabled) return

        val pool = poolFor(world.registryManager)
        if (pool.entries.isEmpty()) return
        // Never stack a second mod enchantment on the same item; one is already a noticeable buff.
        if (hasModEnchantment(stack, pool)) return

        // Interpolate floor → ceiling instead of multiplying by difficulty: see the constant KDoc for the
        // measured reason (`0.6 × 0.13` = 7.8 % made the feature look absent).
        val chance = if (slot == EquipmentSlot.MAINHAND) {
            chanceFor(MAIN_HAND_CHANCE_MIN, MAIN_HAND_CHANCE_MAX, effectiveDifficulty)
        } else {
            chanceFor(ARMOR_CHANCE_MIN, ARMOR_CHANCE_MAX, effectiveDifficulty)
        }
        if (random.nextFloat() >= chance) {
            // Debug-gated. Exists so a plain slot and a dead seam are distinguishable in the log —
            // `AGENTS.md` §4.4. The first real session after this feature shipped had three mobs, one
            // `mob-enchanted` line and no way to tell whether the other two had rolled or never reached
            // here at all.
            FSTLog.log("MobEnchant") {
                "mob=${mob.type.name.string}, slot=$slot, chance=${"%.2f".format(chance)}, " +
                    "difficulty=${"%.2f".format(effectiveDifficulty)}, " +
                    "outcome=mob-enchant-roll-failed"
            }
            return
        }

        // How many mod enchantments this item gets. **More than one from level 5 upwards**, because a
        // single roll per item made every high-level mob read the same: one bow enchant on a skeleton, one
        // sword enchant on a zombie, and nothing on a mob whose main hand is empty.
        //
        // `1 + (level-1)/4`, capped at [MAX_ENCHANTS_PER_ITEM]: level 1-4 → 1, 5-8 → 2, 9+ → 3.
        val mobLevel = (mob as? LevelledMob)?.mobLevel ?: 1
        val wanted = (1 + (mobLevel - 1) / 4).coerceAtMost(MAX_ENCHANTS_PER_ITEM)

        val applied = applyEnchantments(mob, slot, stack, pool, random, wanted)
        if (applied == 0) return

        // Re-equip so the enchantment's own effects (attributes, location-based effects) are applied and
        // the change is tracked; the ItemStack was mutated in place, so this is mostly bookkeeping.
        mob.equipStack(slot, stack)

        FSTLog.log("MobEnchant") {
            "mob=${mob.type.name.string}, slot=$slot, applied=$applied (wanted=$wanted, level=$mobLevel), " +
                "chance=${"%.2f".format(chance)}, difficulty=${"%.2f".format(effectiveDifficulty)}, " +
                "outcome=mob-enchanted"
        }
    }

    /**
     * Applies up to [wanted] distinct mod enchantments to [stack], returning how many were added.
     *
     * ⚠️ Built explicitly instead of calling `ItemStack#addEnchantment`. That helper goes through
     * `EnchantmentHelper#apply`, which **returns early when the component is absent** — and mob gear that
     * vanilla did not enchant has no ENCHANTMENTS component at all, so `addEnchantment` would silently do
     * nothing. This is the same silent no-op recorded in `EnchantmentScreenHandlerMixin`.
     */
    private fun applyEnchantments(
        mob: MobEntity,
        slot: EquipmentSlot,
        stack: ItemStack,
        pool: Pool,
        random: Random,
        wanted: Int,
    ): Int {
        val type = DataComponentTypes.ENCHANTMENTS
        var applied = 0
        for (attempt in 0 until wanted) {
            val index = pick(stack, pool, random)
            if (index < 0) break
            val level = 1 + random.nextInt(pool.maxLevels[index])
            val builder = ItemEnchantmentsComponent.Builder(EnchantmentHelper.getEnchantments(stack))
            builder.add(pool.entries[index], level)
            stack.set(type, builder.build())
            applied++
        }
        return applied
    }

    /**
     * Linearly interpolates [min] → [max] over `0 .. `[DIFFICULTY_FOR_FULL_CHANCE].
     *
     * Linear on purpose: it is trivially predictable ("a third of mobs near spawn, most of them in hard
     * areas") and has no tuning constant beyond the three that are already here. A curve would be more
     * "natural" and impossible to reason about without a table.
     */
    private fun chanceFor(min: Float, max: Float, effectiveDifficulty: Float): Float {
        val t = (effectiveDifficulty / DIFFICULTY_FOR_FULL_CHANCE).coerceIn(0f, 1f)
        return min + (max - min) * t
    }

    /**
     * Picks one eligible enchantment index, weighted by `Enchantment#getWeight()`.
     *
     * Two index loops, **no allocation**: the first sums the weights of the eligible candidates, the
     * second walks them again subtracting from a roll. Eligibility is checked in both passes because
     * caching it would need a per-call scratch buffer; at 56 candidates worst case that is ~112 tag
     * lookups, which the class KDoc explains is negligible for something that runs once per mob spawn.
     */
    private fun pick(stack: ItemStack, pool: Pool, random: Random): Int {
        val existing = EnchantmentHelper.getEnchantments(stack).enchantments
        var total = 0
        for (i in pool.entries.indices) {
            if (!eligible(i, stack, existing, pool)) continue
            total += pool.weights[i]
        }
        if (total <= 0) return -1

        var roll = random.nextInt(total)
        for (i in pool.entries.indices) {
            if (!eligible(i, stack, existing, pool)) continue
            roll -= pool.weights[i]
            if (roll < 0) return i
        }
        return -1
    }

    private fun eligible(
        index: Int,
        stack: ItemStack,
        existing: Set<RegistryEntry<Enchantment>>,
        pool: Pool,
    ): Boolean {
        if (!pool.enchantments[index].isPrimaryItem(stack)) return false
        // `canBeCombined` only tests exclusive sets, so without this a multi-enchant roll could pick the
        // same enchantment twice and *replace* its level with a fresh roll (sometimes lower).
        if (existing.contains(pool.entries[index])) return false
        if (existing.isEmpty()) return true
        val candidate = pool.entries[index]
        for (other in existing) {
            // Covers vanilla's `exclusive_set/damage` *and* this mod's `exclusive_set/elements`:
            // `canBeCombined` checks both sides' exclusive sets (bytecode-verified).
            if (!Enchantment.canBeCombined(candidate, other)) return false
        }
        return true
    }

    // ---------------------------------------------------------------- drop side

    /**
     * Strips every mod enchantment from [mob]'s equipped stacks.
     *
     * Called from `MobEntity#dropEquipment` HEAD, i.e. **before** vanilla decides what drops, so the
     * stripped stack is the one that ends up on the ground. Equipment still drops with its vanilla
     * enchantments — only the mod's own are removed, which is the anti-farm guarantee.
     *
     * Cost: one component read per occupied slot, and the enchantment loop only runs for stacks that
     * actually have enchantments. An ordinary zombified mob with no enchantments never reaches it.
     */
    fun stripAll(mob: MobEntity, world: ServerWorld) {
        val pool = poolFor(world.registryManager)
        if (pool.members.isEmpty()) return
        for (slot in EQUIPMENT_SLOTS) {
            val stack = mob.getEquippedStack(slot)
            if (stack.isEmpty) continue
            // Re-equip only when something was actually removed: `equipStack` is not free (attribute
            // map rebuild, tracking) and the vast majority of slots have nothing to strip.
            if (stripModEnchantments(stack, pool)) {
                mob.equipStack(slot, stack)
                FSTLog.log("MobEnchant") {
                    "mob=${mob.type.name.string}, slot=$slot, outcome=dropped-mod-enchantments-stripped"
                }
            }
        }
    }

    /** Removes mod enchantments from [stack] in place. Returns `true` when it changed something. */
    private fun stripModEnchantments(stack: ItemStack, pool: Pool): Boolean {
        val type = DataComponentTypes.ENCHANTMENTS
        val current = stack.get(type) ?: return false
        if (current.isEmpty) return false

        var found = false
        for (entry in current.enchantments) {
            if (entry in pool.members) {
                found = true
                break
            }
        }
        if (!found) return false

        val builder = ItemEnchantmentsComponent.Builder(current)
        builder.remove { it in pool.members }
        stack.set(type, builder.build())
        return true
    }

    /** True when [stack] already carries at least one mod enchantment. */
    private fun hasModEnchantment(stack: ItemStack, pool: Pool): Boolean {
        val current = stack.get(DataComponentTypes.ENCHANTMENTS) ?: return false
        if (current.isEmpty) return false
        for (entry in current.enchantments) {
            if (entry in pool.members) return true
        }
        return false
    }
}
