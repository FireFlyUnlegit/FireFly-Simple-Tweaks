package dev.firefly.simpletweaks.core

import com.mojang.brigadier.context.CommandContext
import dev.firefly.simpletweaks.SimpleTweaks
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.command.argument.BlockPosArgumentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.EnchantmentTags
import net.minecraft.screen.EnchantmentScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * Development-only integration-test command.
 *
 * ## Why this exists
 * The server-side acceptance harness drives a dedicated server over RCON using Fabric Carpet's
 * `/player` fake-player command. That works for entity interactions (`attack`, `use`, `look`), but
 * **Carpet 1.4.147 — the only release supporting MC 1.21.1 — has no `mine` action**, so there is no
 * way to make a fake player break a block. Verified empirically: neither
 * `/execute as Bot run setblock <pos> air destroy` nor `/loot spawn <pos> mine <pos> <tool>` reaches
 * this mod's seams (the former carries no player context, the latter bypasses `Block.dropStacks`
 * entirely).
 *
 * So the two block-related seams are driven directly here, calling **the same vanilla entry points a
 * real harvest calls** rather than a re-implementation:
 *  - `harvest`     -> `Block.dropStacks(BlockState, World, BlockPos, BlockEntity, Entity, ItemStack)`
 *                     (`method_9511`) — exactly what breaking a block invokes, so the
 *                     `HarvestDropsEvent` seam and AutoSmelt's smelt/durability/XP logic all run.
 *  - `breakspeed`  -> `BlockState.calcBlockBreakingDelta(PlayerEntity, BlockView, BlockPos)`
 *                     (`method_26165`) -> `AbstractBlock.calcBlockBreakingDelta` (`method_9594`),
 *                     which is where the mining position is captured and `getBlockBreakingSpeed`
 *                     is called. Calling it twice exercises the Momentum ramp (the first call only
 *                     records the position; the second applies the bonus).
 *
 * ## Scope
 * Registered **only** when `FabricLoader.isDevelopmentEnvironment()`, so it does not exist in a
 * published jar. Not gated behind a config flag on purpose — a dev-run is the only place it can load.
 *
 * Output is a single feedback line per invocation containing the numbers the acceptance script
 * asserts on, so the assertions do not need a second round trip.
 */
object DevTestCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return@register
            SimpleTweaks.LOGGER.info("Registering dev-only /sttest command")

            dispatcher.register(
                literal("sttest")
                    .requires { it.hasPermissionLevel(2) }
                    .then(
                        literal("harvest")
                            .then(
                                argument("pos", BlockPosArgumentType.blockPos())
                                    .executes { ctx -> harvest(ctx) }
                            )
                    )
                    .then(
                        literal("breakspeed")
                            .then(
                                argument("pos", BlockPosArgumentType.blockPos())
                                    .executes { ctx -> breakSpeed(ctx) }
                            )
                    )
                    .then(
                        literal("enchant")
                            .then(
                                argument("pos", BlockPosArgumentType.blockPos())
                                    .executes { ctx -> enchant(ctx) }
                            )
                    )
            )
        }
    }

    /** Runs a real block-harvest drop pass as the executing player and reports the outcome. */
    private fun harvest(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player ?: run {
            feedback(ctx, "must be run by a player (its main hand supplies the tool)")
            return 0
        }
        val world = ctx.source.world as? ServerWorld ?: run {
            feedback(ctx, "server world only")
            return 0
        }
        val pos: BlockPos = BlockPosArgumentType.getBlockPos(ctx, "pos")
        val state = world.getBlockState(pos)
        val tool = player.mainHandStack

        val damageBefore = tool.damage
        val xpBefore = player.totalExperience

        // The real harvest entry point — this is what fires the HarvestDropsEvent seam.
        Block.dropStacks(state, world, pos, world.getBlockEntity(pos), player, tool)

        val damageAfter = player.mainHandStack.damage
        val xpAfter = player.totalExperience

        val box = Box(pos).expand(3.0)
        val drops = world.getEntitiesByClass(ItemEntity::class.java, box) { true }
            .joinToString("; ") { "${it.stack.item} x${it.stack.count}" }
            .ifEmpty { "(none)" }

        feedback(
            ctx,
            "block=${state.block} drops=[$drops] toolDamage=$damageBefore->$damageAfter xp=$xpBefore->$xpAfter"
        )
        return 1
    }

    /**
     * Evaluates break delta twice at the same position through the real mining path.
     * `delta1` only records the mining position; `delta2` is where Momentum's ramp applies.
     */
    private fun breakSpeed(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player ?: run {
            feedback(ctx, "must be run by a player (its main hand supplies the tool)")
            return 0
        }
        val world = ctx.source.world as? ServerWorld ?: run {
            feedback(ctx, "server world only")
            return 0
        }
        val pos: BlockPos = BlockPosArgumentType.getBlockPos(ctx, "pos")
        val state = world.getBlockState(pos)

        val speedPlain = player.getBlockBreakingSpeed(state)
        val delta1 = state.calcBlockBreakingDelta(player, world, pos)
        val delta2 = state.calcBlockBreakingDelta(player, world, pos)

        feedback(
            ctx,
            "block=${state.block} pos=$pos delta1=$delta1 delta2=$delta2 " +
                "rampRatio=${if (delta1 > 0f) delta2 / delta1 else 0f} plainSpeed=$speedPlain"
        )
        return 1
    }

    /**
     * Builds a **real** `EnchantmentScreenHandler` at an enchanting-table position, puts the
     * executing player's main hand into slot 0, and reports the three offers it generated.
     *
     * ## Why this is needed
     * The "already-enchanted items / enchanted books can be enchanted again" behaviour lives in
     * `EnchantmentScreenHandler#onContentChanged`, which only runs when a player opens the table and
     * the slot contents change. Carpet fake players can *open* a GUI (`use`) but cannot click a
     * button, so there is no RCON-reachable path into the offer generation. Constructing the handler
     * directly calls the same public entry point vanilla calls, which is the same technique
     * `harvest` / `breakspeed` use.
     *
     * `powers` is the value the gate controls: vanilla `isEnchantable()` zeroes all three offers for
     * an enchanted item or an enchanted book, so a non-zero reading is direct evidence that the
     * redirect took effect. Bookshelves must exist around `pos` or the power is 0 for *every* item,
     * which would make the test pass vacuously in the negative direction — the acceptance case
     * therefore also asserts a plain, unenchantable item still reads all zeros.
     */
    private fun enchant(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player ?: run {
            feedback(ctx, "must be run by a player (its main hand supplies the item)")
            return 0
        }
        val world = ctx.source.world as? ServerWorld ?: run {
            feedback(ctx, "server world only")
            return 0
        }
        val pos: BlockPos = BlockPosArgumentType.getBlockPos(ctx, "pos")

        val stack = player.mainHandStack.copy()

        // Diagnostic triple: this is what separates "the redirect never ran" from "the predicate said
        // no" from "the gate was fine and offer generation produced zero". See EnchantTableGate.
        val vanillaGate = EnchantTableGate.vanillaCanEnchant(stack)
        val mixinGate = EnchantTableGate.canEnchant(stack)
        val callsBefore = EnchantTableGate.invocations

        // A throwaway handler: never sent to a client, never registered on the player. The sync id is
        // deliberately far from 0 so it cannot collide with a real open container.
        //
        // The three offer arrays are snapshotted after EVERY step instead of being reasoned about from
        // bytecode. `enchantmentLevel` staying at -1 while the generator provably returned a non-empty
        // list is a contradiction that bytecode reading kept getting wrong (three times), so the
        // runtime values are now the source of truth.
        val stages = StringBuilder()
        val handler = EnchantmentScreenHandler(999, player.inventory, ScreenHandlerContext.create(world, pos))
        stages.append(snap("ctor", handler))

        handler.getSlot(0).setStack(stack)
        stages.append(snap("setSlot0", handler))

        handler.onContentChanged(handler.getSlot(0).inventory)
        stages.append(snap("contentChanged", handler))

        val redirectCalls = EnchantTableGate.invocations - callsBefore
        val powers = handler.enchantmentPower.joinToString(",")

        // --- generator diagnostics (B-2a step 1) ---------------------------------------------
        // `EnchantmentHelper.generateEnchantments` returns an empty list in three distinguishable
        // ways, and the observable symptom (`enchantmentId[]` stuck at -1) is identical for all of
        // them, so each input is reported explicitly:
        //   1. `stack.getItem().getEnchantability() <= 0` -> immediate empty return
        //   2. `EnchantmentTags.IN_ENCHANTING_TABLE` not resolving -> handler returns List.of()
        //   3. the candidate filter rejecting everything
        val enchantRegistry = world.registryManager.get(RegistryKeys.ENCHANTMENT)
        val tableTag = enchantRegistry.getEntryList(EnchantmentTags.IN_ENCHANTING_TABLE)
        val tagEntries = tableTag.map { it.size() }.orElse(-1)
        val enchantability = stack.item.enchantability
        val power0 = handler.enchantmentPower[0]
        val generated = if (tableTag.isPresent) {
            EnchantmentHelper.generateEnchantments(world.random, stack, power0, tableTag.get().stream())
        } else {
            emptyList()
        }
        val generatedText = generated.joinToString(";") { entry ->
            "${entry.enchantment.key.map { it.value.toString() }.orElse("?")}:${entry.level}"
        }.ifEmpty { "(empty)" }

        // Phase B: actually take the first offer, through the real public entry point the button
        // packet calls. This is what exercises "replace, not accumulate" — onButtonClick ends in the
        // unmapped apply method the mixin hooks.
        handler.getSlot(1).setStack(ItemStack(Items.LAPIS_LAZULI, 3))
        stages.append(snap("setSlot1", handler))

        player.addExperienceLevels(100)
        val clicked = handler.onButtonClick(player, 0)
        stages.append(snap("clicked", handler))
        val slot0 = EnchantTableGate.describeEnchantments(handler.getSlot(0).stack)
        val slot0Item = handler.getSlot(0).stack.item.toString()
        val mainHand = EnchantTableGate.describeEnchantments(player.mainHandStack)

        // Where did the enchanted result go? `describeEnchantments` maps both air and an unenchanted
        // item to "(none)", so the slot's ITEM ID is reported alongside, and the whole player
        // inventory is scanned for anything carrying enchantments.
        val inventoryEnchanted = buildString {
            for (i in 0 until player.inventory.size()) {
                val invStack = player.inventory.getStack(i)
                val plain = invStack.get(DataComponentTypes.ENCHANTMENTS)
                val stored = invStack.get(DataComponentTypes.STORED_ENCHANTMENTS)
                val hasPlain = plain != null && !plain.isEmpty
                val hasStored = stored != null && !stored.isEmpty
                if (hasPlain || hasStored) {
                    append("inv[$i]=${invStack.item}")
                    if (hasPlain) append("{${EnchantTableGate.describeEnchantments(invStack)}}")
                    if (hasStored) append("<${EnchantTableGate.describeStoredEnchantments(invStack)}>")
                    append(' ')
                }
            }
        }.ifEmpty { "(none)" }

        feedback(
            ctx,
            "item=${stack.item} enchantments={${EnchantTableGate.describeEnchantments(stack)}} " +
                "stored={${EnchantTableGate.describeStoredEnchantments(stack)}} " +
                "gateVanilla=$vanillaGate gateMixin=$mixinGate redirectCalls=$redirectCalls " +
                "powers=[$powers] levels=[${handler.enchantmentLevel.joinToString(",")}] " +
                "enchantability=$enchantability tagEntries=$tagEntries power0=$power0 " +
                "genSize=${generated.size} gen={$generatedText} " +
                "lapis=${handler.lapisCount} xp=${player.experienceLevel} " +
                "clicked=$clicked slot0Item=$slot0Item slot0={$slot0} mainHand={$mainHand} " +
                "inventoryEnchanted=[$inventoryEnchanted] stages=$stages"
        )
        return 1
    }

    /**
     * One-line snapshot of the handler's three offer arrays plus its seed, taken after one pipeline
     * step. `P` = `enchantmentPower`, `I` = `enchantmentId`, `L` = `enchantmentLevel` — all public
     * fields, so these are the real values vanilla would send to the client.
     */
    private fun snap(stage: String, handler: EnchantmentScreenHandler): String =
        "$stage:P[${handler.enchantmentPower.joinToString(",")}]" +
            "I[${handler.enchantmentId.joinToString(",")}]" +
            "L[${handler.enchantmentLevel.joinToString(",")}]" +
            "seed=${handler.getSeed()}|"

    private fun feedback(ctx: CommandContext<ServerCommandSource>, message: String) {
        ctx.source.sendFeedback({ Text.literal("[sttest] $message") }, false)
    }
}
