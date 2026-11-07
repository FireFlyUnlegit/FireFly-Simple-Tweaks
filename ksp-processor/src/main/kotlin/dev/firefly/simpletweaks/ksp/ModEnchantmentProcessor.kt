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

/**
 * Turns a single `@ModEnchantment`-annotated handler into everything the data-driven 1.21 enchantment
 * needs, so adding an enchantment is one file again (the 1.12.2 feel) instead of "edit four tables
 * and hope they agree".
 *
 * ## Outputs, per annotated `object`
 *
 * | output | where | replaces |
 * |---|---|---|
 * | `<id>.json` | `resources/data/simple_tweaks/enchantment/` | hand-written datapack definition |
 * | `GeneratedEnchantments.<ID>` | `enchantments/generated/GeneratedEnchantments.kt` | `ModEnchantmentKeys` entry |
 * | `GeneratedEnchantments.KEYS` | same | the `ALL` list in `ModEnchantmentKeys` |
 * | `CATEGORY` / `TYPE` / `COLOR` / `MAX_LEVEL` maps | same | the `EnchantmentTiers` tables |
 * | `GeneratedEnchantments.HANDLERS` | same | the hand-maintained `handlerList` in `EnchantmentManager` |
 *
 * ## What is deliberately NOT generated
 * **lang.** The enchantment name and description stay hand-written
 * (`enchantment.simple_tweaks.<id>` / `.desc`), because the text is the one thing an annotation
 * cannot carry readably.
 *
 * ## Why the annotation is not defined here
 * It lives in the mod (`enchantments/annotations/ModEnchantment.kt`) so a handler file needs no
 * import from a build-only module. This processor therefore refers to it **by string** and never
 * links against the mod: see [ANNOTATION_FQN]. Consequence to remember: renaming or moving the
 * annotation silently stops this processor from seeing anything, and the only symptom is that the
 * generated file comes out empty — the self-check below is what makes that loud instead of silent.
 */
private const val ANNOTATION_FQN = "dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment"

/** Must match `SimpleTweaks.MOD_ID` / `archives_base_name`: used for the datapack path and lang key. */
private const val MOD_ID = "simple_tweaks"

private const val GENERATED_PACKAGE = "dev.firefly.simpletweaks.enchantments.generated"

/**
 * `createNewFile`'s `packageName` becomes a *directory* prefix. KSP routes any extension that is not
 * `kt`/`java` into the source set's **resources** output, which is what puts the datapack JSON on the
 * jar's classpath -- verified from the built jar, see `MIGRATION.md` §15.
 */
private const val RESOURCE_PACKAGE = "data/$MOD_ID/enchantment"

/** 1.12.2 `EnchantmentCategories.color`, copied from the same table `tools/gen-enchantments.ps1` used. */
private val CATEGORY_COLORS = linkedMapOf(
    "unique" to "WHITE",
    "common" to "GRAY",
    "uncommon" to "GREEN",
    "rare" to "BLUE",
    "epic" to "LIGHT_PURPLE",
    "legendary" to "GOLD",
    "mythic" to "DARK_RED",
    "mystery" to "AQUA",
)

/** Enchantment ids become file names, lang keys and Kotlin constant names, so they are checked. */
private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

private data class Spec(
    val id: String,
    val category: String,
    val type: String,
    val color: String,
    val maxLevel: Int,
    val weight: Int,
    val anvilCost: Int,
    val minCostBase: Int,
    val minCostPerLevel: Int,
    val maxCostBase: Int,
    val maxCostPerLevel: Int,
    val supportedItems: String,
    val primaryItems: String,
    val slots: List<String>,
    val handlerFqn: String,
    val handlerSimpleName: String,
    val source: KSFile?,
) {
    val constantName: String = id.uppercase()
}

class ModEnchantmentProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /**
     * Guards against generating twice.
     *
     * KSP calls `process()` once per round on the same instance, and a round that produced any file is
     * always followed by at least one more round; creating the same file again throws
     * `FileAlreadyExistsException` (observed). Both checks are kept because they fail independently:
     * [done] covers instance reuse, `generatedFile` covers an instance that was rebuilt.
     */
    private var done = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done || codeGenerator.generatedFile.isNotEmpty()) {
            return emptyList()
        }
        done = true

        val annotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        // NOTE: no `validate()`-based deferral here, deliberately -- it deadlocks on this processor.
        //
        // The usual KSP idiom is to return unvalidated symbols so that a later round sees them once
        // another processor has generated what they refer to. That idiom does not work when the
        // symbol refers to **this processor's own output**: an annotated handler reads its key from
        // `GeneratedEnchantments.<ID>`, so `validate()` reports it as unresolvable on every round --
        // it can never become valid before the file it needs has been written. Deferring therefore
        // generates an empty file in round 1 and throws in round 2.
        //
        // Reading the annotation's arguments needs no resolution of the class body, so the symbols are
        // taken as they are; anything genuinely broken is the Kotlin compiler's to report.
        val specs = annotated.mapNotNull { spec(it) }
        val unique = reportDuplicates(specs)

        logger.info(
            "ModEnchantment: ${annotated.size} annotated handler(s) -> ${unique.size} enchantment(s) " +
                "(1 Kotlin file + ${unique.size} datapack JSON file(s))",
        )

        // Always emit, even with nothing annotated: the mod's own code imports these declarations
        // unconditionally, so an empty file is what keeps a project with zero KSP enchantments
        // compiling.
        writeResources(unique)
        writeKotlin(unique)
        return emptyList()
    }

    // ------------------------------------------------------------------ reading @ModEnchantment

    private fun spec(decl: KSClassDeclaration): Spec? {
        val where = decl.simpleName.asString()

        if (decl.classKind != ClassKind.OBJECT) {
            logger.error(
                "@ModEnchantment must annotate a Kotlin `object`: a handler is a singleton, and the " +
                    "generated HANDLERS list needs an instance to register. '$where' is a " +
                    "${decl.classKind}.",
                decl,
            )
            return null
        }

        val annotation = decl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FQN
        } ?: return null

        // `arguments` may or may not include the parameter defaults depending on the KSP version, so
        // every optional field is read with the same default the annotation declares. Required
        // fields are simply read as null and reported below.
        val args = annotation.arguments.associateBy { it.name?.asString() }
        fun text(name: String): String? = args[name]?.value as? String
        fun number(name: String, fallback: Int): Int = args[name]?.value as? Int ?: fallback
        fun texts(name: String): List<String> = (args[name]?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val id = text("id")
        val category = text("category")
        val type = text("type")

        val problems = mutableListOf<String>()
        if (id.isNullOrBlank()) problems += "`id` is required"
        else if (!ID_PATTERN.matches(id)) problems += "`id` must match ${ID_PATTERN.pattern} (got '$id')"
        if (category.isNullOrBlank()) problems += "`category` is required"
        else if (category !in CATEGORY_COLORS) {
            problems += "`category` must be one of ${CATEGORY_COLORS.keys.joinToString()} (got '$category')"
        }
        if (type.isNullOrBlank()) problems += "`type` is required"
        if (problems.isNotEmpty()) {
            logger.error("@ModEnchantment on '$where': ${problems.joinToString("; ")}", decl)
            return null
        }

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

        val slots = texts("slots")
        if (slots.isEmpty()) {
            logger.error("@ModEnchantment on '$where': `slots` must list at least one slot", decl)
            return null
        }

        val explicitColor = text("color").orEmpty()

        return Spec(
            id = id!!,
            category = category!!,
            type = type!!,
            // An empty `color` means "use the category's 1.12.2 colour", which is the common case;
            // only the two enchantments with a bespoke colour pass it explicitly.
            color = (if (explicitColor.isBlank()) CATEGORY_COLORS.getValue(category) else explicitColor).uppercase(),
            maxLevel = maxLevel,
            weight = weight,
            anvilCost = number("anvilCost", 1),
            minCostBase = number("minCostBase", 1),
            minCostPerLevel = number("minCostPerLevel", 0),
            maxCostBase = number("maxCostBase", 65535),
            maxCostPerLevel = number("maxCostPerLevel", 0),
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
            logger.error(
                "Duplicate @ModEnchantment id '$id' on: ${clashing.joinToString { it.handlerFqn }}. " +
                    "The id is the JSON file name, the lang key and the registry path -- it must be unique.",
            )
        }
        return byId.values.map { it.first() }.sortedBy { it.id }
    }

    // ------------------------------------------------------------------ emitted files

    private fun sources(specs: List<Spec>): Array<KSFile> = specs.mapNotNull { it.source }.distinct().toTypedArray()

    private fun writeResources(specs: List<Spec>) {
        val deps = Dependencies(aggregating = false, *sources(specs))
        specs.forEach { spec ->
            codeGenerator.createNewFile(deps, RESOURCE_PACKAGE, spec.id, "json")
                .bufferedWriter()
                .use { it.write(json(spec)) }
        }
    }

    private fun writeKotlin(specs: List<Spec>) {
        val deps = Dependencies(aggregating = false, *sources(specs))
        codeGenerator.createNewFile(deps, GENERATED_PACKAGE, "GeneratedEnchantments")
            .bufferedWriter()
            .use { it.write(kotlin(specs)) }
    }

    /**
     * The datapack definition. Key order matches what `tools/gen-enchantments.ps1` produced, so the
     * generated file is diffable against the hand-written ones it replaces.
     */
    private fun json(s: Spec): String = buildString {
        appendLine("{")
        appendLine("  \"anvil_cost\": ${s.anvilCost},")
        appendLine("  \"description\": {")
        appendLine("    \"translate\": \"enchantment.$MOD_ID.${s.id}\"")
        appendLine("  },")
        appendLine("  \"effects\": {},")
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
        appendLine("  \"slots\": [${s.slots.joinToString(", ") { "\"$it\"" }}],")
        appendLine("  \"supported_items\": \"${s.supportedItems}\",")
        appendLine("  \"weight\": ${s.weight}")
        appendLine("}")
    }

    private fun kotlin(specs: List<Spec>): String = buildString {
        appendLine("// GENERATED by ModEnchantmentProcessor from @ModEnchantment -- DO NOT EDIT.")
        appendLine("//")
        appendLine("// Adding an enchantment: write ONE file with @ModEnchantment on the handler object.")
        appendLine("// The datapack definition lands next to this file as")
        appendLine("// resources/$RESOURCE_PACKAGE/<id>.json; the lang keys stay hand-written.")
        appendLine("package $GENERATED_PACKAGE")
        appendLine()
        appendLine("import dev.firefly.simpletweaks.SimpleTweaks")
        appendLine("import dev.firefly.simpletweaks.core.Listenable")
        appendLine("import net.minecraft.enchantment.Enchantment")
        appendLine("import net.minecraft.registry.RegistryKey")
        appendLine("import net.minecraft.registry.RegistryKeys")
        appendLine("import net.minecraft.util.Identifier")
        specs.map { it.handlerFqn }.distinct().sorted().forEach { appendLine("import $it") }
        appendLine()
        appendLine("/** Keys, index metadata and handler registry for every `@ModEnchantment` declaration. */")
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
        appendLine("    /** Every KSP-declared key. Merged with the legacy table by [dev.firefly.simpletweaks.enchantments.EnchantmentMeta]. */")
        appendLine("    val KEYS: List<RegistryKey<Enchantment>> = $keys")
        appendLine()
        specs.forEach { appendLine("    /** `${it.id}`: ${it.category}, ${it.type}, max level ${it.maxLevel}. */") }
        appendLine("    val CATEGORY: Map<String, String> = ${entries(specs) { "\"${it.id}\" to \"${it.category}\"" }}")
        appendLine("    val TYPE: Map<String, String> = ${entries(specs) { "\"${it.id}\" to \"${it.type}\"" }}")
        appendLine("    val COLOR: Map<String, String> = ${entries(specs) { "\"${it.id}\" to \"${it.color}\"" }}")
        appendLine("    val MAX_LEVEL: Map<String, Int> = ${entries(specs) { "\"${it.id}\" to ${it.maxLevel}" }}")
        appendLine()
        appendLine("    /** Handlers to register on the event bus, in declaration order. */")
        appendLine("    val HANDLERS: List<Listenable> = ${handlerEntries(specs)}")
        appendLine("}")
    }

    /**
     * `emptyList()`/`emptyMap()` for the zero-declaration case: the declared types on the left-hand
     * side carry the type arguments, so no explicit type parameter is needed and an empty project
     * still compiles.
     */
    private fun entries(specs: List<Spec>, render: (Spec) -> String): String =
        if (specs.isEmpty()) "emptyMap()" else "mapOf(${specs.joinToString(", ", transform = render)})"

    private fun handlerEntries(specs: List<Spec>): String =
        if (specs.isEmpty()) "emptyList()" else "listOf(${specs.joinToString(", ") { it.handlerSimpleName }})"
}

/**
 * Registered via `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`.
 * KSP discovers processors through that file, not through an annotation.
 */
class ModEnchantmentProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ModEnchantmentProcessor(environment.codeGenerator, environment.logger)
}
