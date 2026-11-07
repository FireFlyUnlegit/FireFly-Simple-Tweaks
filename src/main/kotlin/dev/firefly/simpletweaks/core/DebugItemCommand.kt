package dev.firefly.simpletweaks.core

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import java.util.UUID
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.enchantment.Enchantment
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.Registry
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * `/fst debugItem …` — inspect and edit the **held item's** data components (1.21's replacement for item
 * NBT), so the author can build test items without hand-writing `/give` strings.
 *
 * ## Why this exists
 * 1.21 deleted item NBT: everything is a **data component** now, and `minecraft:custom_data` is the one
 * component that still holds arbitrary NBT. Diagnosing "does this enchantment read my stack" therefore
 * needs two different views — the component list (typed data) and `custom_data` (free NBT) — and the
 * author asked for exactly that distinction: *"加一条 nbt 或者叫 datacomponent 然后子命令 show set add 等"*.
 *
 * ## Shape
 * ```text
 * /fst debugItem info                           item id, count, custom name, durability
 * /fst debugItem nbt show                       the whole `minecraft:custom_data` compound (SNBT)
 * /fst debugItem nbt set  <key> <snbt>          set / overwrite one key
 * /fst debugItem nbt add  <key> <snbt>          add only if the key is absent (refuses otherwise)
 * /fst debugItem nbt remove <key>               drop one key
 * /fst debugItem nbt clear                      drop the whole component
 * /fst debugItem component list                 every component id on the stack
 * /fst debugItem component get    <id>          one component, as SNBT
 * /fst debugItem component set    <id> <snbt>   decode through the component's own codec
 * /fst debugItem component remove <id>          remove one component
 * /fst debugItem name <string>                  set `custom_name`
 * /fst debugItem damage <int>                   set `damage`
 * /fst debugItem unbreakable [true|false]       set / clear `unbreakable`
 * /fst debugItem enchants show                 the held item's enchantments
 * /fst debugItem enchants copy                 remember them (per player)
 * /fst debugItem enchants paste                replace another item's enchantments with them
 * /fst debugItem enchants merge                same, but keep the target's other enchantments
 * /fst debugItem enchants set <id> <level>     one enchantment
 * /fst debugItem enchants remove <id>          drop one
 * /fst debugItem enchants clear                drop the whole component
 * ```
 *
 * ## Design notes
 * - **Server-side only.** Registered from [EnchantCommand.tree] like every other node; a client-side
 *   registration would swallow the whole root (`AGENTS.md` §4.1).
 * - **Values are raw SNBT**, parsed with `StringNbtReader`, not Brigadier argument types: 1.21 has no
 *   "any NBT element" argument (`NbtCompoundArgumentType` only accepts compounds), and the author needs
 *   `1b`, `"text"`, `[1,2]` and `{a:1}` through the same syntax.
 * - **`component set` decodes through the component's own codec**, so a typo is rejected by the game's
 *   validation instead of storing a component that cannot be read back.
 * - The held stack is the **live** stack (`player.mainHandStack`), so writing to it is what the player is
 *   holding; no copy is made and no `give` is involved.
 * - Output is truncated to [MAX_SNBT_LENGTH] characters: a custom-data blob can be arbitrarily large and
 *   a single chat line is the wrong place for 200 KB of SNBT.
 * - User-facing text is literal English on purpose: this is an OP-only debugging tool, not a feature the
 *   `lang` files should carry.
 */
object DebugItemCommand {

    private const val MAX_SNBT_LENGTH = 2000

    private const val PREFIX = "[FST-DebugItem] "

    /** Entry point, called from [EnchantCommand.tree]. */
    fun node(): LiteralArgumentBuilder<ServerCommandSource> =
        CommandManager.literal("debugItem")
            .requires { it.hasPermissionLevel(2) }
            .executes { ctx -> reportInfo(ctx) }
            .then(CommandManager.literal("info").executes { ctx -> reportInfo(ctx) })
            .then(nbtNode())
            .then(enchantsNode())
            .then(componentNode())
            .then(
                CommandManager.literal("name")
                    .then(
                        CommandManager.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val stack = heldStack(ctx) ?: return@executes 0
                                val name = StringArgumentType.getString(ctx, "name")
                                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))
                                feedback(ctx, "custom_name = \"$name\"")
                                1
                            }
                    )
            )
            .then(
                CommandManager.literal("damage")
                    .then(
                        CommandManager.argument("value", IntegerArgumentType.integer(0))
                            .executes { ctx ->
                                val stack = heldStack(ctx) ?: return@executes 0
                                val value = IntegerArgumentType.getInteger(ctx, "value")
                                stack.set(DataComponentTypes.DAMAGE, value)
                                feedback(ctx, "damage = $value (max ${stack.maxDamage})")
                                1
                            }
                    )
            )
            .then(
                CommandManager.literal("unbreakable")
                    .executes { ctx -> setUnbreakable(ctx, true) }
                    .then(CommandManager.literal("true").executes { ctx -> setUnbreakable(ctx, true) })
                    .then(CommandManager.literal("false").executes { ctx -> setUnbreakable(ctx, false) })
            )

    // ------------------------------------------------------------------ nbt (custom_data)

    private fun nbtNode(): LiteralArgumentBuilder<ServerCommandSource> =
        CommandManager.literal("nbt")
            .executes { ctx -> showNbt(ctx) }
            .then(CommandManager.literal("show").executes { ctx -> showNbt(ctx) })
            .then(CommandManager.literal("clear").executes { ctx -> clearNbt(ctx) })
            .then(
                CommandManager.literal("set")
                    .then(
                        CommandManager.argument("key", StringArgumentType.word())
                            .then(
                                CommandManager.argument("value", StringArgumentType.greedyString())
                                    .executes { ctx -> writeNbt(ctx, overwrite = true) }
                            )
                    )
            )
            .then(
                CommandManager.literal("add")
                    .then(
                        CommandManager.argument("key", StringArgumentType.word())
                            .then(
                                CommandManager.argument("value", StringArgumentType.greedyString())
                                    .executes { ctx -> writeNbt(ctx, overwrite = false) }
                            )
                    )
            )
            .then(
                CommandManager.literal("remove")
                    .then(
                        CommandManager.argument("key", StringArgumentType.word())
                            .executes { ctx ->
                                val stack = heldStack(ctx) ?: return@executes 0
                                val compound = customData(stack)
                                if (compound == null) {
                                    error(ctx, "no custom_data on this item")
                                    return@executes 0
                                }
                                val key = StringArgumentType.getString(ctx, "key")
                                if (!compound.contains(key)) {
                                    error(ctx, "key '$key' is absent")
                                    return@executes 0
                                }
                                compound.remove(key)
                                storeCustomData(stack, compound)
                                feedback(ctx, "removed '$key' -> ${snbt(compound)}")
                                1
                            }
                    )
            )

    private fun showNbt(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val compound = customData(stack)
        if (compound == null || compound.isEmpty) {
            feedback(ctx, "custom_data: (empty)")
        } else {
            feedback(ctx, "custom_data: ${snbt(compound)}")
        }
        return 1
    }

    private fun clearNbt(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        if (stack.get(DataComponentTypes.CUSTOM_DATA) == null) {
            error(ctx, "custom_data is already absent")
            return 0
        }
        stack.remove(DataComponentTypes.CUSTOM_DATA)
        feedback(ctx, "custom_data removed")
        return 1
    }

    private fun writeNbt(ctx: CommandContext<ServerCommandSource>, overwrite: Boolean): Int {
        val stack = heldStack(ctx) ?: return 0
        val key = StringArgumentType.getString(ctx, "key")
        val element = parseSnbt(ctx, StringArgumentType.getString(ctx, "value")) ?: return 0

        val compound = customData(stack) ?: NbtCompound()
        if (!overwrite && compound.contains(key)) {
            error(ctx, "key '$key' already exists (use `nbt set` to overwrite)")
            return 0
        }
        compound.put(key, element)
        storeCustomData(stack, compound)
        feedback(ctx, "custom_data.$key = ${snbt(element)}")
        return 1
    }

    // ------------------------------------------------------------------ typed components

    private fun componentNode(): LiteralArgumentBuilder<ServerCommandSource> =
        CommandManager.literal("component")
            .executes { ctx -> listComponents(ctx) }
            .then(CommandManager.literal("list").executes { ctx -> listComponents(ctx) })
            .then(
                CommandManager.literal("get")
                    .then(
                        CommandManager.argument("id", IdentifierArgumentType.identifier())
                            .executes { ctx -> getComponent(ctx) }
                    )
            )
            .then(
                CommandManager.literal("set")
                    .then(
                        CommandManager.argument("id", IdentifierArgumentType.identifier())
                            .then(
                                CommandManager.argument("value", StringArgumentType.greedyString())
                                    .executes { ctx -> setComponent(ctx) }
                            )
                    )
            )
            .then(
                CommandManager.literal("remove")
                    .then(
                        CommandManager.argument("id", IdentifierArgumentType.identifier())
                            .executes { ctx ->
                                val stack = heldStack(ctx) ?: return@executes 0
                                val type = componentType(ctx) ?: return@executes 0
                                if (stack.get(type) == null) {
                                    error(ctx, "${typeId(type)} is not present")
                                    return@executes 0
                                }
                                stack.remove(type)
                                feedback(ctx, "removed ${typeId(type)}")
                                1
                            }
                    )
            )

    private fun listComponents(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val types = stack.components.types
        if (types.isEmpty()) {
            feedback(ctx, "components: (none)")
            return 1
        }
        val ids = types.mapNotNull { Registries.DATA_COMPONENT_TYPE.getId(it)?.toString() }.sorted()
        feedback(ctx, "components (${ids.size}): ${ids.joinToString(", ")}")
        return 1
    }

    private fun getComponent(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val type = componentType(ctx) ?: return 0
        val value = stack.get(type)
        if (value == null) {
            error(ctx, "${typeId(type)} is not present")
            return 0
        }
        feedback(ctx, "${typeId(type)}: ${encodeComponent(type, value)}")
        return 1
    }

    private fun setComponent(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val type = componentType(ctx) ?: return 0
        val element = parseSnbt(ctx, StringArgumentType.getString(ctx, "value")) ?: return 0

        val decoded = decodeComponent(type, element)
        if (decoded == null) {
            error(ctx, "could not decode ${typeId(type)} from ${snbt(element)}")
            return 0
        }
        storeComponent(stack, type, decoded)
        feedback(ctx, "set ${typeId(type)} = ${snbt(element)}")
        return 1
    }

    /**
     * `ComponentType<T>` → `T`.
     *
     * The unchecked cast is unavoidable: the registry stores `ComponentType<?>`, and the whole point of
     * `component set` is to decode through the codec that lives on the type itself.
     */
    @Suppress("UNCHECKED_CAST")
    private fun decodeComponent(type: ComponentType<*>, element: NbtElement): Any? {
        val codec = (type as ComponentType<Any>).codec ?: return null
        return codec.parse(NbtOps.INSTANCE, element).result().orElse(null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun storeComponent(stack: ItemStack, type: ComponentType<*>, value: Any) {
        stack.set(type as ComponentType<Any>, value)
    }

    /** Component → SNBT through the component's own codec (the only way to render typed data). */
    @Suppress("UNCHECKED_CAST")
    private fun encodeComponent(type: ComponentType<*>, value: Any): String {
        val codec = (type as ComponentType<Any>).codec ?: return "(no codec)"
        val encoded = codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null)
        return encoded?.let { snbt(it) } ?: "(unencodable)"
    }

    // ------------------------------------------------------------------ enchantments

    /**
     * Copy / paste the **enchantment component** between held items.
     *
     * ## Why a clipboard instead of two item arguments
     * One hand can only hold one item, so "copy A onto B" is naturally two steps: copy while holding A,
     * then swap to B and paste. That also keeps the command independent of inventory-slot plumbing, and
     * therefore independent of any other mod's slot handling.
     *
     * ## Why `paste` replaces and `merge` merges
     * - `paste` makes the target's enchantments **exactly** the copied set — the mental model of "copy this
     *   item's enchants onto that one".
     * - `merge` keeps the target's own enchantments and only overrides the ids that were copied, which is
     *   the useful half when building a test item from two sources.
     *
     * **Nothing else on the target is touched**: the command writes only
     * `minecraft:enchantments`, so name / lore / custom_data / damage / attribute modifiers all survive.
     */
    private fun enchantsNode(): LiteralArgumentBuilder<ServerCommandSource> =
        CommandManager.literal("enchants")
            .executes { ctx -> showEnchants(ctx) }
            .then(CommandManager.literal("show").executes { ctx -> showEnchants(ctx) })
            .then(
                CommandManager.literal("copy")
                    .executes { ctx -> copyEnchants(ctx) }
            )
            .then(
                CommandManager.literal("paste")
                    .executes { ctx -> applyClipboard(ctx, merge = false) }
            )
            .then(
                CommandManager.literal("merge")
                    .executes { ctx -> applyClipboard(ctx, merge = true) }
            )
            .then(
                CommandManager.literal("clear")
                    .executes { ctx ->
                        val stack = heldStack(ctx) ?: return@executes 0
                        if (stack.get(DataComponentTypes.ENCHANTMENTS) == null) {
                            error(ctx, "no enchantments on this item")
                            return@executes 0
                        }
                        stack.remove(DataComponentTypes.ENCHANTMENTS)
                        feedback(ctx, "enchantments removed")
                        1
                    }
            )
            .then(
                CommandManager.literal("set")
                    .then(
                        CommandManager.argument("id", IdentifierArgumentType.identifier())
                            .then(
                                CommandManager.argument("level", IntegerArgumentType.integer(1, 255))
                                    .executes { ctx ->
                                        val stack = heldStack(ctx) ?: return@executes 0
                                        val id = IdentifierArgumentType.getIdentifier(ctx, "id")
                                        val level = IntegerArgumentType.getInteger(ctx, "level")
                                        val entry = enchantmentEntry(ctx, id) ?: return@executes 0
                                        val builder = enchantmentBuilder(stack)
                                        builder.set(entry, level)
                                        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build())
                                        feedback(ctx, "set $id = $level")
                                        1
                                    }
                            )
                    )
            )
            .then(
                CommandManager.literal("remove")
                    .then(
                        CommandManager.argument("id", IdentifierArgumentType.identifier())
                            .executes { ctx ->
                                val stack = heldStack(ctx) ?: return@executes 0
                                val id = IdentifierArgumentType.getIdentifier(ctx, "id")
                                val existing = stack.get(DataComponentTypes.ENCHANTMENTS)
                                if (existing == null || existing.isEmpty) {
                                    error(ctx, "no enchantments on this item")
                                    return@executes 0
                                }
                                val entry = existing.enchantments.firstOrNull {
                                    (it.key.map { key -> key.value }.orElse(null)) == id
                                }
                                if (entry == null) {
                                    error(ctx, "$id is not on this item")
                                    return@executes 0
                                }
                                val builder = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)
                                for (other in existing.enchantments) {
                                    if (other !== entry) builder.set(other, existing.getLevel(other))
                                }
                                stack.set(DataComponentTypes.ENCHANTMENTS, builder.build())
                                feedback(ctx, "removed $id")
                                1
                            }
                    )
            )

    private fun showEnchants(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val component = stack.get(DataComponentTypes.ENCHANTMENTS)
        if (component == null || component.isEmpty) {
            feedback(ctx, "enchantments: (none)")
            return 1
        }
        val listed = component.enchantments
            .mapNotNull { entry ->
                val id = entry.key.map { key -> key.value }.orElse(null) ?: return@mapNotNull null
                "$id=${component.getLevel(entry)}"
            }
            .sorted()
        feedback(ctx, "enchantments (${listed.size}): ${listed.joinToString(", ")}")
        return 1
    }

    private fun copyEnchants(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val player = ctx.source.player ?: return 0
        val component = stack.get(DataComponentTypes.ENCHANTMENTS)
        if (component == null || component.isEmpty) {
            error(ctx, "this item has no enchantments to copy")
            return 0
        }
        val copied = LinkedHashMap<Identifier, Int>()
        for (entry in component.enchantments) {
            val id = entry.key.map { key -> key.value }.orElse(null) ?: continue
            copied[id] = component.getLevel(entry)
        }
        CLIPBOARD[player.uuid] = copied
        feedback(ctx, "copied ${copied.size} enchantment(s): ${copied.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        return 1
    }

    private fun applyClipboard(ctx: CommandContext<ServerCommandSource>, merge: Boolean): Int {
        val stack = heldStack(ctx) ?: return 0
        val player = ctx.source.player ?: return 0
        val copied = CLIPBOARD[player.uuid]
        if (copied.isNullOrEmpty()) {
            error(ctx, "clipboard is empty — hold the source item and run `enchants copy` first")
            return 0
        }
        val builder =
            if (merge) enchantmentBuilder(stack)
            else ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)

        var applied = 0
        for ((id, level) in copied) {
            val entry = enchantmentRegistry(ctx).getEntry(id).orElse(null)
            if (entry == null) {
                error(ctx, "unknown enchantment '$id' (skipped)")
                continue
            }
            builder.set(entry, level)
            applied++
        }
        if (applied == 0) {
            error(ctx, "nothing was applied")
            return 0
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build())
        feedback(ctx, "${if (merge) "merged" else "pasted"} $applied enchantment(s); other components untouched")
        return 1
    }

    /** Builder seeded with the item's current enchantments (or an empty one). */
    private fun enchantmentBuilder(stack: ItemStack): ItemEnchantmentsComponent.Builder {
        val existing = stack.get(DataComponentTypes.ENCHANTMENTS) ?: ItemEnchantmentsComponent.DEFAULT
        return ItemEnchantmentsComponent.Builder(existing)
    }

    private fun enchantmentEntry(
        ctx: CommandContext<ServerCommandSource>,
        id: Identifier,
    ): RegistryEntry<Enchantment>? {
        val entry = enchantmentRegistry(ctx).getEntry(id).orElse(null)
        if (entry == null) {
            error(ctx, "unknown enchantment '$id'")
        }
        return entry
    }

    /**
     * The **dynamic** enchantment registry.
     *
     * 1.21 enchantments are not a static `Registries` field — they are loaded from the datapack, so the
     * only way to resolve an id to an entry is through the server's registry manager.
     */
    private fun enchantmentRegistry(ctx: CommandContext<ServerCommandSource>): Registry<Enchantment> =
        ctx.source.server.registryManager.get(RegistryKeys.ENCHANTMENT)

    /** Per-player clipboard, so two operators in one session cannot overwrite each other. */
    private val CLIPBOARD: MutableMap<UUID, Map<Identifier, Int>> = java.util.concurrent.ConcurrentHashMap()

    // ------------------------------------------------------------------ shared helpers

    private fun heldStack(ctx: CommandContext<ServerCommandSource>): ItemStack? {
        val player = ctx.source.player
        if (player == null) {
            error(ctx, "this command needs a player (it edits the held item)")
            return null
        }
        val stack = player.mainHandStack
        if (stack.isEmpty) {
            error(ctx, "main hand is empty")
            return null
        }
        return stack
    }

    private fun customData(stack: ItemStack): NbtCompound? =
        stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()

    private fun storeCustomData(stack: ItemStack, compound: NbtCompound) {
        if (compound.isEmpty) {
            stack.remove(DataComponentTypes.CUSTOM_DATA)
        } else {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound))
        }
    }

    private fun componentType(ctx: CommandContext<ServerCommandSource>): ComponentType<*>? {
        val id: Identifier = IdentifierArgumentType.getIdentifier(ctx, "id")
        val type = Registries.DATA_COMPONENT_TYPE.get(id)
        if (type == null) {
            error(ctx, "unknown component '$id' (see `component list`)")
        }
        return type
    }

    private fun typeId(type: ComponentType<*>): String =
        Registries.DATA_COMPONENT_TYPE.getId(type)?.toString() ?: "?"

    private fun parseSnbt(ctx: CommandContext<ServerCommandSource>, raw: String): NbtElement? =
        try {
            StringNbtReader.parse(raw)
        } catch (e: Exception) {
            error(ctx, "invalid SNBT: ${e.message}")
            null
        }

    private fun snbt(element: NbtElement): String {
        val text = element.toString()
        return if (text.length <= MAX_SNBT_LENGTH) {
            text
        } else {
            text.take(MAX_SNBT_LENGTH) + "… (${text.length} chars)"
        }
    }

    private fun reportInfo(ctx: CommandContext<ServerCommandSource>): Int {
        val stack = heldStack(ctx) ?: return 0
        val id = Registries.ITEM.getId(stack.item)
        val name = stack.get(DataComponentTypes.CUSTOM_NAME)?.string
        feedback(
            ctx,
            "item=$id count=${stack.count} damage=${stack.damage}/${stack.maxDamage} " +
                "name=${name ?: "(none)"} components=${stack.components.size()}",
        )
        return 1
    }

    private fun setUnbreakable(ctx: CommandContext<ServerCommandSource>, value: Boolean): Int {
        val stack = heldStack(ctx) ?: return 0
        if (value) {
            stack.set(DataComponentTypes.UNBREAKABLE, net.minecraft.component.type.UnbreakableComponent(true))
        } else {
            stack.remove(DataComponentTypes.UNBREAKABLE)
        }
        feedback(ctx, "unbreakable = $value")
        return 1
    }

    private fun feedback(ctx: CommandContext<ServerCommandSource>, message: String) {
        ctx.source.sendFeedback({ Text.literal(PREFIX + message) }, false)
    }

    private fun error(ctx: CommandContext<ServerCommandSource>, message: String) {
        ctx.source.sendError(Text.literal(PREFIX + message))
    }
}
