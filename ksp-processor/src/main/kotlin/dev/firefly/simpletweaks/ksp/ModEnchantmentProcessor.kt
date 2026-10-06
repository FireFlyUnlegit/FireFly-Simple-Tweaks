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

private const val ANNOTATION_FQN = "dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment"
private const val MOD_ID = "simple_tweaks"
private const val GENERATED_PACKAGE = "dev.firefly.simpletweaks.enchantments.generated"
private const val RESOURCE_PACKAGE = "data/$MOD_ID/enchantment"

private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

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

        logger.info("ModEnchantment: ${annotated.size} annotated handler(s) -> ${unique.size} enchantment(s)")

        writeResources(unique)
        writeKotlin(unique)
        return emptyList()
    }

    // ---------- reading ----------

    /** 从 KSP 的注解参数值读枚举常量名。KSP1 给 KSClassDeclaration，KSP2 给 KSType，都处理。 */
    private fun Any?.enumName(): String? = when (this) {
        is KSClassDeclaration -> simpleName.asString()
        is KSType -> declaration.simpleName.asString()
        else -> null
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
        fun number(name: String, fallback: Int): Int = (args[name]?.value as? Number)?.toInt() ?: fallback
        fun enumList(name: String): List<String> =
            (args[name]?.value as? List<*>)?.mapNotNull { it.enumName() } ?: emptyList()

        val id = text("id")
        val category = enumName("category")
        val type = enumName("type")
        val color = enumName("color") ?: "INHERIT"

        val problems = mutableListOf<String>()
        if (id.isNullOrBlank()) problems += "`id` is required"
        else if (!ID_PATTERN.matches(id)) problems += "`id` must match ${ID_PATTERN.pattern} (got '$id')"
        if (category.isNullOrBlank()) problems += "`category` is required"
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

        val slots = enumList("slots")
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

    private fun writeKotlin(specs: List<Spec>) {
        val deps = Dependencies(aggregating = true, *sources(specs))
        codeGenerator.createNewFile(deps, GENERATED_PACKAGE, "GeneratedEnchantments")
            .bufferedWriter().use { it.write(kotlin(specs)) }
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

    private fun kotlin(specs: List<Spec>): String = buildString {
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
        appendLine("    val CATEGORY: Map<String, EnchantCategory> = ${
            mapEntries(specs) { "\"${it.id}\" to EnchantCategory.${it.category}" }
        }")
        appendLine("    val TYPE: Map<String, EnchantType> = ${
            mapEntries(specs) { "\"${it.id}\" to EnchantType.${it.type}" }
        }")
        val explicitColors = specs.filter { it.color != "INHERIT" }
        appendLine("    val COLOR: Map<String, EnchantColor> = ${
            mapEntries(explicitColors) { "\"${it.id}\" to EnchantColor.${it.color}" }
        }")
        appendLine("    val MAX_LEVEL: Map<String, Int> = ${
            mapEntries(specs) { "\"${it.id}\" to ${it.maxLevel}" }
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
        ModEnchantmentProcessor(environment.codeGenerator, environment.logger)
}