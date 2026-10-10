package dev.firefly.simpletweaks.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType

private const val ANNOTATION_FQN = "dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment"
private const val MOD_ID = "simple_tweaks"
private const val GENERATED_PACKAGE = "dev.firefly.simpletweaks.enchantments.generated"
private const val RESOURCE_PACKAGE = "data/$MOD_ID/enchantment"

/**
 * Where the vanilla tags this processor owns are written. Note the namespace **and** the shape differ
 * from [RESOURCE_PACKAGE]: tag files live at `data/<namespace>/tags/<registry>/`.
 */
private const val TAG_PACKAGE = "data/minecraft/tags/enchantment"

/** A namespaced id such as `minecraft:generic.max_health`. */
private val RESOURCE_LOCATION = Regex("[a-z_0-9]+:[a-z_0-9_./-]+")

/** The three vanilla attribute modifier operations. */
private val ATTRIBUTE_OPERATIONS = setOf("add_value", "add_multiplied_base", "add_multiplied_total")

/**
 * Categories kept out of the enchanting table: MYSTERY and UNIQUE.
 *
 * Note this is a set of **categories**; the one excluded by *id* is [NOT_IN_ENCHANTING_TABLE_ID], and
 * the two checks are deliberately separate — folding an id into this set would compare it against
 * `Meta.category` and silently match nothing.
 */
private val NOT_IN_ENCHANTING_TABLE_CATEGORIES = setOf("mystery", "unique")

/**
 * The one enchantment excluded from the enchanting table by id.
 *
 * `infinite_power` is MYTHIC, so no category rule can cover it, and it has to be excluded because 1.12.2
 * never offered it: `MIGRATION.md` §"阶段 2 的保真取舍" records that both of its cost bounds returned
 * `Int.MAX_VALUE`, which the port renders as `min_cost = 65535`. That already makes it unofferable, but
 * resting on that side effect is the kind of "it works for a reason nobody remembers" this project keeps
 * getting bitten by.
 */
private const val NOT_IN_ENCHANTING_TABLE_ID = "infinite_power"

private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

/**
 * The shape every enum argument must have, checked by [ModEnchantmentProcessor.validEnum].
 *
 * This is not cosmetic. An annotation argument that is **not a compile-time constant** — say
 * `color = EnchantCategory.EPIC.color`, a property read — does not make KSP report an error: it hands
 * back a placeholder whose text is literally `<ERROR TYPE: ...>`. A processor that writes that out
 * produces a `.kt` file that compiles nowhere, so the failure surfaces one compilation step later as a
 * syntax error in *generated* code, pointing at nothing the author wrote. Rejecting anything that is
 * not a plain constant name turns that into an error on the declaration itself.
 */
private val ENUM_NAME_PATTERN = Regex("[A-Z][A-Z0-9_]*")

/**
 * One `minecraft:attributes` entry, read from a nested `AttributeSpec` argument.
 *
 * [tail] is derived rather than passed because the generated `id` must be unique per entry — vanilla uses
 * it to keep one item from stacking the same modifier twice — and `<enchantment>.<attribute tail>` is
 * unique by construction and legible in a `/attribute` listing.
 */
private data class AttrSpec(
    val attribute: String,
    val perLevel: Double,
    val operation: String,
) {
    /** `minecraft:generic.max_health` -> `max_health`. */
    val tail: String = attribute.substringAfterLast('.')
}

private data class Spec(
    val id: String,
    val category: String,   // 枚举名，如 "RARE"
    val type: String,       // 枚举名，如 "BOW"
    val color: String,      // 枚举名，如 "INHERIT" / "RAINBOW" / "BLUE"
    val maxLevel: Int,
    val weight: Int,
    val anvilCost: Int,
    val minCostBase: Int,
    val minCostPerLevel: Int,
    val maxCostBase: Int,
    val maxCostPerLevel: Int,
    val damagePerLevel: Double,
    val attributes: List<AttrSpec>,
    /** Registration position; `Int.MAX_VALUE` means "append last". See `@ModEnchantment.order`. */
    val order: Int,
    /** `false` = leave the datapack JSON to the hand-written file. See `@ModEnchantment.jsonEmit`. */
    val jsonEmit: Boolean,
    val supportedItems: String,
    val primaryItems: String,
    val slots: List<String>,  // 枚举名，如 ["MAINHAND", "OFFHAND"]
    val handlerFqn: String,
    val handlerSimpleName: String,
    val source: KSFile?,
) {
    val constantName: String = id.uppercase()

    /**
     * The index-metadata projection: the tags and `CATEGORY`/`TYPE`/`MAX_LEVEL` need only these four
     * fields. Both text fields are lowercased here because every consumer — the enchant index, the tier
     * colour lookup — works in lowercase, while [Spec] carries enum constant names.
     */
    fun toMeta(): Meta = Meta(id, category.lowercase(), type.lowercase(), maxLevel)
}

/**
 * Index metadata for one enchantment — everything the tags and the index tables need.
 *
 * There used to be a second source of these (a manifest written by `tools/gen-enchantments.ps1` for the
 * enchantments that were still generated rather than declared); the migration finished, so this is now a
 * plain projection of [Spec].
 */
private data class Meta(
    val id: String,
    val category: String,
    val type: String,
    val maxLevel: Int,
)

class ModEnchantmentProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var done = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done || codeGenerator.generatedFile.isNotEmpty()) return emptyList()
        done = true

        val annotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val specs = annotated.mapNotNull { spec(it) }
        val unique = reportDuplicates(specs)
        val metas = unique.map { it.toMeta() }

        logger.info("ModEnchantment: ${annotated.size} annotated handler(s) -> ${unique.size} enchantment(s)")

        // Only the declarations that asked for a generated JSON. `jsonEmit = false` still contributes the
        // key, the HANDLERS entry and the index metadata -- it just leaves the datapack file to the
        // hand-written copy already in `src/main/resources`.
        writeResources(unique.filter { it.jsonEmit })
        writeTags(metas)
        writeKotlin(unique, metas)
        return emptyList()
    }

    // ---------- reading ----------

    /** 从 KSP 的注解参数值读枚举常量名。KSP1 给 KSClassDeclaration，KSP2 给 KSType，都处理。 */
    private fun Any?.enumName(): String? = when (this) {
        is KSClassDeclaration -> simpleName.asString()
        is KSType -> declaration.simpleName.asString()
        else -> null
    }

    /**
     * Gate for an enum-typed argument: returns [raw] when it looks like an enum constant name, else
     * reports it against [decl] and returns `null`.
     *
     * Callers `?: return null`, so one bad field drops the whole declaration. That is deliberate: a
     * half-written `@ModEnchantment` must not produce a generated file at all, because a *wrong* value
     * is worse than a missing one — `EnchantColor.<ERROR TYPE: …>` only fails later, while a silently
     * mis-read `maxLevel` would compile and ship.
     */
    private fun validEnum(raw: String?, field: String, decl: KSClassDeclaration): String? {
        val where = decl.simpleName.asString()
        if (raw == null) {
            logger.error(
                "@ModEnchantment on '$where': `$field` could not be read as an enum constant. An " +
                    "annotation argument must be a compile-time constant, so an expression such as " +
                    "`EnchantCategory.EPIC.color` is not allowed — omit `color` to inherit the " +
                    "category's colour.",
                decl,
            )
            return null
        }
        if (!ENUM_NAME_PATTERN.matches(raw)) {
            logger.error(
                "@ModEnchantment on '$where': `$field` must name an enum constant matching " +
                    "${ENUM_NAME_PATTERN.pattern}, but read '$raw'. A value like this means the " +
                    "argument was not a compile-time constant.",
                decl,
            )
            return null
        }
        return raw
    }

    /**
     * Reads the nested `AttributeSpec` arguments of the `attributes` parameter.
     *
     * This is not an enum argument, so [validEnum] does not apply — but the failure mode it guards against
     * is the same, and so is the response: anything unreadable makes this return `null`, which drops the
     * whole declaration. A nested annotation built from a non-constant expression arrives as an error
     * placeholder rather than a value, and writing that out yields a JSON file that fails to parse at load
     * time, far from the line that caused it.
     */
    private fun readAttributes(raw: Any?, where: String, decl: KSClassDeclaration): List<AttrSpec>? {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: run {
            logger.error("@ModEnchantment on '$where': `attributes` is not a list.", decl)
            return null
        }
        val out = mutableListOf<AttrSpec>()
        list.forEachIndexed { i, element ->
            val annotation = element as? KSAnnotation ?: run {
                logger.error(
                    "@ModEnchantment on '$where': `attributes[$i]` is not an AttributeSpec. Each entry " +
                        "must be a compile-time constant, so it cannot come from a variable or a call.",
                    decl,
                )
                return null
            }
            val args = annotation.arguments.associateBy { it.name?.asString() }
            val attribute = args["attribute"]?.value as? String
            val perLevel = (args["perLevel"]?.value as? Number)?.toDouble()
            // Defaulted here as well as in the annotation, because KSP does not guarantee that a nested
            // annotation's omitted arguments appear at all.
            val operation = args["operation"]?.value as? String ?: "add_multiplied_base"

            if (attribute == null || !RESOURCE_LOCATION.matches(attribute)) {
                logger.error(
                    "@ModEnchantment on '$where': `attributes[$i].attribute` must be a namespaced id such " +
                        "as 'minecraft:generic.max_health' (read: '$attribute').",
                    decl,
                )
                return null
            }
            if (perLevel == null) {
                logger.error(
                    "@ModEnchantment on '$where': `attributes[$i].perLevel` could not be read as a number. " +
                        "A non-constant expression arrives as an error placeholder rather than a value.",
                    decl,
                )
                return null
            }
            if (operation !in ATTRIBUTE_OPERATIONS) {
                logger.error(
                    "@ModEnchantment on '$where': `attributes[$i].operation` must be one of " +
                        "${ATTRIBUTE_OPERATIONS.joinToString()}, but read '$operation'.",
                    decl,
                )
                return null
            }
            out += AttrSpec(attribute, perLevel, operation)
        }
        return out
    }

    private fun spec(decl: KSClassDeclaration): Spec? {
        val where = decl.simpleName.asString()

        if (decl.classKind != ClassKind.OBJECT) {
            logger.error("@ModEnchantment must annotate a Kotlin `object`; '$where' is ${decl.classKind}.", decl)
            return null
        }

        val annotation = decl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FQN
        } ?: return null

        val args = annotation.arguments.associateBy { it.name?.asString() }

        fun text(name: String): String? = args[name]?.value as? String
        fun enumName(name: String): String? = args[name]?.value.enumName()

        /**
         * Like `mapNotNull { it.enumName() }`, except that unreadable entries are kept as `null` so
         * [validEnum] can fail the declaration instead of the slot vanishing quietly.
         */
        fun enumNames(name: String): List<String?> =
            (args[name]?.value as? List<*>)?.map { it.enumName() } ?: emptyList()

        fun number(name: String, fallback: Int): Int = (args[name]?.value as? Number)?.toInt() ?: fallback

        val id = text("id")

        val problems = mutableListOf<String>()
        if (id.isNullOrBlank()) problems += "`id` is required"
        else if (!ID_PATTERN.matches(id)) problems += "`id` must match ${ID_PATTERN.pattern} (got '$id')"
        if (problems.isNotEmpty()) {
            logger.error("@ModEnchantment on '$where': ${problems.joinToString("; ")}", decl)
            return null
        }

        // Validated rather than merely read: see [validEnum] for why an unreadable enum argument has to
        // stop the declaration here (or be absent entirely) instead of being written out.
        val category = validEnum(enumName("category"), "category", decl) ?: return null
        val type = validEnum(enumName("type"), "type", decl) ?: return null
        val color = validEnum(enumName("color") ?: "INHERIT", "color", decl) ?: return null

        val maxLevel = number("maxLevel", 1)
        val weight = number("weight", 1)
        if (maxLevel < 1) {
            logger.error("@ModEnchantment on '$where': `maxLevel` must be >= 1 (got $maxLevel)", decl)
            return null
        }
        if (weight < 1) {
            logger.error("@ModEnchantment on '$where': `weight` must be >= 1 (got $weight)", decl)
            return null
        }

        val supportedItems = text("supportedItems")
        if (supportedItems.isNullOrBlank()) {
            logger.error("@ModEnchantment on '$where': `supportedItems` is required", decl)
            return null
        }

        val slots = enumNames("slots").map { validEnum(it, "slots", decl) ?: return null }
        if (slots.isEmpty()) {
            logger.error("@ModEnchantment on '$where': `slots` must list at least one slot", decl)
            return null
        }

        val attributes = readAttributes(args["attributes"]?.value, where, decl) ?: return null

        return Spec(
            id = id!!,
            category = category!!,
            type = type!!,
            color = color,
            maxLevel = maxLevel,
            weight = weight,
            anvilCost = number("anvilCost", 1),
            minCostBase = number("minCostBase", 1),
            minCostPerLevel = number("minCostPerLevel", 0),
            maxCostBase = number("maxCostBase", 65535),
            maxCostPerLevel = number("maxCostPerLevel", 0),
            damagePerLevel = (args["damagePerLevel"]?.value as? Number)?.toDouble() ?: 0.0,
            attributes = attributes,
            order = number("order", Int.MAX_VALUE),
            // Defaults to true here as well as in the annotation: an omitted argument may or may not appear
            // in `arguments`, and the wrong default would silently delete a datapack definition.
            jsonEmit = args["jsonEmit"]?.value as? Boolean ?: true,
            supportedItems = supportedItems,
            // Blank means "same as supportedItems" -- the rule `tools/gen-enchantments.ps1` applied for every
            // non-treasure enchantment. The treasure ones omit the field entirely, which is why they set
            // `jsonEmit = false` instead of trying to express it here.
            primaryItems = text("primaryItems").takeUnless { it.isNullOrBlank() } ?: supportedItems,
            slots = slots,
            handlerFqn = decl.qualifiedName?.asString() ?: where,
            handlerSimpleName = where,
            source = decl.containingFile,
        )
    }

    private fun reportDuplicates(specs: List<Spec>): List<Spec> {
        val byId = specs.groupBy { it.id }
        byId.filterValues { it.size > 1 }.forEach { (id, clashing) ->
            logger.error("Duplicate @ModEnchantment id '$id' on: ${clashing.joinToString { it.handlerFqn }}.")
        }
        return byId.values.map { it.first() }.sortedBy { it.id }
    }

    // ---------- writing ----------

    private fun sources(specs: List<Spec>): Array<KSFile> =
        specs.mapNotNull { it.source }.distinct().toTypedArray()

    private fun writeResources(specs: List<Spec>) {
        val deps = Dependencies(aggregating = true, *sources(specs))
        specs.forEach { spec ->
            codeGenerator.createNewFile(deps, RESOURCE_PACKAGE, spec.id, "json")
                .bufferedWriter().use { it.write(json(spec)) }
        }
    }

    /**
     * The three vanilla tags this processor owns, for every declared enchantment.
     *
     * `replace` is **false** for all three, deliberately. `replace: true` means "these values replace the
     * whole tag" — and while we do own these files, we do not own the tags: vanilla's own
     * `non_treasure.json` lists 35 vanilla enchantments and `in_enchanting_table.json` is nothing but a
     * reference to `#minecraft:non_treasure`. Replacing either would take the vanilla enchantments out of
     * the enchanting table.
     */
    private fun writeTags(metas: List<Meta>) {
        val deps = Dependencies(aggregating = true, *sources(emptyList()))
        val offered = metas
            .filter {
                it.category !in NOT_IN_ENCHANTING_TABLE_CATEGORIES && it.id != NOT_IN_ENCHANTING_TABLE_ID
            }
            .map { "$MOD_ID:${it.id}" }

        writeTag(deps, "non_treasure", offered)
        // Redundant with vanilla (`in_enchanting_table` is `#non_treasure`), but it states the intent and
        // keeps working should that indirection ever change.
        writeTag(deps, "in_enchanting_table", offered)

        // Villagers must sell none of them, so this one is purely a removal list — and removals are a
        // **Fabric** tag extension, not a vanilla one: 1.21.1's `TagFile` has only `entries`/`replace`,
        // and Fabric reads the key `fabric:remove`. Spelled plain `remove` it is silently ignored.
        val text = buildString {
            appendLine("{")
            appendLine("  \"replace\": false,")
            appendLine("  \"values\": [],")
            appendLine("  \"fabric:remove\": [")
            appendLine(metas.joinToString(",\n") { "    \"$MOD_ID:${it.id}\"" })
            appendLine("  ]")
            appendLine("}")
        }
        codeGenerator.createNewFile(deps, TAG_PACKAGE, "tradeable", "json")
            .bufferedWriter().use { it.write(text) }
    }

    private fun writeTag(deps: Dependencies, name: String, values: List<String>) {
        val text = buildString {
            appendLine("{")
            appendLine("  \"replace\": false,")
            appendLine("  \"values\": [")
            appendLine(values.joinToString(",\n") { "    \"$it\"" })
            appendLine("  ]")
            appendLine("}")
        }
        codeGenerator.createNewFile(deps, TAG_PACKAGE, name, "json")
            .bufferedWriter().use { it.write(text) }
    }

    private fun writeKotlin(specs: List<Spec>, metas: List<Meta>) {
        val deps = Dependencies(aggregating = true, *sources(specs))
        codeGenerator.createNewFile(deps, GENERATED_PACKAGE, "GeneratedEnchantments")
            .bufferedWriter().use { it.write(kotlin(specs, metas)) }
    }

    private fun json(s: Spec): String = buildString {
        appendLine("{")
        appendLine("  \"anvil_cost\": ${s.anvilCost},")
        appendLine("  \"description\": {")
        appendLine("    \"translate\": \"enchantment.$MOD_ID.${s.id}\"")
        appendLine("  },")
        jsonEffects(s)
        // `max_cost` / `min_cost` on one line, and the `amount` object of each attribute likewise: this is
        // the formatting of the hand-written `prismatic_blessing.json` this processor took over, kept so the
        // migration can be checked with a byte-for-byte diff rather than a structural one.
        appendLine("  \"max_cost\": { \"base\": ${s.maxCostBase}, \"per_level_above_first\": ${s.maxCostPerLevel} },")
        appendLine("  \"max_level\": ${s.maxLevel},")
        appendLine("  \"min_cost\": { \"base\": ${s.minCostBase}, \"per_level_above_first\": ${s.minCostPerLevel} },")
        appendLine("  \"primary_items\": \"${s.primaryItems}\",")
        appendLine("  \"slots\": [${s.slots.joinToString(", ") { "\"${it.lowercase()}\"" }}],")
        appendLine("  \"supported_items\": \"${s.supportedItems}\",")
        appendLine("  \"weight\": ${s.weight}")
        appendLine("}")
    }

    /**
     * The `effects` object, or `{}` when the declaration sets no effect component at all.
     *
     * `minecraft:damage` and `minecraft:attributes` are independent and can both be present — damage first,
     * matching the order `tools/gen-enchantments.ps1` used when it wrote these files by hand.
     */
    private fun StringBuilder.jsonEffects(s: Spec) {
        val damage = s.damagePerLevel > 0.0
        val attrs = s.attributes.isNotEmpty()
        if (!damage && !attrs) {
            appendLine("  \"effects\": {},")
            return
        }
        appendLine("  \"effects\": {")
        if (damage) {
            appendLine("    \"minecraft:damage\": [")
            appendLine("      {")
            appendLine("        \"effect\": {")
            appendLine("          \"type\": \"minecraft:add\",")
            appendLine("          \"value\": {")
            appendLine("            \"type\": \"minecraft:linear\",")
            appendLine("            \"base\": ${s.damagePerLevel},")
            appendLine("            \"per_level_above_first\": ${s.damagePerLevel}")
            appendLine("          }")
            appendLine("        }")
            appendLine("      }")
            appendLine("    ]${if (attrs) "," else ""}")
        }
        if (attrs) {
            appendLine("    \"minecraft:attributes\": [")
            s.attributes.forEachIndexed { i, a ->
                appendLine("      {")
                appendLine(
                    "        \"amount\": { \"type\": \"minecraft:linear\", \"base\": ${a.perLevel}, " +
                        "\"per_level_above_first\": ${a.perLevel} },",
                )
                appendLine("        \"attribute\": \"${a.attribute}\",")
                appendLine("        \"id\": \"$MOD_ID:enchantment.${s.id}.${a.tail}\",")
                appendLine("        \"operation\": \"${a.operation}\"")
                appendLine(if (i == s.attributes.lastIndex) "      }" else "      },")
            }
            appendLine("    ]")
        }
        appendLine("  },")
    }

    private fun kotlin(specs: List<Spec>, metas: List<Meta>): String = buildString {
        appendLine("// GENERATED by ModEnchantmentProcessor from @ModEnchantment -- DO NOT EDIT.")
        appendLine("package $GENERATED_PACKAGE")
        appendLine()
        appendLine("import dev.firefly.simpletweaks.SimpleTweaks")
        appendLine("import dev.firefly.simpletweaks.core.Listenable")
        appendLine("import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory")
        appendLine("import dev.firefly.simpletweaks.enchantments.annotations.EnchantColor")
        appendLine("import dev.firefly.simpletweaks.enchantments.annotations.EnchantType")
        appendLine("import net.minecraft.enchantment.Enchantment")
        appendLine("import net.minecraft.registry.RegistryKey")
        appendLine("import net.minecraft.registry.RegistryKeys")
        appendLine("import net.minecraft.util.Identifier")
        specs.map { it.handlerFqn }.distinct().sorted().forEach { appendLine("import $it") }
        appendLine()
        appendLine("object GeneratedEnchantments {")
        appendLine()
        appendLine("    private fun of(id: String): RegistryKey<Enchantment> =")
        appendLine("        RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(SimpleTweaks.MOD_ID, id))")
        appendLine()
        if (specs.isEmpty()) {
            appendLine("    // No @ModEnchantment declarations were seen in this compilation.")
        } else {
            specs.forEach { appendLine("    val ${it.constantName}: RegistryKey<Enchantment> = of(\"${it.id}\")") }
        }
        appendLine()
        val keys = if (specs.isEmpty()) "emptyList()" else "listOf(${specs.joinToString(", ") { it.constantName }})"
        appendLine("    val KEYS: List<RegistryKey<Enchantment>> = $keys")
        appendLine()
        // id -> category / type / max level. The annotations are the only source of these now:
        // `tools/gen-enchantments.ps1` is gone, so nothing else contributes index metadata.
        appendLine("    val CATEGORY: Map<String, EnchantCategory> = ${
            mapEntries(metas) { "\"${it.id}\" to EnchantCategory.${it.category.uppercase()}" }
        }")
        appendLine("    val TYPE: Map<String, EnchantType> = ${
            mapEntries(metas) { "\"${it.id}\" to EnchantType.${it.type.uppercase()}" }
        }")
        // Only a declaration can name a colour; the rest take their category's.
        val explicitColors = specs.filter { it.color != "INHERIT" }
        appendLine("    val COLOR: Map<String, EnchantColor> = ${
            mapEntries(explicitColors) { "\"${it.id}\" to EnchantColor.${it.color}" }
        }")
        appendLine("    val MAX_LEVEL: Map<String, Int> = ${
            mapEntries(metas) { "\"${it.id}\" to ${it.maxLevel}" }
        }")
        appendLine()
        // Sorted by the declaration's `order`. A handler migrated out of `EnchantmentManager.handlerList`
        // carries the position it had there, so the bus ends up dispatching in the same sequence as before
        // -- registration order is what decides among listeners sharing an EventPriority.
        //
        // HANDLER_ORDERS is emitted alongside because sorting alone cannot interleave: `handlerList` still
        // holds the handlers that are not `@ModEnchantment`-declared (e.g. `infinite_power`'s four aura
        // handlers), and the merge needs to place this list *among* them, not after them.
        val ordered = specs.sortedBy { it.order }
        appendLine("    val HANDLERS: List<Listenable> = ${
            if (ordered.isEmpty()) "emptyList()" else "listOf(${ordered.joinToString(", ") { it.handlerSimpleName }})"
        }")
        appendLine("    val HANDLER_ORDERS: List<Int> = ${
            if (ordered.isEmpty()) "emptyList()" else "listOf(${ordered.joinToString(", ") { it.order.toString() }})"
        }")
        appendLine("}")
    }

    private fun <T> mapEntries(items: List<T>, render: (T) -> String): String =
        if (items.isEmpty()) "emptyMap()" else "mapOf(${items.joinToString(", ", transform = render)})"
}

class ModEnchantmentProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ModEnchantmentProcessor(environment.codeGenerator, environment.logger)
}