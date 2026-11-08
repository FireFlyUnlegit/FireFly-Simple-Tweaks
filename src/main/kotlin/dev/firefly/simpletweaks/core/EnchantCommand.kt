package dev.firefly.simpletweaks.core

import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.firefly.simpletweaks.core.config.ConfigBounds
import dev.firefly.simpletweaks.core.config.SimpleTweaksConfig
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.mobs.MobLevels
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Formatting
import net.minecraft.text.Text

/**
 * `/simple_tweaks enchant <targets> <enchantment> <level> [slot]` — an unrestricted replacement for
 * vanilla `/enchant`, meant for testing the mod's own enchantments.
 *
 * <h2>What it does differently from `/enchant`</h2>
 * 1. **`level = 0` removes the enchantment.** Vanilla cannot express this: its level argument is
 *    `IntegerArgumentType.integer(1)`, so 0 is rejected **while parsing**, before any handler runs.
 *    Building our own command is what makes the "remove" form possible without the two mixins the
 *    1.12.2 `/enchant ... 0` would have needed.
 * 2. **No max-level check.** `Enchantment#getMaxLevel` is never consulted, so level 99 is accepted.
 *    This is safe to store: `ItemEnchantmentsComponent.Builder` does not clamp either (verified — it
 *    contains no reference to `getMaxLevel`), it is a plain put into an int map.
 * 3. **No `supported_items` check.** Vanilla refuses an enchantment the item cannot carry; here any
 *    item takes any enchantment, which is what makes it usable for cross-testing.
 * 4. **The level is *set*, not added.** A second run replaces the previous level instead of stacking.
 * 5. **A slot can be chosen**, so armour enchantments no longer require holding the piece.
 *
 * <h2>Why the command is `/simple_tweaks enchant` and not `/simple_tweaks:enchant`</h2>
 * A colon cannot appear in a Brigadier literal. `StringReader#isAllowedInUnquotedString` accepts only
 * `[0-9A-Za-z]`, `_`, `-`, `.` and `+` — verified from brigadier-1.3.10 bytecode, where the compared
 * values are 48..57, 65..90, 97..122, 95, 45, 46 and 43, and **58 (`:`) is absent** — and
 * `CommandManager` only strips a leading `/`. A literal containing `:` would therefore be unreachable.
 * The root literal plus subcommand is the closest parseable equivalent.
 *
 * <h2>Why this is a server command</h2>
 * It mutates item components, which must happen on the side that owns the inventory — the server,
 * including the integrated server of a singleplayer world.
 *
 * <p>**Every** command of this mod is a server command, including the two that only open client-only
 * screens. A name registered client-side claims its whole root and makes the server's children
 * unreachable, which is not a theoretical concern: it broke `/simple_tweaks enchant` in a real client
 * log. See [CommandRoots] and [ScreenCommands].
 *
 * <p>This file owns the shared tree builder ([tree]) for both roots; the screen subcommands are
 * attached to it by [ScreenCommands.attach].
 */
object EnchantCommand {

    /** Slot aliases, spelled like the enchantment JSON's own `slots` values. */
    private val SLOT_ALIASES: Map<String, List<EquipmentSlot>> = linkedMapOf(
        "mainhand" to listOf(EquipmentSlot.MAINHAND),
        "offhand" to listOf(EquipmentSlot.OFFHAND),
        "hand" to listOf(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND),
        "head" to listOf(EquipmentSlot.HEAD),
        "chest" to listOf(EquipmentSlot.CHEST),
        "legs" to listOf(EquipmentSlot.LEGS),
        "feet" to listOf(EquipmentSlot.FEET),
        "armor" to listOf(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        ),
        "body" to listOf(EquipmentSlot.BODY),
        "any" to listOf(
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        ),
    )

    private const val DEFAULT_SLOT = "mainhand"

    // All user-facing text goes through the lang files (`commands.simple_tweaks.*`).
    private const val KEY_ROOT_USAGE = "commands.simple_tweaks.usage"
    private const val KEY_USAGE = "commands.simple_tweaks.enchant.usage"
    private const val KEY_SET = "commands.simple_tweaks.enchant.set"
    private const val KEY_REMOVED = "commands.simple_tweaks.enchant.removed"
    private const val KEY_BAD_SLOT = "commands.simple_tweaks.enchant.bad_slot"
    private const val KEY_NO_ITEM = "commands.simple_tweaks.enchant.no_item"
    private const val KEY_UNSUPPORTED = "commands.simple_tweaks.enchant.unsupported_item"
    private const val KEY_DIFFICULTY = "commands.simple_tweaks.difficulty"

    /**
     * Two arguments, hence `Dynamic2...`: the offending name **and** the valid list. The list is
     * passed in rather than spelled out in the lang file so that [SLOT_ALIASES] stays its only
     * definition.
     */
    private val BAD_SLOT = Dynamic2CommandExceptionType { name, valid ->
        Text.translatable(KEY_BAD_SLOT, name, valid)
    }

    private val NO_ITEM = SimpleCommandExceptionType(Text.translatable(KEY_NO_ITEM))

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
            for (root in CommandRoots.ALL) {
                dispatcher.register(tree(root, registryAccess))
            }
        }
    }

    /**
     * `<root> enchant <targets> <enchantment> <level> [slot]`, plus a bare `<root>` that prints the
     * usage line.
     *
     * The permission requirement deliberately sits on the `enchant` node and **not** on [root]: a
     * requirement on a literal that the client tree also registers would be silently dropped during
     * Brigadier's merge, turning a permission check into a no-op. See [CommandRoots].
     */
    private fun tree(
        root: String,
        registryAccess: CommandRegistryAccess,
    ): LiteralArgumentBuilder<ServerCommandSource> =
        // The whole tree is built here, server-side, and the two screen subcommands are attached by
        // ScreenCommands. Nothing is registered client-side any more -- that is what made the server's
        // children unreachable. See CommandRoots and ScreenCommands.
        ScreenCommands.attach(
            CommandManager.literal(root)
                .executes { ctx ->
                    ctx.source.sendError(Text.translatable(KEY_ROOT_USAGE))
                    0
                }
                .then(
                    CommandManager.literal("enchant")
                        .requires { it.hasPermissionLevel(2) }
                        // Bare `<root> enchant` prints the syntax rather than a parse error.
                        .executes { ctx ->
                            ctx.source.sendError(Text.translatable(KEY_USAGE))
                            0
                        }
                        .then(
                            CommandManager.argument("targets", EntityArgumentType.entities())
                                .then(
                                    CommandManager.argument(
                                        "enchantment",
                                        RegistryEntryReferenceArgumentType.registryEntry(
                                            registryAccess, RegistryKeys.ENCHANTMENT,
                                        ),
                                    )
                                        .then(
                                            CommandManager.argument("level", IntegerArgumentType.integer(0))
                                                .executes { ctx -> apply(ctx, DEFAULT_SLOT) }
                                                .then(
                                                    CommandManager.argument("slot", StringArgumentType.word())
                                                        .suggests { _, builder ->
                                                            val prefix = builder.remaining.lowercase()
                                                            SLOT_ALIASES.keys
                                                                .filter { it.startsWith(prefix) }
                                                                .forEach { builder.suggest(it) }
                                                            builder.buildFuture()
                                                        }
                                                        .executes { ctx ->
                                                            apply(ctx, StringArgumentType.getString(ctx, "slot"))
                                                        }
                                                )
                                        )
                                )
                        )
                )
                .then(difficultyNode())
            .then(DebugItemCommand.node())
        )

    /**
     * `<root> difficulty [<offset>|reset]` — a **session-only** difficulty offset for the mob-enhancement
     * system.
     *
     * ## Why this exists
     * Vanilla's `clampedLocalDifficulty` is ~`0.13` near spawn and in freshly loaded chunks, so the level
     * (and both probabilities) sit at their floor exactly where a player spends most of their time. Tuning
     * by "go find a long-inhabited hard-difficulty chunk" is not a usable loop, so this command lets the
     * author dial the number directly and see level / enchant / element all respond at once.
     *
     * It writes [MobLevels.difficultyOffset], which `MobLevels.effectiveDifficulty` adds to every mob's
     * base. Deliberately **not persisted**: a restart clears it, which is what a tuning aid should do.
     *
     * `0` is legal and means "no offset", so the same argument doubles as the reset.
     */
    private fun difficultyNode(): LiteralArgumentBuilder<ServerCommandSource> =
        CommandManager.literal("difficulty")
            .requires { it.hasPermissionLevel(2) }
            .executes { ctx -> reportDifficulty(ctx.source) }
            .then(
                CommandManager.literal("reset")
                    .executes { ctx ->
                        MobLevels.difficultyOffset = 0f
                        reportDifficulty(ctx.source)
                    }
            )
            .then(
                CommandManager.argument(
                "offset",
                FloatArgumentType.floatArg(0f, ConfigBounds.DIFFICULTY_MODIFIER.endInclusive.toFloat()),
            )
                    .executes { ctx ->
                        MobLevels.difficultyOffset =
                            FloatArgumentType.getFloat(ctx, "offset")
                        reportDifficulty(ctx.source)
                    }
            )
            // ---- cleanup27: persisted knob + a report of the *computed* number ----------------
            .then(
                CommandManager.literal("check")
                    .executes { ctx -> reportComputedDifficulty(ctx.source) }
            )
            .then(
                CommandManager.literal("modify")
                    .executes { ctx -> reportComputedDifficulty(ctx.source) }
                    // `modify <delta>` **adds** to the persistent modifier; negative values are legal, so
                    // the same node both raises and lowers it.
                    .then(
                        // The per-call delta used to be clamped to +-100 while the stored value allows the full
        // `ConfigBounds.DIFFICULTY_MODIFIER` range, so a large value could only be reached a hundred at a
        // time. The argument now spans the same bounds as the store (`build=cleanup65`).
        CommandManager.argument(
            "delta",
            FloatArgumentType.floatArg(
                ConfigBounds.DIFFICULTY_MODIFIER.start.toFloat(),
                ConfigBounds.DIFFICULTY_MODIFIER.endInclusive.toFloat(),
            ),
        )
                            .executes { ctx ->
                                val delta = FloatArgumentType.getFloat(ctx, "delta")
                                MobEnhancementConfig.difficultyModifier =
                                    (MobEnhancementConfig.difficultyModifier + delta)
                                        .coerceIn(
                                            ConfigBounds.DIFFICULTY_MODIFIER.start.toFloat(),
                                            ConfigBounds.DIFFICULTY_MODIFIER.endInclusive.toFloat(),
                                        )
                                SimpleTweaksConfig.save()
                                reportComputedDifficulty(ctx.source)
                            }
                    )
                    .then(
                        CommandManager.literal("clear")
                            .executes { ctx ->
                                MobEnhancementConfig.difficultyModifier = 0f
                                SimpleTweaksConfig.save()
                                reportComputedDifficulty(ctx.source)
                            }
                    )
            )
            .then(
                CommandManager.literal("show")
                    .executes { ctx -> reportComputedDifficulty(ctx.source) }
            )

    /**
     * Prints the current offset **and what it maps to**, because the offset alone does not tell the author
     * whether the value they picked is interesting: `level = 1 + floor(difficulty / 0.30)` is the thing
     * they are actually tuning. The persistent `/fst difficulty modify` value is printed next to it so
     * the two knobs can never be confused for one another.
     */
    private fun reportDifficulty(source: ServerCommandSource): Int {
        val offset = MobLevels.difficultyOffset
        val modifier = MobEnhancementConfig.difficultyModifier
        val level = MobLevels.averageLevelFor((offset + modifier).coerceAtLeast(0f))
        source.sendFeedback(
            {
                Text.literal(
                    "[FST] session offset=${"%.2f".format(offset)} " +
                        "persistent modify=${"%.2f".format(modifier)} " +
                        "(sum -> level $level; `/fst difficulty check` shows the full computed number)"
                )
            },
            false,
        )
        return level
    }

    /**
     * The **computed** number a mob would spawn with right here, which is the only figure that answers
     * "is my difficulty actually doing anything".
     *
     * Reports every term separately, because a single total cannot tell the author whether the world
     * term, their persistent `modify`, or their gear is doing the work:
     *
     * ```text
     * localDifficulty=0.13  base=0.00  offset=0.00  modify=2.00  playerPower=4.20 -> effective=10.40  level=35
     * ```
     */
    private fun reportComputedDifficulty(source: ServerCommandSource): Int {
        val world = source.world
        val pos = source.player?.blockPos ?: net.minecraft.util.math.BlockPos.ORIGIN
        val effective = MobLevels.previewDifficulty(world, pos, source.player)
        val level = MobLevels.averageLevelFor(effective)
        val local = world.getLocalDifficulty(pos).localDifficulty
        val base = MobLevels.baseDifficulty(local)
        val power = effective - base
        source.sendFeedback(
            {
                Text.literal(
                    "[FST] localDifficulty=${"%.2f".format(local)} base=${"%.2f".format(base)} " +
                        "offset=${"%.2f".format(MobLevels.difficultyOffset)} " +
                        "modify=${"%.2f".format(MobEnhancementConfig.difficultyModifier)} " +
                        "playerPower=${"%.2f".format(power)} -> effective=${"%.2f".format(effective)} " +
                        "level=$level"
                )
            },
            false,
        )
        return level
    }

    private fun apply(ctx: CommandContext<ServerCommandSource>, slotName: String): Int {
        val source = ctx.source
        val slot = slotName.lowercase()
        val slots = SLOT_ALIASES[slot]
            ?: throw BAD_SLOT.create(slotName, SLOT_ALIASES.keys.joinToString(", "))

        val targets = EntityArgumentType.getEntities(ctx, "targets")
        val entry = RegistryEntryReferenceArgumentType.getEnchantment(ctx, "enchantment")
        val level = IntegerArgumentType.getInteger(ctx, "level")

        var inspected = 0
        var changed = 0
        var unsupported = 0
        for (entity in targets) {
            if (entity !is LivingEntity) continue
            for (equipmentSlot in slots) {
                val stack = entity.getEquippedStack(equipmentSlot)
                if (stack.isEmpty) continue
                inspected++
                // `build=cleanup78`: **a chat hint when the enchantment is not supported by this item.**
                //
                // The write itself is deliberate and stays unconditional — this command is the debug /
                // tuning path and "the enchantment is applied regardless of whether the item could
                // normally carry it" is its documented behaviour (`EnchantmentHelper#set` does not check
                // `supported_items`). What is *not* acceptable is the silence: when `karma` was still
                // main-hand-only and got installed with `... karma 4 armor`, four armour pieces carried a
                // level, no handler ever matched, and the session produced zero log lines — i.e. "the
                // feature is broken" and "it was applied where it cannot act" looked identical.
                // So: write first, then tell the player.
                if (level > 0 && !entry.value().isSupportedItem(stack)) {
                    unsupported++
                }
                if (write(stack, entry, level)) changed++
            }
        }
        if (inspected == 0) throw NO_ITEM.create()

        val id = entry.key.map { it.value.toString() }.orElse("?")
        source.sendFeedback(
            {
                if (level <= 0) Text.translatable(KEY_REMOVED, id, changed, slot)
                else Text.translatable(KEY_SET, id, level, changed, slot)
            },
            true,
        )
        // Yellow feedback, not a red error: the command did exactly what it was asked to. This is a hint
        // that the item may not be one the enchantment can act on — see the loop above for why silence
        // here costs a whole test round. `broadcastToOps = false` keeps it to the caller.
        if (unsupported > 0) {
            source.sendFeedback(
                { Text.translatable(KEY_UNSUPPORTED, id, unsupported, slot).formatted(Formatting.YELLOW) },
                false,
            )
        }
        FSTLog.log("EnchantCommand") {
            "targets=${targets.size}, enchantment=$id, level=$level, slot=$slot, " +
                "changed=$changed, inspected=$inspected, unsupported=$unsupported, " +
                "outcome=${if (level <= 0) "removed" else "set"}"
        }
        return changed
    }

    /** Writes [level] of [entry] onto [stack]; returns whether anything actually changed. */
    private fun write(
        stack: ItemStack,
        entry: RegistryEntry.Reference<Enchantment>,
        level: Int,
    ): Boolean {
        // An enchanted book keeps its enchantments in STORED_ENCHANTMENTS, everything else in
        // ENCHANTMENTS. Reading or writing the wrong one is a silent no-op -- the same split
        // EnchantmentScreenHandlerMixin makes for the same reason.
        val type: ComponentType<ItemEnchantmentsComponent> =
            if (stack.isOf(Items.ENCHANTED_BOOK)) DataComponentTypes.STORED_ENCHANTMENTS
            else DataComponentTypes.ENCHANTMENTS

        val current = stack.get(type) ?: ItemEnchantmentsComponent.DEFAULT
        val builder = ItemEnchantmentsComponent.Builder(current)

        if (level <= 0) {
            if (current.getLevel(entry) <= 0) return false
            val key = entry.key.orElse(null) ?: return false
            builder.remove { it.matchesKey(key) }
        } else {
            // Deliberately no max-level and no supported_items check -- see the class KDoc.
            builder.set(entry, level)
        }

        stack.set(type, builder.build())
        return true
    }
}
