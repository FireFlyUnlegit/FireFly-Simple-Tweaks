package dev.firefly.simpletweaks.compat;

import dev.firefly.simpletweaks.compat.event.BlockEvent;
import dev.firefly.simpletweaks.compat.event.CriticalHitEvent;
import dev.firefly.simpletweaks.compat.event.EventResult;
import dev.firefly.simpletweaks.compat.event.LivingAttackEvent;
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent;
import dev.firefly.simpletweaks.compat.event.LivingHealEvent;
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent;
import dev.firefly.simpletweaks.compat.event.PlayerDropsEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared state for the living-entity damage seams.
 *
 * <h2>The problem this solves</h2>
 * Forge's damage events let a handler both <b>cancel</b> the hit and <b>rewrite the damage amount</b>.
 * In Mixin those two capabilities live in different injectors:
 * <ul>
 *   <li>cancelling the call requires {@code @Inject(at = HEAD, cancellable = true)}</li>
 *   <li>rewriting a method <i>argument</i> requires {@code @ModifyVariable(at = HEAD, argsOnly = true)}</li>
 * </ul>
 * Two injectors at the same {@code HEAD} point means the event must be fired exactly once no matter
 * which injector runs first — and Mixin does not document a guaranteed relative order.
 *
 * <h2>Why this is safe</h2>
 * Each accessor is <i>get-or-fire</i>: it reuses the cached event only when the cache was created
 * for the <b>same entity and the same DamageSource instance</b>, otherwise it fires a fresh one.
 * Consequences:
 * <ul>
 *   <li>Whichever injector runs first, exactly one event is fired per invocation.</li>
 *   <li>The other injector finds the cache and observes the fired event's mutations and cancel flag.</li>
 *   <li>If a cache ever survives into a later invocation (e.g. the method exited early through the
 *       cancel path before {@code @At("RETURN")} could clear it), the identity check fails and a new
 *       event is fired — so a stale entry can never silently suppress a handler run.</li>
 * </ul>
 *
 * <p>Mutating handlers in the 1.12.2 code make this mandatory: 25 call sites do
 * {@code e.amount *= ... / += ... / = ...}, and 15 call sites set {@code isCanceled = true} directly
 * without zeroing the amount (including {@code EnchantImmortalHandler} and
 * {@code EnchantDeathProtectionHandler}) — so "treat cancel as amount 0" would be wrong.
 */
public final class EventSeams {

    private static final ThreadLocal<LivingHurtEvent> HURT = new ThreadLocal<>();
    private static final ThreadLocal<LivingDamageEvent> DAMAGE = new ThreadLocal<>();
    private static final ThreadLocal<LivingHealEvent> HEAL = new ThreadLocal<>();

    private EventSeams() {
    }

    /**
     * Fires {@link LivingAttackEvent} and then {@link LivingHurtEvent}, once per
     * {@code Entity#damage} invocation, preserving Forge's ordering (attack before hurt).
     */
    public static LivingHurtEvent hurt(LivingEntity entity, DamageSource source, float amount) {
        LivingHurtEvent cached = HURT.get();
        if (cached != null && cached.getEntityLiving() == entity && cached.getSource() == source) {
            return cached;
        }

        LivingHurtEvent event;
        LivingAttackEvent attack = new LivingAttackEvent(entity, source, amount);
        ForgeEventBus.post(attack);
        if (attack.isCanceled()) {
            // Forge semantics: cancelling LivingAttackEvent suppresses the hit entirely.
            event = new LivingHurtEvent(entity, source, 0.0f);
            event.setCanceled(true);
        } else {
            event = new LivingHurtEvent(entity, source, amount);
            ForgeEventBus.post(event);
        }
        HURT.set(event);
        return event;
    }

    public static void clearHurt() {
        HURT.remove();
    }

    /**
     * Fires {@link LivingDamageEvent} once per {@code LivingEntity#applyDamage} invocation.
     * A cancelled event is reported as zero remaining damage.
     */
    public static LivingDamageEvent damage(LivingEntity entity, DamageSource source, float amount) {
        LivingDamageEvent cached = DAMAGE.get();
        if (cached != null && cached.getEntityLiving() == entity && cached.getSource() == source) {
            return cached;
        }
        LivingDamageEvent event = new LivingDamageEvent(entity, source, amount);
        ForgeEventBus.post(event);
        DAMAGE.set(event);
        return event;
    }

    public static void clearDamage() {
        DAMAGE.remove();
    }

    /**
     * Fires {@link LivingHealEvent} once per {@code LivingEntity#heal} invocation.
     * A cancelled event is reported as zero healing.
     */
    public static LivingHealEvent heal(LivingEntity entity, float amount) {
        LivingHealEvent cached = HEAL.get();
        if (cached != null && cached.getEntityLiving() == entity) {
            return cached;
        }
        LivingHealEvent event = new LivingHealEvent(entity, amount);
        ForgeEventBus.post(event);
        HEAL.set(event);
        return event;
    }

    public static void clearHeal() {
        HEAL.remove();
    }

    // ------------------------------------------------------------------ harvest drops

    /** Per-invocation buffer for [dev.firefly.simpletweaks.compat.event.BlockEvent.HarvestDropsEvent]. */
    private static final class Harvest {
        final World world;
        final BlockPos pos;
        final BlockState state;
        final PlayerEntity harvester;
        final List<ItemStack> stacks = new ArrayList<>();
        /** nesting depth: only the outermost dropStacks fires the event and re-spawns. */
        int depth;

        Harvest(World world, BlockPos pos, BlockState state, PlayerEntity harvester) {
            this.world = world;
            this.pos = pos;
            this.state = state;
            this.harvester = harvester;
        }
    }

    private static final ThreadLocal<Harvest> HARVEST = new ThreadLocal<>();

    /**
     * Opens (or deepens) the harvest buffer. Does nothing when no player is involved, so
     * non-player block breaks keep vanilla behaviour and pay only one type check.
     */
    public static void beginHarvest(World world, BlockPos pos, BlockState state, Entity harvester) {
        Harvest current = HARVEST.get();
        if (current != null) {
            current.depth++;
            return;
        }
        if (!(harvester instanceof PlayerEntity player)) {
            return;
        }
        Harvest buffer = new Harvest(world, pos, state, player);
        buffer.depth = 1;
        HARVEST.set(buffer);
    }

    /**
     * Records a would-be drop and reports whether the caller should cancel the spawn.
     * Returns false when no buffer is open, so vanilla spawns the stack normally.
     */
    public static boolean captureHarvestDrop(ItemStack stack) {
        Harvest buffer = HARVEST.get();
        if (buffer == null) {
            return false;
        }
        buffer.stacks.add(stack.copy());
        return true;
    }

    /**
     * Fires the event for the outermost harvest and re-spawns whatever survived. The buffer is
     * cleared before spawning so the spawn calls are not themselves captured.
     */
    public static void finishHarvest() {
        Harvest buffer = HARVEST.get();
        if (buffer == null) {
            return;
        }
        if (--buffer.depth > 0) {
            return;
        }
        HARVEST.remove();

        BlockEvent.HarvestDropsEvent event = new BlockEvent.HarvestDropsEvent(
                buffer.world, buffer.pos, buffer.state, buffer.harvester, buffer.stacks);
        ForgeEventBus.post(event);

        for (ItemStack stack : buffer.stacks) {
            if (!stack.isEmpty()) {
                Block.dropStack(buffer.world, buffer.pos, stack);
            }
        }
    }

    // ------------------------------------------------------------------ mining position

    /**
     * The block currently being mined, for {@code PlayerEvent.BreakSpeed}.
     *
     * Set by {@link dev.firefly.simpletweaks.mixin.AbstractBlockBreakingDeltaMixin} around
     * {@code AbstractBlock#calcBlockBreakingDelta} (which receives the position) and read by
     * {@link dev.firefly.simpletweaks.mixin.PlayerEntityBreakSpeedMixin} during the synchronous
     * {@code getBlockBreakingSpeed} call inside it. Forge exposed the same value as
     * {@code BreakSpeed.getPos()}.
     */
    private static final ThreadLocal<BlockPos> MINING_POS = new ThreadLocal<>();

    public static void setMiningPos(BlockPos pos) {
        MINING_POS.set(pos);
    }

    public static BlockPos getMiningPos() {
        return MINING_POS.get();
    }

    public static void clearMiningPos() {
        MINING_POS.remove();
    }

    // ------------------------------------------------------------------ critical hit

    /**
     * Per-swing state for {@link CriticalHitEvent}, opened by
     * {@link dev.firefly.simpletweaks.mixin.PlayerEntityAttackMixin} at the head of
     * {@code PlayerEntity#attack(Entity)}.
     *
     * <p>Unlike the damage seams this one has no ambiguity about injector order: the three injectors
     * sit at strictly increasing bytecode offsets (HEAD, the vanilla {@code f *= 1.5F} constant, the
     * {@code Entity#damage} call), so they always run in that order.
     *
     * <p>The event is created lazily, on the first of those two points that is reached. That keeps
     * Forge's firing position exactly: if vanilla returns early (target not attackable, shield block,
     * …) no event is fired, just as {@code ForgeHooks.getCriticalHit} was never reached.
     */
    private static final class Crit {
        final PlayerEntity player;
        final Entity target;
        /**
         * Attack charge captured at the HEAD of {@code attack}, before vanilla resets the cooldown.
         * Reading it live at event time returns ≈0.5 and breaks every {@code charge >= 0.848} gate.
         */
        final float attackCharge;
        /** Set by the {@code @ModifyConstant} hook — i.e. vanilla really did take the crit branch. */
        boolean vanillaCritical;
        CriticalHitEvent event;

        Crit(PlayerEntity player, Entity target, float attackCharge) {
            this.player = player;
            this.target = target;
            this.attackCharge = attackCharge;
        }
    }

    private static final ThreadLocal<Crit> CRIT = new ThreadLocal<>();

    public static void beginCrit(PlayerEntity player, Entity target) {
        CRIT.set(new Crit(player, target, player.getAttackCooldownProgress(0.5f)));
    }

    public static boolean critActive() {
        return CRIT.get() != null;
    }

    /** Called from the {@code @ModifyConstant} hook; records that vanilla's crit branch was taken. */
    public static void markVanillaCrit() {
        Crit crit = CRIT.get();
        if (crit != null) {
            crit.vanillaCritical = true;
        }
    }

    /**
     * Fires the event (once) and returns the multiplier to apply to the swing damage.
     *
     * <p>Mirrors {@code ForgeHooks.getCriticalHit} + {@code EntityPlayer}: the swing is a crit iff
     * {@code result == ALLOW || (vanillaCritical && result == DEFAULT)}; otherwise the multiplier is
     * exactly {@code 1.0}. Vanilla's own {@code *1.5} has already been neutralised, so this value is
     * applied to the un-crit damage.
     */
    public static float critMultiplier() {
        Crit crit = CRIT.get();
        if (crit == null) {
            return 1.0f;
        }
        if (crit.event == null) {
            crit.event = new CriticalHitEvent(crit.player, crit.target, crit.vanillaCritical,
                    crit.attackCharge);
            ForgeEventBus.post(crit.event);
        }
        boolean isCrit = crit.event.getResult() == EventResult.ALLOW
                || (crit.vanillaCritical && crit.event.getResult() == EventResult.DEFAULT);
        return isCrit ? crit.event.getDamageModifier() : 1.0f;
    }

    public static void endCrit() {
        CRIT.remove();
    }

    /**
     * Forge's {@code ForgeHooks.getCriticalHit(player, target, vanillaCritical, critModifier)},
     * exposed for {@code util/PlayerUtils.runPlayerAttack} — the only caller that needs to crit-roll
     * outside the vanilla attack path (the 1.12.2 helper re-created the pipeline by hand).
     *
     * @return the damage multiplier to apply, or {@code 0} when the hit is not a critical hit
     *         (Forge signalled that by returning {@code null}).
     */
    public static float forgeCriticalHit(PlayerEntity player, Entity target, boolean vanillaCritical) {
        // Not inside PlayerEntity#attack, so the cooldown timer is untouched and a live read is the
        // right value here (this is the runPlayerAttack path, e.g. the AoE splash).
        CriticalHitEvent event = new CriticalHitEvent(player, target, vanillaCritical,
                player.getAttackCooldownProgress(0.5f));
        ForgeEventBus.post(event);
        boolean isCrit = event.getResult() == EventResult.ALLOW
                || (vanillaCritical && event.getResult() == EventResult.DEFAULT);
        return isCrit ? event.getDamageModifier() : 0.0f;
    }

    // ------------------------------------------------------------------ player death drops

    /**
     * Per-death buffer for {@link PlayerDropsEvent}, opened by
     * {@link dev.firefly.simpletweaks.mixin.ServerPlayerDropsMixin} at the head of
     * {@code ServerPlayerEntity#onDeath} and filled by
     * {@link dev.firefly.simpletweaks.mixin.PlayerEntityDropItemMixin} from
     * {@code PlayerEntity#dropItem}.
     *
     * <p>Yarn 1.21.1 ground truth (verified in the bytecode): {@code ServerPlayerEntity#onDeath}
     * calls {@code PlayerEntity#dropItem(ItemStack, ZZ)} directly — it does <b>not</b> route through
     * {@code PlayerInventory#dropAll()}, which discards the entity it gets back. So {@code dropItem}
     * is the one and only place the death drops materialise.
     */
    private static final class PlayerDrops {
        final ServerPlayerEntity player;
        final List<ItemEntity> drops = new ArrayList<>();

        PlayerDrops(ServerPlayerEntity player) {
            this.player = player;
        }
    }

    private static final ThreadLocal<PlayerDrops> PLAYER_DROPS = new ThreadLocal<>();

    public static void beginPlayerDrops(ServerPlayerEntity player) {
        // Overwrite rather than nest: only one death can be in flight on a thread, and an
        // overwrite makes a buffer leaked by an early return harmless instead of permanent.
        PLAYER_DROPS.set(new PlayerDrops(player));
    }

    public static void capturePlayerDrop(ItemEntity entity) {
        PlayerDrops buffer = PLAYER_DROPS.get();
        if (buffer != null && entity != null) {
            buffer.drops.add(entity);
        }
    }

    /**
     * Posts the event and discards whatever a handler removed.
     *
     * <p>The snapshot is what makes "removed" observable: Forge never spawned the excluded entities,
     * but 1.21.1 spawns them inside {@code onDeath}, so exclusion is expressed as a
     * {@link Entity#discard()} in the same tick. See {@link PlayerDropsEvent} for why that is
     * accepted rather than worked around.
     */
    public static void finishPlayerDrops() {
        PlayerDrops buffer = PLAYER_DROPS.get();
        if (buffer == null) {
            return;
        }
        PLAYER_DROPS.remove();
        if (buffer.drops.isEmpty()) {
            return;
        }

        List<ItemEntity> before = new ArrayList<>(buffer.drops);
        ForgeEventBus.post(new PlayerDropsEvent(buffer.player, buffer.drops));
        for (ItemEntity entity : before) {
            if (!buffer.drops.contains(entity)) {
                entity.discard();
            }
        }
    }
}
