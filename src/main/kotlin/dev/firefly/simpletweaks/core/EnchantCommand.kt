package dev.firefly.simpletweaks.core

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.firefly.simpletweaks.compat.STLog
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
        )

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
        for (entity in targets) {
            if (entity !is LivingEntity) continue
            for (equipmentSlot in slots) {
                val stack = entity.getEquippedStack(equipmentSlot)
                if (stack.isEmpty) continue
                inspected++
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
        STLog.log("EnchantCommand") {
            "targets=${targets.size}, enchantment=$id, level=$level, slot=$slot, " +
                "changed=$changed, inspected=$inspected, outcome=${if (level <= 0) "removed" else "set"}"
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
