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
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import java.io.File

private const val ANNOTATION_FQN = "dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment"
private const val MOD_ID = "simple_tweaks"
private const val GENERATED_PACKAGE = "dev.firefly.simpletweaks.enchantments.generated"
private const val RESOURCE_PACKAGE = "data/$MOD_ID/enchantment"

/**
 * Where the vanilla tags this processor owns are written. Note the namespace **and** the shape differ
 * from [RESOURCE_PACKAGE]: tag files live at `data/<namespace>/tags/<registry>/`.
 */
private const val TAG_PACKAGE = "data/minecraft/tags/enchantment"

/**
 * KSP option holding the path of `tools/legacy-enchantments.json` — the 1.12.2-derived half of the
 * enchantment set, written once by `tools/gen-enchantments.ps1` and committed.
 *
 * Passed in by `build.gradle`. It is required, not optional: without it the tags could only cover the
 * `@ModEnchantment` declarations and would silently drop the other 54 enchantments.
 */
private const val LEGACY_MANIFEST_OPTION = "simpleTweaks.legacyManifest"

/**
 * One entry of that manifest. A strict whole-object match on purpose: a format change must fail loudly
 * (see `readLegacyManifest`) rather than half-parse into a shorter list.
 */
private val LEGACY_ENTRY = Regex(
    "\\{\\s*\"id\"\\s*:\\s*\"([a-z][a-z0-9_]*)\"\\s*,\\s*\"category\"\\s*:\\s*\"([a-z]+)\"\\s*," +
        "\\s*\"type\"\\s*:\\s*\"([a-z_]+)\"\\s*,\\s*\"maxLevel\"\\s*:\\s*(\\d+)\\s*\\}",
)

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
    val supportedItems: String,
    val primaryItems: String,
    val slots: List<String>,  // 枚举名，如 ["MAINHAND", "OFFHAND"]
    val handlerFqn: String,
    val handlerSimpleName: String,
    val source: KSFile?,
) {
    val constantName: String = id.uppercase()
}

/**
 * Index metadata for **one** enchantment, whichever generation declares it — this is what the two
 * sources are merged into. The tags and `CATEGORY`/`TYPE`/`MAX_LEVEL` must cover both sets, and those
 * are the only fields the two sides have in common: everything else in [Spec] is datapack-JSON detail
 * that the legacy side wrote for itself long ago.
 *
 * Both fields are stored **lowercased** regardless of origin, because the legacy half arrives that way
 * (from the 1.12.2 parse) while [Spec] carries enum constant names.
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
    private val options: Map<String, String>,
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
        val merged = merge(readLegacyManifest(), unique)

        logger.info(
            "ModEnchantment: ${annotated.size} annotated handler(s) -> ${unique.size} enchantment(s); " +
                "merged with the legacy manifest -> ${merged.size} total",
        )

        writeResources(unique)
        writeTags(merged)
        writeKotlin(unique, merged)
        return emptyList()
    }

    // ---------- the other half of the enchantment set ----------

    /**
     * Reads the 1.12.2-derived manifest named by [LEGACY_MANIFEST_OPTION].
     *
     * Every failure here is reported, never defaulted: a missing option, a missing file or a manifest
     * that does not parse would all silently shrink the tags, and a tag missing enchantments is
     * invisible until somebody notices the enchanting table offering too little.
     */
    private fun readLegacyManifest(): List<Meta> {
        val path = options[LEGACY_MANIFEST_OPTION]
        if (path.isNullOrBlank()) {
            logger.error(
                "KSP option '$LEGACY_MANIFEST_OPTION' is not set; it must point at " +
                    "tools/legacy-enchantments.json. Without it the generated tags would only cover the " +
                    "@ModEnchantment declarations.",
            )
            return emptyList()
        }
        val file = File(path)
        if (!file.isFile) {
            logger.error("'$LEGACY_MANIFEST_OPTION' points at '$path', which is not a file.")
            return emptyList()
        }
        val metas = LEGACY_ENTRY.findAll(file.readText()).map { m ->
            Meta(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4].toInt())
        }.toList()
        if (metas.isEmpty()) {
            logger.error("No entries could be read from '$path' -- has the manifest format changed?")
        }
        return metas
    }

    /**
     * The union that the tags and the index tables are built from, sorted by id.
     *
     * A clash is an error: it means `tools/gen-enchantments.ps1` failed to exclude a migrated
     * enchantment, and two definitions of one id would end up at the same jar path.
     */
    private fun merge(legacy: List<Meta>, specs: List<Spec>): List<Meta> {
        val byId = linkedMapOf<String, Meta>()
        legacy.forEach { byId[it.id] = it }
        specs.forEach { s ->
            val clash = byId.put(s.id, Meta(s.id, s.category.lowercase(), s.type.lowercase(), s.maxLevel))
            if (clash != null) {
                logger.error(
                    "id '${s.id}' is declared with @ModEnchantment but is also in the legacy manifest. " +
                        "That script is supposed to exclude @ModEnchantment ids from its outputs, so a " +
                        "leftover entry means its annotation parse failed.",
                )
            }
        }
        return byId.values.sortedBy { it.id }
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
            supportedItems = supportedItems,
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
     * The three vanilla tags this processor owns, for the **merged** set.
     *
     * KSP is the single producer of each: a tag file has exactly one producer, and only this side knows
     * both the `@ModEnchantment` declarations and the 1.12.2-derived manifest.
     *
     * `replace` is **false** for all three, deliberately. `replace: true` means "these values replace the
     * whole tag" — and while we do own these files, we do not own the tags: vanilla's own
     * `non_treasure.json` lists 35 vanilla enchantments and `in_enchanting_table.json` is nothing but a
     * reference to `#minecraft:non_treasure`. Replacing either would take the vanilla enchantments out of
     * the enchanting table.
     */
    private fun writeTags(merged: List<Meta>) {
        val deps = Dependencies(aggregating = true, *sources(emptyList()))
        val offered = merged
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
            appendLine(merged.joinToString(",\n") { "    \"$MOD_ID:${it.id}\"" })
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

    private fun writeKotlin(specs: List<Spec>, merged: List<Meta>) {
        val deps = Dependencies(aggregating = true, *sources(specs))
        codeGenerator.createNewFile(deps, GENERATED_PACKAGE, "GeneratedEnchantments")
            .bufferedWriter().use { it.write(kotlin(specs, merged)) }
    }

    private fun json(s: Spec): String = buildString {
        appendLine("{")
        appendLine("  \"anvil_cost\": ${s.anvilCost},")
        appendLine("  \"description\": {")
        appendLine("    \"translate\": \"enchantment.$MOD_ID.${s.id}\"")
        appendLine("  },")
        if (s.damagePerLevel > 0.0) {
            appendLine("  \"effects\": {")
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
            appendLine("    ]")
            appendLine("  },")
        } else {
            appendLine("  \"effects\": {},")
        }
        appendLine("  \"max_cost\": {")
        appendLine("    \"base\": ${s.maxCostBase},")
        appendLine("    \"per_level_above_first\": ${s.maxCostPerLevel}")
        appendLine("  },")
        appendLine("  \"max_level\": ${s.maxLevel},")
        appendLine("  \"min_cost\": {")
        appendLine("    \"base\": ${s.minCostBase},")
        appendLine("    \"per_level_above_first\": ${s.minCostPerLevel}")
        appendLine("  },")
        appendLine("  \"primary_items\": \"${s.primaryItems}\",")
        appendLine("  \"slots\": [${s.slots.joinToString(", ") { "\"${it.lowercase()}\"" }}],")
        appendLine("  \"supported_items\": \"${s.supportedItems}\",")
        appendLine("  \"weight\": ${s.weight}")
        appendLine("}")
    }

    private fun kotlin(specs: List<Spec>, merged: List<Meta>): String = buildString {
        appendLine("// GENERATED by ModEnchantmentProcessor from @ModEnchantment + tools/legacy-enchantments.json -- DO NOT EDIT.")
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
        // id -> category / type / max level for **every** enchantment, not just the annotated ones: this
        // file is now the only source, since `tools/gen-enchantments.ps1` stopped writing
        // `EnchantmentTiers.kt` and supplies the legacy half as a manifest instead.
        appendLine("    val CATEGORY: Map<String, EnchantCategory> = ${
            mapEntries(merged) { "\"${it.id}\" to EnchantCategory.${it.category.uppercase()}" }
        }")
        appendLine("    val TYPE: Map<String, EnchantType> = ${
            mapEntries(merged) { "\"${it.id}\" to EnchantType.${it.type.uppercase()}" }
        }")
        // Only an annotated declaration can name a colour; the legacy half keeps its category's.
        val explicitColors = specs.filter { it.color != "INHERIT" }
        appendLine("    val COLOR: Map<String, EnchantColor> = ${
            mapEntries(explicitColors) { "\"${it.id}\" to EnchantColor.${it.color}" }
        }")
        appendLine("    val MAX_LEVEL: Map<String, Int> = ${
            mapEntries(merged) { "\"${it.id}\" to ${it.maxLevel}" }
        }")
        appendLine()
        appendLine("    val HANDLERS: List<Listenable> = ${
            if (specs.isEmpty()) "emptyList()" else "listOf(${specs.joinToString(", ") { it.handlerSimpleName }})"
        }")
        appendLine("}")
    }

    private fun <T> mapEntries(items: List<T>, render: (T) -> String): String =
        if (items.isEmpty()) "emptyMap()" else "mapOf(${items.joinToString(", ", transform = render)})"
}

class ModEnchantmentProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ModEnchantmentProcessor(environment.codeGenerator, environment.logger, environment.options)
}