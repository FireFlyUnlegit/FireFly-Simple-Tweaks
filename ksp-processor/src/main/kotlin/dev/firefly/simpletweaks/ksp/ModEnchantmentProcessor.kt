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
 *
 * These three tags (`non_treasure` / `in_enchanting_table` / `tradeable`) are **vanilla** tags that
 * this mod extends, so their namespace is `minecraft`.
 */
private const val TAG_PACKAGE = "data/minecraft/tags/enchantment"

/**
 * Where **this mod's own** enchantment tags go — e.g. `exclusive_set/elements`.
 *
 * Deliberately a separate constant from [TAG_PACKAGE]: a mod-owned tag lives in this mod's namespace,
 * and using the vanilla one here wrote the set to `data/minecraft/tags/enchantment/exclusive_set/…`,
 * where every `#simple_tweaks:exclusive_set/…` reference resolved to nothing. Vanilla reads a missing
 * exclusive set as "no exclusivity", so the bug was invisible: the JSON looked right, the enchantments
 * looked right, and only the *combining* was silently unlimited.
 */
private const val MOD_TAG_PACKAGE = "data/$MOD_ID/tags/enchantment"

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
 * The "derive from the tier" sentinel, mirroring `WEIGHT_FROM_CATEGORY` /
 * `DAMAGE_PER_LEVEL_FROM_CATEGORY` in `ModEnchantment.kt` (a real weight is `>= 1` and a real damage
 * per level is `>= 0`, so a negative can only mean "derive"). Duplicated because this module cannot
 * import from the mod: it is a plain Kotlin/JVM library that reads enum *names* out of the annotations.
 */
private const val DERIVE = -1

/**
 * Tier → enchanting-table weight. `@ModEnchantment.weight` defaults to "derive from this table".
 *
 * ## History, and what the numbers mean now
 * The original curve was 1.12.2's `EnchantmentCategories#weight` (`10 - rarity * 2`, floored at 1),
 * i.e. `10/8/6/4/2/1`. The author replaced it with the **rarity-ladder** below so that every one of
 * this mod's enchantments is roughly one vanilla tier harder to roll than before: vanilla's own
 * `weight: 10` is `COMMON`, `5` is `UNCOMMON`, `2` is `LEGENDARY`-class. As a side effect the tiers
 * are now **strictly separated** (each value is unique), where the old curve had a `1/1/1` tail that
 * made MYTHIC, MYSTERY and UNIQUE indistinguishable by weight.
 *
 * | tier | before | now |
 * |---|---|---|
 * | COMMON | 10 | **8** |
 * | UNCOMMON | 8 | **5** |
 * | RARE | 6 | **3** |
 * | EPIC | 4 | **2** |
 * | LEGENDARY | 2 | **1** |
 * | MYTHIC / MYSTERY / UNIQUE | 1 | **1** |
 *
 * Measured against the generated JSONs (all 67 files, not hand-added): the mod's total weight falls
 * from **320** to **196** — COMMON 7×8=56, UNCOMMON 10×5=50, RARE 14×3=42, EPIC 12×2=24, and the 24
 * weight-1 files (13 LEGENDARY + 9 MYTHIC + 2 MYSTERY). Against vanilla's 146 that takes the mod's
 * share of any weighted draw from **69% to 57%**.
 *
 * ⚠️ **The single deviation**: the old formula yielded `0` for UNIQUE and `0` is rejected below, so
 * UNIQUE stays `1`. UNIQUE/MYSTERY are excluded from the enchanting table by category anyway, so the
 * value is descriptive only. `EnchantItemWeights` spells out the same table for call sites; the
 * processor compares every explicit `weight = ` against this copy and `warn`s on disagreement, so if
 * one changes, change both — this is the copy the build actually uses.
 *
 * ⚠️ **Weight is not the whole difficulty story.** It only biases *which* eligible enchantment wins a
 * weighted draw; it cannot stop an enchantment from being drawn, nor change the level it comes out at.
 * Those are `min_cost` (can it be offered at this power) and `max_cost` (which levels it may reach).
 */
private val TIER_WEIGHTS = mapOf(
    "UNIQUE" to 1,
    "COMMON" to 8,
    "UNCOMMON" to 5,
    "RARE" to 3,
    "EPIC" to 2,
    "LEGENDARY" to 1,
    "MYTHIC" to 1,
    "MYSTERY" to 1,
)

/** 1.12.2 rarity per category, from `EnchantmentCategories` in the reference project. */
private val TIER_RARITY = mapOf(
    "UNIQUE" to 0,
    "COMMON" to 0,
    "UNCOMMON" to 1,
    "RARE" to 2,
    "EPIC" to 3,
    "LEGENDARY" to 4,
    "MYTHIC" to 5,
    "MYSTERY" to 6,
)

private fun tierWeight(category: String): Int = TIER_WEIGHTS[category] ?: 1

/**
 * The derived `damagePerLevel`, **only for SWORD types** — every other type gets `0.0` (the reference
 * project's base `itemExtraDamage` returned `0.0` for them too).
 *
 * The curve is `rarity * 1.5 / 20`, i.e. `UNIQUE 0 / COMMON 0 / UNCOMMON 0.075 / RARE 0.15 /
 * EPIC 0.225 / LEGENDARY 0.3 / MYTHIC 0.375 / MYSTERY 0.45`.
 *
 * ⚠️ **This is deliberately NOT the reference project's formula.** 1.12.2 used `0.2 + rarity / 20`
 * (COMMON 0.2 … MYTHIC 0.45), which the author replaced with this curve in `build=cleanup21` as a
 * rebalance: low tiers lose most of their flat damage and COMMON now adds none at all. The 1.12.2
 * provenance of every *override* below still holds (they are the six `itemExtraDamage` overrides plus
 * explicit opt-outs) — only the derived default changed. Keep this comment and the function together:
 * a comment describing a formula the code no longer implements is how this project's earlier drift
 * started.
 *
 * A value below 0 means "derive" (the annotation's sentinel), which is why [TIER_RARITY] never has to
 * express "no damage": the type does.
 */
private fun tierDamagePerLevel(category: String, type: String): Double {
    if (type != "SWORD") return 0.0
    val rarity = TIER_RARITY[category] ?: 0
    return (rarity * 1.5) / 20.0
}

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
    /**
     * `exclusive_set` as it will be written into the JSON, or `""` for "no key at all".
     *
     * Already normalised to the `#namespace:path` form by [ModEnchantmentProcessor.normaliseExclusiveSet],
     * so the JSON writer never has to guess whether the author typed the `#`.
     */
    val exclusiveSet: String,
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

    /**
     * Declarations whose explicit `weight` / `damagePerLevel` disagree with their tier's derived value,
     * rendered as `field: Handler=value (tier CATEGORY=derived)`. Reported once per build so that an
     * override is visible in the build log instead of being discovered in game.
     */
    private val overrides = mutableListOf<String>()

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

        // Deliberate overrides are fine, invisible ones are not: see `overrides`.
        // `warn`, not `info`: KSP's info output is swallowed by a plain `gradlew build` (no `-i`), and a
        // report nobody sees cannot catch the drift it exists for.
        if (overrides.isNotEmpty()) {
            logger.warn(
                "ModEnchantment: ${overrides.size} explicit value(s) differ from the tier default " +
                    "(deliberate overrides) -> " + overrides.joinToString("; ")
            )
        }

        // Only the declarations that asked for a generated JSON. `jsonEmit = false` still contributes the
        // key, the HANDLERS entry and the index metadata -- it just leaves the datapack file to the
        // hand-written copy already in `src/main/resources`.
        writeResources(unique.filter { it.jsonEmit })
        writeTags(metas, unique)
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
        if (maxLevel < 1) {
            logger.error("@ModEnchantment on '$where': `maxLevel` must be >= 1 (got $maxLevel)", decl)
            return null
        }

        // `weight` and `damagePerLevel` are DERIVED from `category` unless the declaration overrides
        // them. Deriving is the only way this can work at all: an annotation argument must be a
        // compile-time constant, so the call site cannot compute `10 - rarity * 2` from the category,
        // and the annotation's default is therefore a sentinel ("negative = derive").
        //
        // ⚠️ The fallbacks below must be the SENTINELS, not "1" / "0.0". KSP is not obliged to surface an
        // omitted argument at all, so a declaration that leaves `weight` out can arrive here as a missing
        // key — and a fallback of 1 would then ship weight 1 while the author expected the tier value.
        // Mirrors `WEIGHT_FROM_CATEGORY` / `DAMAGE_PER_LEVEL_FROM_CATEGORY` in `ModEnchantment.kt`.
        val declaredWeight = number("weight", DERIVE)
        val derivedWeight = tierWeight(category!!)
        val weight = if (declaredWeight < 0) derivedWeight else declaredWeight
        if (weight < 1) {
            logger.error("@ModEnchantment on '$where': `weight` must be >= 1 (got $weight)", decl)
            return null
        }

        val declaredDamage = (args["damagePerLevel"]?.value as? Number)?.toDouble() ?: DERIVE.toDouble()
        val derivedDamage = tierDamagePerLevel(category, type!!)
        val damagePerLevel = if (declaredDamage < 0.0) derivedDamage else declaredDamage

        // Drift report: everything below compares what the author wrote against what the tier says.
        // Reported at info level because an override is legitimate — the point is that an *accidental*
        // disagreement (the kind that silently changes enchanting-table odds or weapon damage) cannot
        // hide. `build=cleanup21` exists because it did: see DEVLOG.md.
        if (declaredWeight >= 0 && declaredWeight != derivedWeight) {
            overrides += "weight: $where=${declaredWeight} (tier $category=$derivedWeight)"
        }
        if (declaredDamage >= 0.0 && declaredDamage != derivedDamage) {
            overrides += "damagePerLevel: $where=${declaredDamage} (tier $category=$derivedDamage)"
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
            exclusiveSet = normaliseExclusiveSet(text("exclusiveSet"), where, decl),
            damagePerLevel = damagePerLevel,
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

    /**
     * Normalises `@ModEnchantment.exclusiveSet` into the `#namespace:path` form the JSON needs.
     *
     * Accepts `""`/`null` (no key), `#ns:path` (canonical) and `ns:path` (the `#` is added). Anything
     * else — an empty namespace, a bare path with no namespace — is an error on the declaration rather
     * than a silently wrong tag reference: a typo'd `exclusiveSet` would emit a key pointing at a tag
     * that does not exist, and vanilla treats a missing exclusive tag as "no exclusivity", i.e. the
     * exclusivity would stop working with **no** build-time or runtime signal.
     */
    private fun normaliseExclusiveSet(raw: String?, where: String, decl: KSClassDeclaration): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val withoutHash = trimmed.removePrefix("#")
        if (!RESOURCE_LOCATION.matches(withoutHash)) {
            logger.error(
                "@ModEnchantment on '$where': `exclusiveSet` must be a namespaced id such as " +
                    "'#simple_tweaks:exclusive_set/elements' (read: '$trimmed'). A missing namespace " +
                    "here would point at a tag that does not exist, which vanilla reads as " +
                    "'no exclusivity'.",
                decl,
            )
            return ""
        }
        return "#$withoutHash"
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
    private fun writeTags(metas: List<Meta>, specsForExclusiveSets: List<Spec>) {
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

        writeExclusiveSets(specsForExclusiveSets)
    }

    /**
     * Writes one tag file per `exclusive_set` **owned by this mod**, listing every declaration that
     * references it.
     *
     * ## Why this is generated rather than hand-written
     * `exclusive_set` names a tag, and vanilla's own sets are tags too (`#minecraft:exclusive_set/damage`).
     * For a mod-owned set, the membership is nothing but "the enchantments that said they are in it", so
     * deriving it means a new element cannot be forgotten: adding `nature_conversion` with
     * `exclusiveSet = "#simple_tweaks:exclusive_set/elements"` puts it in the tag automatically, and a
     * hand-maintained list is exactly the kind of table `AGENTS.md` §4.10 has been bitten by twice.
     *
     * ## Path
     * `data/<ns>/tags/enchantment/exclusive_set/<name>.json` — note the `tags/` segment. Putting it at
     * `data/<ns>/exclusive_set/…` would make it a registry entry, which fails to parse (the same
     * directory mistake that broke startup with `magic_damage`, `DEV_GUIDE` §6 #29).
     *
     * ## Scope
     * Only sets whose namespace is **this mod's** are generated. A declaration may legally point at
     * `#minecraft:exclusive_set/damage` — vanilla owns that file, and writing it here would either
     * collide or overwrite vanilla's contents.
     */
    private fun writeExclusiveSets(specs: List<Spec>) {
        val deps = Dependencies(aggregating = true, *sources(specs))
        val owned = specs
            .filter { it.exclusiveSet.startsWith("#$MOD_ID:") }
            .groupBy { it.exclusiveSet.removePrefix("#$MOD_ID:") }

        for ((path, members) in owned) {
            // `exclusive_set/elements` -> directory `data/simple_tweaks/tags/enchantment/exclusive_set`,
            // file `elements`. The namespace segment is **this mod's**, not `minecraft` — reusing
            // `TAG_PACKAGE` here wrote the tag to `data/minecraft/tags/enchantment/exclusive_set/…`,
            // where the enchantment's `#simple_tweaks:exclusive_set/elements` reference resolves to
            // nothing and vanilla silently treats the enchantment as having no exclusive set at all.
            val segments = path.split("/")
            val directory = (MOD_TAG_PACKAGE + "/" + segments.dropLast(1).joinToString("/")).trimEnd('/')
            val name = segments.last()
            val text = buildString {
                appendLine("{")
                appendLine("  \"replace\": false,")
                appendLine("  \"values\": [")
                appendLine(members.map { it.id }.sorted().joinToString(",\n") { "    \"$MOD_ID:$it\"" })
                appendLine("  ]")
                appendLine("}")
            }
            codeGenerator.createNewFile(deps, directory, name, "json")
                .bufferedWriter().use { it.write(text) }
            logger.info("Exclusive set '#$MOD_ID:$path': ${members.size} member(s) at $directory/$name.json")
        }
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
        // Sorted-key order in vanilla's own files puts `exclusive_set` right after `effects`, which is
        // where this lands too (jsonEffects ran just above). Omitted entirely when blank, so the 67
        // pre-existing files stay byte-identical.
        if (s.exclusiveSet.isNotEmpty()) {
            appendLine("  \"exclusive_set\": \"${s.exclusiveSet}\",")
        }
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
        // Which item tag an enchantment declares, and which conflict set it belongs to. Both are needed at
        // run time by `reroll` (it must roll *applicable* enchantments and must not build an illegal
        // combination), and the processor already knows both -- generating them here is the only way to
        // answer "may this enchantment go on this item" without hand-duplicating the tables.
        appendLine("    val SUPPORTED_TAGS: Map<String, String> = ${
            mapEntries(specs) { "\"${it.id}\" to \"${it.supportedItems}\"" }
        }")
        appendLine("    val EXCLUSIVE_SETS: Map<String, String> = ${
            mapEntries(specs.filter { it.exclusiveSet.isNotEmpty() }) { "\"${it.id}\" to \"${it.exclusiveSet}\"" }
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
        appendLine()
        // Element enchantments are looked up by CONVENTION (`<element>_conversion` / `<element>_protection`)
        // rather than by a generated 16-entry table, so that adding a ninth element needs no processor
        // change. The lookup still goes through KEYS, so a name that does not exist answers `null`
        // instead of throwing — and a typo is therefore a silent "level 0", which is why the map is
        // also emitted as a plain id set below and checked at startup.
        appendLine("    /** Every registered enchantment id, for by-convention lookups. */")
        appendLine("    val IDS: Set<String> = KEYS.mapNotNull { it.value.path }.toSet()")
        appendLine()
        appendLine("    /**")
        appendLine("     * The key for `<element>_conversion` (or `_protection`), or `null` if the generated")
        appendLine("     * table has no such id. See `util/elements/ElementType`.")
        appendLine("     */")
        appendLine("    fun keyOfElement(elementId: String, protective: Boolean): RegistryKey<Enchantment>? {")
        appendLine("        val id = if (protective) \"\${elementId}_protection\" else \"\${elementId}_conversion\"")
        appendLine("        if (id !in IDS) return null")
        appendLine("        return of(id)")
        appendLine("    }")
        appendLine("}")
    }

    private fun <T> mapEntries(items: List<T>, render: (T) -> String): String =
        if (items.isEmpty()) "emptyMap()" else "mapOf(${items.joinToString(", ", transform = render)})"
}

class ModEnchantmentProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ModEnchantmentProcessor(environment.codeGenerator, environment.logger)
}