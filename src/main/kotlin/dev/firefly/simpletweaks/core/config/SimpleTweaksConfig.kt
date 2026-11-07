package dev.firefly.simpletweaks.core.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.firefly.simpletweaks.SimpleTweaks
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * JSON-backed replacement for Forge's annotation config system (phase 1c).
 *
 * <h2>What 1.12.2 had, and what replaces it</h2>
 * <pre>
 *   Forge:  config/simple_tweaks.cfg, written by ConfigManager from @Config annotations,
 *           two categories (GeneralSettings, DamageIndicator), reloaded on ConfigChangedEvent.
 *   1.21:   config/simple_tweaks.json, written by Gson from the values in [GeneralConfig] and
 *           [DamageIndicatorConfig], one file with two sections, loaded once at mod init.
 * </pre>
 *
 * Cloth Config was deliberately **not** added: it would be a new dependency plus a new options screen,
 * and the options screen is phase-6 work. A plain JSON file keeps this layer dependency-free and
 * diff-able, which matters more here than an in-game GUI.
 *
 * <h2>File shape</h2>
 * ```json
 * {
 *   "general":         { "disableAnvilCostLimit": true, "maxAnvilCost": 2147483647, ... },
 *   "damageIndicator": { "duration": 40, "scale": 1.2, ... }
 * }
 * ```
 * Keys are the 1.12.2 **field names**, so a field can be checked against the old source by name.
 * Unknown keys are ignored; missing keys fall back to the documented default.
 *
 * <h2>Two deliberate behaviours</h2>
 * <ol>
 *   <li><b>The file is written back on every load.</b> That normalises a hand-edited file (adds
 *       missing keys, applies Forge's old `&#64;RangeInt` / `&#64;RangeDouble` clamping) and means a user
 *       can discover every option without reading the source. Cheap: once per game start.</li>
 *   <li><b>Nothing in here can throw.</b> A malformed or unreadable file logs a warning and leaves the
 *       defaults in place — the same "bad config must not stop the mod from loading" property Forge
 *       gave, which is easy to lose when replacing it by hand.</li>
 * </ol>
 */
object SimpleTweaksConfig {

    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** `config/simple_tweaks.json` in the game directory (server or client). */
    val path: Path get() = FabricLoader.getInstance().configDir.resolve("${SimpleTweaks.MOD_ID}.json")

    /**
     * Loads the file into the two config objects, then writes the normalised result back.
     * Must run before anything reads a config value — [SimpleTweaks.onInitialize] calls it first.
     */
    fun load() {
        val root = try {
            val p = path
            if (Files.exists(p)) JsonParser.parseString(Files.readString(p)).asJsonObject else JsonObject()
        } catch (e: Exception) {
            SimpleTweaks.LOGGER.warn(
                "Could not read {} — falling back to defaults. Cause: {}", path, e.toString()
            )
            JsonObject()
        }

        readGeneral(root.objectOrEmpty("general"))
        readMobEnhancement(root.objectOrEmpty("mobEnhancement"))
        readDamageIndicator(root.objectOrEmpty("damageIndicator"))

        save()

        SimpleTweaks.LOGGER.info(
            "Config loaded from {} (anvilLimit={}, enchantTableLimit={}, damageIndicator={}, " +
                "cancelVanillaDmgIndicator={}, mobEnhance={})",
            path,
            GeneralConfig.disableAnvilCostLimit,
            GeneralConfig.disableEnchantmentTableLimit,
            DamageIndicatorConfig.enabled,
            GeneralConfig.cancelVanillaDamageIndicator,
            MobEnhancementConfig.enabled
        )
    }


    private fun readGeneral(s: JsonObject) {
        GeneralConfig.cancelVanillaDamageIndicator = s.bool("cancelVanillaDamageIndicator", true)
        GeneralConfig.disableAnvilCostLimit = s.bool("disableAnvilCostLimit", true)
        GeneralConfig.maxAnvilCost = s.int("maxAnvilCost", Int.MAX_VALUE)
            .coerceIn(ConfigBounds.ANVIL_COST)
        GeneralConfig.disableEnchantmentTableLimit = s.bool("disableEnchantmentTableLimit", true)
        GeneralConfig.maxEnchantmentPower = s.int("maxEnchantmentPower", 15)
            .coerceIn(ConfigBounds.ENCHANT_POWER)
        GeneralConfig.enabledEnchantmentColor = s.bool("enabledEnchantmentColor", true)
        GeneralConfig.enabledSpecialParticles = s.bool("enabledSpecialParticles", true)
        GeneralConfig.autoSprintEnabled = s.bool("autoSprintEnabled", false)
        GeneralConfig.autoSprintOmniSprint = s.bool("autoSprintOmniSprint", false)
        // New in cleanup21, not a 1.12.2 option — hence a `false` default rather than a ported one.
        GeneralConfig.noHurtCam = s.bool("noHurtCam", false)
    }

    /**
     * Mob-enhancement options (new section, no 1.12.2 counterpart — defaults are this port's choice).
     *
     * Both keys are read immediately after load: [MobEnhancementConfig.enabled] by the spawn-path guard
     * and [MobEnhancementConfig.enchantmentsEnabled] by `tryApplyAtSpawn`. Neither is persisted back into
     * the world, so flipping them takes effect on the next spawn without a restart.
     */
    private fun readMobEnhancement(s: JsonObject) {
        MobEnhancementConfig.enabled = s.bool("enabled", true)
        MobEnhancementConfig.enchantmentsEnabled = s.bool("enchantmentsEnabled", true)
        MobEnhancementConfig.levelsEnabled = s.bool("levelsEnabled", true)
        MobEnhancementConfig.elementsEnabled = s.bool("elementsEnabled", true)
        // cleanup27: persistent difficulty bonus (`/fst difficulty modify`). Bounded so a typo cannot
        // turn every spawn into a level-5000 mob.
        MobEnhancementConfig.difficultyModifier = s.dbl("difficultyModifier", 0.0)
            .coerceIn(ConfigBounds.DIFFICULTY_MODIFIER).toFloat()
    }

    private fun readDamageIndicator(s: JsonObject) {
        DamageIndicatorConfig.enabled = s.bool("enabled", true)
        DamageIndicatorConfig.symbol = s.bool("symbol", true)
        // Bounds copied from the 1.12.2 @RangeInt / @RangeDouble annotations.
        DamageIndicatorConfig.duration = s.int("duration", 40)
            .coerceIn(ConfigBounds.DAMAGE_DURATION)
        DamageIndicatorConfig.riseDuration = s.int("riseDuration", 10)
            .coerceIn(ConfigBounds.DAMAGE_RISE)
        DamageIndicatorConfig.maxDistance = s.int("maxDistance", 64)
            .coerceIn(ConfigBounds.DAMAGE_DISTANCE)
        DamageIndicatorConfig.scale = s.dbl("scale", 1.2).coerceIn(ConfigBounds.DAMAGE_SCALE)
        DamageIndicatorConfig.percentageMode = s.bool("percentageMode", false)
        // cleanup26: below this absolute health delta no number is spawned. 0 = no filtering, which is
        // why the default does not change any pre-existing behaviour.
        DamageIndicatorConfig.minHealthChange = s.dbl("minHealthChange", 0.0)
            .coerceIn(ConfigBounds.DAMAGE_MIN_HEALTH_CHANGE)
        DamageIndicatorConfig.splitElemental = s.bool("splitElemental", false)
        DamageIndicatorConfig.showShadow = s.bool("showShadow", true)
        DamageIndicatorConfig.yStartFactor = s.dbl("yStartFactor", 1.0)
            .coerceIn(ConfigBounds.DAMAGE_Y_START)
    }

    // ------------------------------------------------------------------ write

    /**
     * Writes the current in-memory values to [path], normalised.
     *
     * Used by [load] (which always normalises the file it just read) and by the config GUI, where a
     * widget callback calls this immediately after mutating a field — so a change is persisted even if
     * the game later crashes, and closing the screen needs no extra bookkeeping.
     */
    fun save() {
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, GSON.toJson(write(), JsonObject::class.java))
        } catch (e: Exception) {
            SimpleTweaks.LOGGER.warn("Could not write {} — values still applied. Cause: {}", path, e.toString())
        }
    }

    private fun write(): JsonObject {
        val general = JsonObject().apply {
            addProperty("cancelVanillaDamageIndicator", GeneralConfig.cancelVanillaDamageIndicator)
            addProperty("disableAnvilCostLimit", GeneralConfig.disableAnvilCostLimit)
            addProperty("maxAnvilCost", GeneralConfig.maxAnvilCost)
            addProperty("disableEnchantmentTableLimit", GeneralConfig.disableEnchantmentTableLimit)
            addProperty("maxEnchantmentPower", GeneralConfig.maxEnchantmentPower)
            addProperty("enabledEnchantmentColor", GeneralConfig.enabledEnchantmentColor)
            addProperty("enabledSpecialParticles", GeneralConfig.enabledSpecialParticles)
            addProperty("autoSprintEnabled", GeneralConfig.autoSprintEnabled)
            addProperty("autoSprintOmniSprint", GeneralConfig.autoSprintOmniSprint)
            addProperty("noHurtCam", GeneralConfig.noHurtCam)
        }
        val mobEnhancement = JsonObject().apply {
            addProperty("enabled", MobEnhancementConfig.enabled)
            addProperty("enchantmentsEnabled", MobEnhancementConfig.enchantmentsEnabled)
            addProperty("levelsEnabled", MobEnhancementConfig.levelsEnabled)
            addProperty("elementsEnabled", MobEnhancementConfig.elementsEnabled)
            addProperty("difficultyModifier", MobEnhancementConfig.difficultyModifier)
        }
        val indicator = JsonObject().apply {
            addProperty("enabled", DamageIndicatorConfig.enabled)
            addProperty("symbol", DamageIndicatorConfig.symbol)
            addProperty("duration", DamageIndicatorConfig.duration)
            addProperty("riseDuration", DamageIndicatorConfig.riseDuration)
            addProperty("maxDistance", DamageIndicatorConfig.maxDistance)
            addProperty("scale", DamageIndicatorConfig.scale)
            addProperty("percentageMode", DamageIndicatorConfig.percentageMode)
            addProperty("minHealthChange", DamageIndicatorConfig.minHealthChange)
            addProperty("splitElemental", DamageIndicatorConfig.splitElemental)
            addProperty("showShadow", DamageIndicatorConfig.showShadow)
            addProperty("yStartFactor", DamageIndicatorConfig.yStartFactor)
        }
        return JsonObject().apply {
            add("general", general)
            add("mobEnhancement", mobEnhancement)
            add("damageIndicator", indicator)
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Reads are tolerant by construction: a key of the wrong JSON type, or absent, yields [def]
     * instead of an exception. Forge's config reader behaved the same way, and a config file is
     * exactly the kind of input a user will hand-edit into an invalid state.
     */
    private fun JsonObject.objectOrEmpty(key: String): JsonObject =
        runCatching { if (has(key) && get(key).isJsonObject) getAsJsonObject(key) else JsonObject() }
            .getOrDefault(JsonObject())

    private fun JsonObject.bool(key: String, def: Boolean): Boolean =
        runCatching { if (has(key) && get(key).isJsonPrimitive && get(key).asJsonPrimitive.isBoolean) get(key).asBoolean else def }
            .getOrDefault(def)

    private fun JsonObject.int(key: String, def: Int): Int =
        runCatching { if (has(key) && get(key).isJsonPrimitive) get(key).asInt else def }.getOrDefault(def)

    private fun JsonObject.dbl(key: String, def: Double): Double =
        runCatching { if (has(key) && get(key).isJsonPrimitive) get(key).asDouble else def }.getOrDefault(def)
}
