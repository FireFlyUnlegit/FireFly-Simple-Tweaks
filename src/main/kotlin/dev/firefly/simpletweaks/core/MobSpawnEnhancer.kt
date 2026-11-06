package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketMobLevel
import dev.firefly.simpletweaks.util.mobs.LevelledMob
import dev.firefly.simpletweaks.util.mobs.MobElements
import dev.firefly.simpletweaks.util.mobs.MobLevels
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.entity.mob.MobEntity

/**
 * Gives **every** mob its level (and therefore its base stats) — not only the ones that wear equipment.
 *
 * ## The bug this fixes
 * Until `v2.2.0 build=cleanup2`, `MobLevels.ensureLevel` and `MobElements.tryAssign` were only called from
 * `MobEntityEnchantMixin`, i.e. inside the **private** `MobEntity#enchantEquipment(world, slot, random,
 * chance, difficulty)` overload. That overload is reached only through the per-slot equipment path, so a
 * spider, a slime, a creeper or any other mob whose `initEquipment` does nothing **never ran it at all**:
 * no level, no name tag, no health/attack/armour bonus, no element. Zombies and skeletons looked fine
 * because they are exactly the mobs that go through that path (and `MobEquipmentTopUpMixin` even hands
 * them gear on purpose).
 *
 * ## Why `ServerEntityEvents.ENTITY_LOAD` and not another mixin
 * The mixin we have is the *equipment* seam; what is needed is a **spawn** seam that does not care whether
 * the mob has slots. The candidates were:
 *
 * | candidate | verdict |
 * |---|---|
 * | `MobEntity#initialize` (mixin) | Correct and universal in principle, but **every** mob class overrides it (`Zombie`, `Skeleton`, `Spider`, `Creeper`, `Slime`, `Enderman`, …). Injecting there needs an override audit and silently does nothing for any subclass that forgets `super` — the `hasLabel` trap from `AGENTS.md` §4.3 again. |
 * | this event | Fires for **every** entity that enters a `ServerWorld`, i.e. natural spawns, spawners, spawn eggs, `/summon`, and chunk loads. No mixin, no override audit, and the entity is already fully built. |
 *
 * ## Idempotency and the one-time guarantee
 * `MobLevels.ensureLevel` writes the level into the entity and returns early when it is already `> 0`;
 * the level is persisted in NBT (`MobLevelMixin`), so a mob is levelled **exactly once in its life**.
 * The hook below skips before doing any expensive work when that marker is already set, which matters
 * because this event fires again for every mob on every chunk load.
 *
 * ## Consequence: mobs that already exist in an old save
 * Such a mob has no `simple_tweaks:mob_level` in its NBT, so the first time its chunk loads after this
 * change it **is** levelled (and healed to its new maximum). That is treated as the feature working rather
 * than as a bug: without it, a world created before the level system would keep its trivial mobs forever.
 *
 * ## Cost
 * One `getLocalDifficulty` + one nearest-player scan per mob, **once per mob lifetime**. Already-levelled
 * mobs cost one `instanceof`, one interface cast and one int compare on load. Nothing runs per tick.
 *
 * ## Why the `levelsEnabled` gate also gates the element roll
 * The level is the "already processed" marker, so with levels switched off there is nothing to record and
 * the hook would re-roll the element on every chunk load. Element assignment for *equipped* mobs keeps
 * working through the equipment seam in that configuration, which is exactly what it did before.
 */
object MobSpawnEnhancer {

    /**
     * How far a player can be and still receive the level packet.
     *
     * Matches the damage-indicator broadcast (64) is *not* enough here: a mob is tracked by every player
     * whose view distance reaches it, which at 12 chunks is ~192 blocks at the corner. 160 keeps the
     * packet count sane while covering the cases the tag matters for.
     */
    private const val ANNOUNCE_RANGE = 160.0

    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity !is MobEntity) return@register
            if (!MobEnhancementConfig.enabled || !MobEnhancementConfig.levelsEnabled) return@register
            val levelled = entity as? LevelledMob ?: return@register
            // Already decided at spawn, or read back from NBT: nothing to do (and this is the hot path on
            // every chunk load, so it must come before the difficulty scan).
            if (levelled.mobLevel > 0) return@register

            // The same number the equipment seam uses, so a mob gets the same level whether or not it
            // happens to own a sword. See `MobLevels.effectiveDifficulty`.
            val difficulty = world.getLocalDifficulty(entity.blockPos)
            val effective = MobLevels.effectiveDifficulty(entity, world, difficulty)
            MobLevels.ensureLevel(entity, effective, world.random)
            MobElements.tryAssign(entity, world.random, effective)

            // `build=cleanup24`: the level is *also* announced here, not only from
            // `MobLevelTracker`'s `START_TRACKING`. The two seams are not ordered relative to each other:
            // when tracking starts before this callback runs (mobs added mid-tick — slime splits,
            // summon-ed minions, spawner cycles) the packet would carry level **0** and nothing ever
            // re-sends it, so that client shows no `[Lv.N]` name tag until the chunk is reloaded.
            // Sending again is idempotent: `MobLevelSync` keys by entity id and simply overwrites.
            val level = levelled.mobLevel
            if (level > 0) {
                for (player in world.players) {
                    if (player.squaredDistanceTo(entity) <= ANNOUNCE_RANGE * ANNOUNCE_RANGE) {
                        NetworkManager.sendToClient(PacketMobLevel(entity.id, level), player)
                    }
                }
            }
        }
    }
}
