package dev.firefly.simpletweaks.gametest;

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments;
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantDevourHandler;
import dev.firefly.simpletweaks.enchantments.handlers.advanced.EnchantGreaterArmorHandler;
import dev.firefly.simpletweaks.enchantments.handlers.advanced.EnchantGreaterVitalityHandler;
import dev.firefly.simpletweaks.util.mobs.LevelledMob;
import dev.firefly.simpletweaks.util.ArcaneEcho;
import dev.firefly.simpletweaks.util.ArmorCurve;
import dev.firefly.simpletweaks.util.DotAcceleration;
import dev.firefly.simpletweaks.util.FireBurn;
import dev.firefly.simpletweaks.util.PlayerPower;
import dev.firefly.simpletweaks.util.ShotWeapon;
import dev.firefly.simpletweaks.core.AdvancedEnchantMode;
import dev.firefly.simpletweaks.util.UpgradeEnchantments;
import dev.firefly.simpletweaks.SimpleTweaks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import dev.firefly.simpletweaks.util.mobs.MobLevels;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Game tests: a real server, real entities, the real damage pipeline.
 *
 * <h2>Why this exists separately from `runTest`</h2>
 * The Gradle task `runTest` (see {@code gradle/post-build-tests.gradle}) is a <b>packaging</b> gate:
 * it never boots Minecraft, so it can check that the jar is well formed but not that the mod does
 * anything. This class is the other half — it spawns a mob, asks the game what its level and health
 * are, and deals damage through {@code LivingEntity#damage}, which is the exact seam the elemental
 * and enchantment handlers sit on.
 *
 * <h2>Why every test logs its values before asserting</h2>
 * Fabric's {@code invokeTestMethod} reports a failed assertion as the generic
 * <em>"Failed to invoke test method"</em>, which says nothing about <em>what</em> was wrong — the
 * first CI run produced three failures and no reasons. So each test logs the numbers it is about to
 * assert on (prefixed {@code [FST-GameTest]}), and the workflow prints the JUnit XML. Between the two,
 * a red run explains itself.
 *
 * <h2>How it runs</h2>
 * Only on demand: {@code ./gradlew runGametest} (the {@code gametest} run config in build.gradle) or
 * the {@code gametest} GitHub workflow, which is scheduled/manual and deliberately <b>not</b> part of
 * {@code build} — it needs a world, a server tick loop and the Minecraft EULA, none of which belong in
 * a per-push build.
 */
public class SimpleTweaksGameTests implements FabricGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("simple_tweaks/gametest");

    static {
        // If a test fails with "Failed to invoke test method", this line proves the class itself
        // initialised (as opposed to a NoClassDefFoundError / ExceptionInInitializerError).
        LOGGER.info("[FST-GameTest] test class initialised");
    }

    /**
     * KSP output is on the runtime classpath.
     *
     * <p>A Fabric Loader warning (<em>"Class path entries reference missing files:
     * build/generated/ksp/main/classes"</em>) reads like the generated enchantment table might be
     * absent at runtime. It is an empty intermediate directory that KSP registers but never writes;
     * the actual class is compiled into {@code build/classes/kotlin/main}. This test turns that
     * argument into a runtime fact instead of a code-reading opinion.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void generatedEnchantmentTableIsAvailableAtRuntime(TestContext context) {
        int keys = GeneratedEnchantments.INSTANCE.getKEYS().size();
        int categories = GeneratedEnchantments.INSTANCE.getCATEGORY().size();
        LOGGER.info("[FST-GameTest] generated table: keys={}, categories={}", keys, categories);
        context.assertEquals(169, keys, "GeneratedEnchantments.KEYS should list all 169 enchantments");
        context.assertEquals(169, categories, "GeneratedEnchantments.CATEGORY should cover every id");
        context.complete();
    }

    /**
     * `catalyze` must change the DoT <em>interval</em>, never the per-tick value, so the only thing
     * that can be asserted without a poison source is the shared factor the venom tick reads.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void dotAccelerationMultipliesTheFactorNotTheDamage(TestContext context) {
        ZombieEntity zombie = EntityType.ZOMBIE.create(context.getWorld());
        context.assertTrue(zombie != null, "could not create a zombie without spawning it");
        if (zombie == null) {
            context.complete();
            return;
        }

        double untouched = DotAcceleration.INSTANCE.factorOf(zombie);
        DotAcceleration.INSTANCE.apply(zombie, 3, 100L, null);
        double catalysed = DotAcceleration.INSTANCE.factorOf(zombie);
        // A lower level must not overwrite a higher one that is still running.
        DotAcceleration.INSTANCE.apply(zombie, 1, 100L, null);
        double afterWeakerHit = DotAcceleration.INSTANCE.factorOf(zombie);

        LOGGER.info("[FST-GameTest] dot factor: untouched={} (expect 1.0), level 3 -> {} (expect 4.0), "
                        + "then level 1 -> {} (expect 4.0)",
                untouched, catalysed, afterWeakerHit);

        context.assertTrue(Math.abs(untouched - 1.0) < 1e-6, "an untouched entity must have factor 1.0");
        context.assertTrue(Math.abs(catalysed - 4.0) < 1e-6, "level 3 must give factor 4.0, got " + catalysed);
        context.assertTrue(Math.abs(afterWeakerHit - 4.0) < 1e-6,
                "a weaker re-application must not shorten the running factor, got " + afterWeakerHit);
        context.complete();
    }

    /**
     * The poison cadence must fire on the 20th tick, not on every tick: the phase counts elapsed
     * ticks and only the interval is divided by the factor.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void venomCadenceFiresOncePerInterval(TestContext context) {
        var plain = new DotAcceleration.Cadence(20);
        int plainFires = 0;
        int firstFireTick = -1;
        for (int tick = 1; tick <= 60; tick++) {
            if (plain.advance(1.0)) {
                plainFires++;
                if (firstFireTick < 0) {
                    firstFireTick = tick;
                }
            }
        }

        var catalysed = new DotAcceleration.Cadence(20);
        int catalysedFires = 0;
        for (int tick = 1; tick <= 60; tick++) {
            if (catalysed.advance(4.0)) {
                catalysedFires++;
            }
        }

        LOGGER.info("[FST-GameTest] venom cadence: first={} (expect 20), plain 60t={} (expect 3), "
                        + "x4 60t={} (expect 12)",
                firstFireTick, plainFires, catalysedFires);

        context.assertEquals(20, firstFireTick, "a 1.0x poison must wait a full interval, not tick every tick");
        context.assertEquals(3, plainFires, "60 ticks at 1.0x is three intervals");
        context.assertEquals(12, catalysedFires, "a 4.0x poison fires four times as often");
        context.complete();
    }

    /**
     * `fire_master` caps the stacked burn at 10 s and turns the remaining burn time into the heat
     * meter: interval shrinks from 20 to 5 ticks, per-tick damage rises from 0.05 to 0.15 per level.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void fireBurnScalesWithHeatAndCapsStacking(TestContext context) {
        double coldInterval = FireBurn.intervalFor(0);
        double hotInterval = FireBurn.intervalFor(FireBurn.MAX_FIRE_TICKS);
        double coldDamage = FireBurn.damageFor(6, 0);
        double hotDamage = FireBurn.damageFor(6, FireBurn.MAX_FIRE_TICKS);
        int stacked = FireBurn.ignitedTicks(180, 6);
        int capped = FireBurn.ignitedTicks(FireBurn.MAX_FIRE_TICKS, 6);
        int lavaLonger = FireBurn.ignitedTicks(400, 6);

        LOGGER.info("[FST-GameTest] fire burn: interval {}->{} (expect 20->5), damage {}->{} "
                        + "(expect 0.3->0.9), 180+60={} (expect 200), 200+60={} (expect 200), 400+60={} (expect 400)",
                coldInterval, hotInterval, coldDamage, hotDamage, stacked, capped, lavaLonger);

        context.assertTrue(Math.abs(coldInterval - 20.0) < 1e-6, "no heat must burn every 20 ticks, got " + coldInterval);
        context.assertTrue(Math.abs(hotInterval - 5.0) < 1e-6, "max heat must burn every 5 ticks, got " + hotInterval);
        context.assertTrue(Math.abs(coldDamage - 0.30) < 1e-6, "level 6 at no heat must tick 0.30, got " + coldDamage);
        context.assertTrue(Math.abs(hotDamage - 0.90) < 1e-6, "level 6 at max heat must tick 0.90, got " + hotDamage);
        context.assertTrue(stacked == FireBurn.MAX_FIRE_TICKS, "stacking must cap at 200 ticks, got " + stacked);
        context.assertTrue(capped == FireBurn.MAX_FIRE_TICKS, "an already capped burn must stay at 200, got " + capped);
        context.assertTrue(lavaLonger == 400, "a longer burn from lava must never be shortened, got " + lavaLonger);
        context.complete();
    }

    /**
     * The level curve, applied to a mob: levelling at a known difficulty must produce a level inside
     * the documented band and the health/attack/armour the curve promises.
     *
     * <p>The test above only proves "a spawned mob gets <em>some</em> level" — in the game-test world
     * that is level 1, i.e. no stat modifiers at all, so it would not notice a broken curve. Here the
     * difficulty is supplied directly, so the level is deterministic up to the documented ±25 % spread
     * and the stats have to match it exactly.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void levellingAppliesTheDocumentedStatCurve(TestContext context) {
        ZombieEntity zombie = EntityType.ZOMBIE.create(context.getWorld());
        context.assertTrue(zombie != null, "could not create a zombie without spawning it");
        if (zombie == null) {
            context.complete();
            return;
        }

        // Read the *base* values before levelling: the modifiers the level adds are ADD_VALUE /
        // ADD_MULTIPLIED_BASE on top of these, and a zombie's base armour is 2, not 0.
        double baseArmor = zombie.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR).getBaseValue();

        // 3.0 difficulty -> averageLevelFor = 1 + floor(3.0 / 0.30) = 11, spread = max(1, 11*0.25) = 2
        MobLevels.INSTANCE.ensureLevel(zombie, 3.0f, context.getWorld().getRandom());

        int level = ((LevelledMob) zombie).getMobLevel();
        double expectedHealth = 20.0 * (1.0 + MobLevels.HEALTH_PER_LEVEL * (level - 1));
        double expectedAttack = 3.0 * (1.0 + MobLevels.ATTACK_PER_LEVEL * (level - 1));
        double expectedArmor = baseArmor + MobLevels.ARMOR_PER_LEVEL * (level - 1);
        // `LivingEntity#getAttributeValue` returns a double in 1.21.1; `getArmor` returns an int.
        double attack = zombie.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        double armor = zombie.getAttributeValue(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR);

        LOGGER.info("[FST-GameTest] levelled at difficulty 3.0: level={} (expect 9..13), maxHealth={} (expect {}), "
                        + "attack={} (expect {}), armor={} (expect {})",
                level, zombie.getMaxHealth(), expectedHealth, attack, expectedAttack, armor, expectedArmor);

        context.assertTrue(level >= 9 && level <= 13,
                "difficulty 3.0 should give level 9..13, got " + level);
        context.assertTrue(Math.abs(zombie.getMaxHealth() - expectedHealth) < 0.01,
                "max health " + zombie.getMaxHealth() + " != " + expectedHealth);
        context.assertTrue(Math.abs(attack - expectedAttack) < 0.01,
                "attack damage " + attack + " != " + expectedAttack);
        context.assertTrue(Math.abs(armor - expectedArmor) < 0.01,
                "armour " + armor + " != " + expectedArmor);
        context.complete();
    }

    /**
     * A mob that spawns must come out levelled, with the health the level curve promises.
     *
     * <p>Regression this guards: {@code v2.2.1} fixed mobs that never reached
     * {@code MobLevels.ensureLevel} at all (only mobs with equipment did).
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void spawnedMobGetsALevelAndItsStats(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);

        int level = ((LevelledMob) zombie).getMobLevel();
        float baseHealth = zombie.getMaxHealth();
        // MobLevels.apply(): max_health = base * (1 + HEALTH_PER_LEVEL * (level - 1))
        double expectedHealth = 20.0 * (1.0 + MobLevels.HEALTH_PER_LEVEL * (level - 1));
        LOGGER.info("[FST-GameTest] spawned zombie: level={}, maxHealth={}, expectedMaxHealth={}, health={}",
                level, baseHealth, expectedHealth, zombie.getHealth());

        context.assertTrue(level >= 1, "a spawned mob must have a level, got " + level);
        context.assertTrue(
                Math.abs(zombie.getMaxHealth() - expectedHealth) < 0.01,
                "max health " + zombie.getMaxHealth() + " does not follow the level curve for level "
                        + level + " (expected " + expectedHealth + ")");
        context.complete();
    }

    /**
     * Damage must actually land, and a smaller hit inside the invulnerability window must be
     * discarded.
     *
     * <p>This is the vanilla gate the elemental riders have to work around (they save and restore
     * {@code timeUntilRegen} / {@code lastDamageTaken}). If a future change breaks that accounting in
     * a way that also disables the gate, "combo" behaviour comes back — observed in real play as
     * "元素伤害无视无敌帧".
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void damageLandsAndIFramesGateTheNextHit(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
        zombie.setHealth(zombie.getMaxHealth());

        float before = zombie.getHealth();
        boolean firstHit = zombie.damage(context.getWorld().getDamageSources().generic(), 4.0f);
        float afterFirst = zombie.getHealth();
        boolean secondHit = zombie.damage(context.getWorld().getDamageSources().generic(), 1.0f);
        float afterSecond = zombie.getHealth();

        LOGGER.info("[FST-GameTest] damage: before={}, firstHit={}, afterFirst={}, secondHit={}, "
                        + "afterSecond={}, lastDamageTaken(hurtTime)={}",
                before, firstHit, afterFirst, secondHit, afterSecond, zombie.hurtTime);

        context.assertTrue(before > 0.0f, "the test zombie must spawn alive");
        context.assertTrue(firstHit, "the first hit must be applied");
        context.assertTrue(afterFirst < before, "health must drop after the first hit");
        context.assertFalse(secondHit, "a smaller hit inside the i-frame window must be discarded");
        context.assertEquals(afterFirst, afterSecond, "a discarded hit must not change health");
        context.complete();
    }

    /**
     * The level curve itself, pinned to the table documented on {@code MobLevels.averageLevelFor}.
     *
     * <p>`v2.2.2` re-calibrated that curve twice over (world term floor, uncapped player power), and
     * both numbers were argued from bytecode rather than measured. A future "small tweak" to the
     * divisor silently re-tunes the whole game, so the documented points are asserted here.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void levelCurveMatchesTheDocumentedTable(TestContext context) {
        assertLevel(context, 0.0f, 1);    // nothing has happened in this world yet
        assertLevel(context, 0.5f, 2);    // a full-health, unarmoured player near spawn
        assertLevel(context, 1.6f, 6);    // iron armour + a few enchantments
        assertLevel(context, 10.9f, 37);  // modded netherite + ~60 enchantment levels
        assertLevel(context, 31.0f, 104); // uncapped: the curve keeps going
        context.complete();
    }

    private static void assertLevel(TestContext context, float difficulty, int expected) {
        int actual = MobLevels.INSTANCE.averageLevelFor(difficulty);
        LOGGER.info("[FST-GameTest] averageLevelFor({}) = {} (expected {})", difficulty, actual, expected);
        context.assertEquals(expected, actual,
                "averageLevelFor(" + difficulty + ") should be " + expected + " but was " + actual);
    }

    /**
     * The reformed armour curve, pinned to the table in {@code ArmorCurve}'s KDoc.
     *
     * <p>This asserts the **maths**, not the mixin: {@code DamageUtilMixin} calls these same functions,
     * and a future "small tweak" to the efficiency or the ceiling would silently re-tune every fight in
     * the game. The points that matter are the two boundaries — vanilla parity at 20 and saturation —
     * because those are the two claims the feature makes.
     */

    /**
     * Smoke test for the damage window: a hit resolved through `LivingEntity#damage` must open the window,
     * read the main hand through the mixin and close again without throwing.
     *
     * <p>⚠️ <b>It does not reproduce the `build=cleanup34` crash.</b> That recursion needs a
     * <b>player</b>-attributed source (`PlayerEntity#getWeaponStack` → `getMainHandStack` → the mixin), and a
     * game test has no player to attribute damage to — verified by disabling the re-entrancy guard and
     * watching this test still pass. Closing that gap needs a fake `ServerPlayerEntity`; until then the guard
     * is covered by review, and this test only guards the window's open/close path on a mob.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void damageWindowDoesNotRecurse(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 1, 1);
        // Player-attributed source and a projectile source: the two shapes that went through the resolver.
        var sources = context.getWorld().getDamageSources();
        zombie.damage(sources.playerAttack(context.getWorld().getClosestPlayer(0, 0, 0, 10, false)), 1.0F);
        zombie.damage(sources.arrow(new net.minecraft.entity.projectile.ArrowEntity(
            context.getWorld(), 0.0, 1.0, 0.0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.ARROW), null
        ), null), 1.0F);
        context.complete();
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void armorCurveExtendsPastTheVanillaWall(TestContext context) {
        // --- percentage part: bit-identical to vanilla up to the wall --------------------------
        assertReduction(context, 100.0f, 0.0f, 0.00f);
        assertReduction(context, 100.0f, 10.0f, 0.40f);
        assertReduction(context, 100.0f, 20.0f, 0.80f);

        // --- overflow becomes flat damage removal -------------------------------------------
        // 26 armour = 6 points over the wall = 3.0 flat off the 20 damage that survived 80 %.
        assertTaken(context, 100.0f, 26.0f, 20.0f - 3.0f);
        // 40 armour wants 10 flat, still below the cap on a 100-damage hit.
        assertTaken(context, 100.0f, 40.0f, 20.0f - 10.0f);
        // A small hit is where flat shines: 6 damage -> 1.2 survives the percentage, and the flat part
        // is capped at 75 % of that, so the hit cannot become free.
        assertTaken(context, 6.0f, 26.0f, 1.2f * (1.0f - ArmorCurve.MAX_FLAT_FRACTION));

        // --- invariants across a wide sweep -------------------------------------------------
        float previous = Float.MAX_VALUE;
        for (float armor = 20.0f; armor <= 200.0f; armor += 1.0f) {
            float taken = ArmorCurve.INSTANCE.damageTaken(50.0f, armor, Math.min(armor, ArmorCurve.VANILLA_CAP));
            context.assertTrue(taken <= previous + 1e-4f,
                    "armor " + armor + " must never take more than " + (armor - 1.0f) + " armour");
            context.assertTrue(taken > 0.0f, "armor " + armor + " must never make a hit free");
            previous = taken;
        }
        context.complete();
    }

    /**
     * Every enchantment must be applicable to <b>at least one</b> item.
     *
     * <p>An enchantment whose {@code primary_items} / {@code supported_items} points at a tag that does
     * not exist is <b>inert and silent</b>: the tag resolves to an empty list, {@code isPrimaryItem}
     * answers {@code false} for every stack in the game, and the enchantment can never be put on
     * anything — no error, no log, no crash. This is the {@code build=cleanup68} bug
     * ({@code shield_breaker} declared {@code #minecraft:enchantable/axe}, and 1.21.1 has no such tag;
     * vanilla's is {@code #minecraft:axes} plus {@code #minecraft:enchantable/sharp_weapon}).
     *
     * <p>Tag <em>contents</em> cannot be checked without the registry, which is why this lives here and
     * not in {@code runTest}: the generated JSON is only a string until a server loads it.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyEnchantmentAcceptsAtLeastOneItem(TestContext context) {
        var registry = context.getWorld().getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        List<String> problems = new ArrayList<>();
        int checked = 0;

        for (var key : GeneratedEnchantments.INSTANCE.getKEYS()) {
            var entry = registry.getEntry(key).orElse(null);
            if (entry == null) {
                problems.add(key.getValue() + "=not-registered");
                continue;
            }
            var definition = entry.value().definition();
            // `supportedItems()` is a plain `RegistryEntryList` in 1.21.1; only `primaryItems()` is
            // wrapped in an `Optional` (absent = "fall back to supported_items").
            // `-1` means "the JSON did not declare one", which is also unusable — the generator always
            // emits both, so an absent one is a bug rather than a "no restriction" feature.
            int primary = definition.primaryItems().map(net.minecraft.registry.entry.RegistryEntryList::size).orElse(-1);
            int supported = definition.supportedItems().size();
            checked++;
            if (primary == 0) {
                problems.add(key.getValue() + "=primary-items-empty");
            } else if (primary < 0) {
                problems.add(key.getValue() + "=primary-items-absent");
            } else if (supported == 0) {
                problems.add(key.getValue() + "=supported-items-empty");
            } else if (supported < 0) {
                problems.add(key.getValue() + "=supported-items-absent");
            }
        }

        // The two enchantments this guard was written for, checked by name so a future "empty tag"
        // regression in them is named explicitly instead of arriving as a bare count.
        var gale = enchantmentByPath(registry, "gale_elytra");
        var breaker = enchantmentByPath(registry, "shield_breaker");
        boolean galeOnElytra = gale != null
                && gale.value().isPrimaryItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.ELYTRA));
        boolean breakerOnAxe = breaker != null
                && breaker.value().isPrimaryItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_AXE));
        int elytraEnchantability = new net.minecraft.item.ItemStack(net.minecraft.item.Items.ELYTRA)
                .getItem().getEnchantability();
        boolean elytraEnchantable = new net.minecraft.item.ItemStack(net.minecraft.item.Items.ELYTRA)
                .isEnchantable();
        boolean axeEnchantable = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_AXE)
                .isEnchantable();

        // `build=cleanup72` added three mod-owned item tags (hoe / pickaxe / shield). A missing or empty
        // tag is silent — the enchantment simply never appears — so each new one is asserted by name.
        boolean bountifulOnHoe = accepts(registry, "bountiful", net.minecraft.item.Items.IRON_HOE);
        boolean gleanerOnPickaxe = accepts(registry, "gleaner", net.minecraft.item.Items.IRON_PICKAXE);
        boolean bashOnShield = accepts(registry, "shield_bash", net.minecraft.item.Items.SHIELD);
        boolean featherOnBoots = accepts(registry, "feather_fall", net.minecraft.item.Items.IRON_BOOTS);
        boolean hamstringOnSword = accepts(registry, "hamstring", net.minecraft.item.Items.IRON_SWORD);
        boolean hamstringOnAxe = accepts(registry, "hamstring", net.minecraft.item.Items.IRON_AXE);
        boolean deftOnMace = accepts(registry, "deft", net.minecraft.item.Items.MACE);

        LOGGER.info("[FST-GameTest] item tags: checked={}, problems={}, gale_elytra->elytra={}, "
                        + "shield_breaker->iron_axe={}, elytra.itemEnchantability={}, "
                        + "elytra.isEnchantable={}, iron_axe.isEnchantable={}",
                checked, problems, galeOnElytra, breakerOnAxe, elytraEnchantability,
                elytraEnchantable, axeEnchantable);
        LOGGER.info("[FST-GameTest] new tags: bountiful->hoe={}, gleaner->pickaxe={}, "
                        + "shield_bash->shield={}, feather_fall->boots={}, hamstring->sword={}, "
                        + "hamstring->axe={}, deft->mace={}",
                bountifulOnHoe, gleanerOnPickaxe, bashOnShield, featherOnBoots,
                hamstringOnSword, hamstringOnAxe, deftOnMace);

        context.assertTrue(problems.isEmpty(),
                "enchantments that can never be applied because their item tag is empty: " + problems);
        context.assertTrue(galeOnElytra, "gale_elytra must be acceptable on minecraft:elytra");
        context.assertTrue(breakerOnAxe, "shield_breaker must be acceptable on minecraft:iron_axe");
        // The table gates on `getEnchantability() > 0`; vanilla returns 0 for the elytra, which is why
        // no enchantment at all could be offered for it. `ItemEnchantabilityMixin` raises it to 15.
        context.assertTrue(elytraEnchantable,
                "minecraft:elytra must be enchantable at a table (ItemEnchantabilityMixin)");
        context.assertTrue(bountifulOnHoe, "bountiful must be acceptable on an iron hoe");
        context.assertTrue(gleanerOnPickaxe, "gleaner must be acceptable on an iron pickaxe");
        context.assertTrue(bashOnShield, "shield_bash must be acceptable on a shield");
        context.assertTrue(featherOnBoots, "feather_fall must be acceptable on iron boots");
        context.assertTrue(hamstringOnSword && hamstringOnAxe,
                "hamstring must be acceptable on both a sword and an axe");
        context.assertTrue(deftOnMace, "deft must be acceptable on a mace (#minecraft:enchantable/weapon)");
        context.complete();
    }

    /**
     * Looks an enchantment up by its id path, e.g. {@code "gale_elytra"}.
     *
     * <p>Goes through {@link GeneratedEnchantments#getKEYS()} rather than a generated per-id property so
     * this file does not have to know the Kotlin property names — and so a rename in KSP breaks the
     * string check loudly instead of the Java getter at compile time.
     */
    private static net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.enchantment.Enchantment>
            enchantmentByPath(net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> registry,
                              String path) {
        for (var key : GeneratedEnchantments.INSTANCE.getKEYS()) {
            if (key.getValue().getPath().equals(path)) {
                return registry.getEntry(key).orElse(null);
            }
        }
        return null;
    }

    /** The registry key of a generated enchantment, for APIs that take a `RegistryKey`. */
    private static net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment>
            enchantmentKeyByPath(String path) {
        for (var key : GeneratedEnchantments.INSTANCE.getKEYS()) {
            if (key.getValue().getPath().equals(path)) return key;
        }
        throw new IllegalStateException("no generated enchantment named " + path);
    }

    /**
     * 剧变 (transformative) reaction damage must not depend on the target's max health.
     *
     * <p>The formula is {@code (4 + 0.55 × attacker XP level) × coefficient × mastery × resonance},
     * i.e. it is driven by the **attacker's** progression — never by what it is hitting. That is easy to
     * misread in play, because a mod that levels mobs raises max health and the reaction numbers at the
     * same time (the player's level usually grows alongside), so "the number got bigger on the big mob"
     * looks like a health term. This test pins the invariant: two zombies, 20 vs 200 max health, same
     * reaction, identical flat damage.
     *
     * <p>Uses {@code attacker = null} deliberately: then level 0 and mastery 0, so the expected value is
     * exactly {@code 4 × 1.25 = 5.0} for {@code conduct} and nothing can hide behind a player's stats.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void transformativeReactionDamageIgnoresTargetMaxHealth(TestContext context) {
        ZombieEntity small = context.spawnMob(EntityType.ZOMBIE, 1, 1, 1);
        ZombieEntity big = context.spawnMob(EntityType.ZOMBIE, 3, 1, 1);
        big.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(200.0);
        big.setHealth(200.0F);

        float smallFlat = conductFlat(small);
        float bigFlat = conductFlat(big);
        float expected = dev.firefly.simpletweaks.util.elements.ElementMarks.TRANSFORMATIVE_BASE * 1.25F;

        LOGGER.info("[FST-GameTest] transformative flat: 20HP target={}, 200HP target={}, expected={} "
                        + "(attacker=null, conduct coeff 1.25)",
                smallFlat, bigFlat, expected);

        context.assertTrue(Math.abs(smallFlat - bigFlat) < 0.001F,
                "transformative damage must be independent of target max health, got "
                        + smallFlat + " vs " + bigFlat);
        context.assertTrue(Math.abs(smallFlat - expected) < 0.001F,
                "expected " + expected + " for conduct at attacker level 0, got " + smallFlat);
        context.complete();
    }

    /** Marks a lightning aura and triggers {@code conduct} (mark LIGHTNING + trigger WATER, coeff 1.25). */
    private static float conductFlat(ZombieEntity target) {
        dev.firefly.simpletweaks.util.elements.ElementMarks.INSTANCE
                .markOnly(target, dev.firefly.simpletweaks.util.elements.ElementType.LIGHTNING,
                        dev.firefly.simpletweaks.util.elements.ElementAuras.WEAK);
        return dev.firefly.simpletweaks.util.elements.ElementMarks.INSTANCE
                .recordAndReact(null, target, dev.firefly.simpletweaks.util.elements.ElementType.WATER,
                        10.0F,
                        dev.firefly.simpletweaks.util.elements.ElementAuras.WEAK,
                        true,
                        null)
                .getFlatDamage();
    }

    /**
     * The reaction scaling: 剧变 / 激化 read **gear power**, not the player's experience level.
     *
     * <p>The old driver was `experienceLevel`, a farmable currency: a mob farm took it to 100+, and one
     * 感电 then hit for ~143 before mastery/resonance. Gear power (the same number the mob difficulty curve
     * uses) cannot be farmed and is deliberately **uncapped** — the clamp was the guard against farming,
     * not against gear.
     *
     * <p>The pure function is tested directly because a game test has no `PlayerEntity`; the numbers below
     * are the documented band (`×5`: naked ≈ 0.5, iron ≈ 6.5, endgame ≈ 26.5).
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void reactionPowerScalesFromGearNotExperience(TestContext context) {
        var marks = dev.firefly.simpletweaks.util.elements.ElementMarks.INSTANCE;
        float scale = dev.firefly.simpletweaks.util.elements.ElementMarks.REACTION_POWER_SCALE;
        float naked = marks.reactionPowerFrom(0.1);
        float iron = marks.reactionPowerFrom(1.3);
        float endgame = marks.reactionPowerFrom(5.3);
        float capped = marks.reactionPowerFrom(50.0);
        float base = dev.firefly.simpletweaks.util.elements.ElementMarks.TRANSFORMATIVE_BASE;
        float perPower = dev.firefly.simpletweaks.util.elements.ElementMarks.TRANSFORMATIVE_PER_POWER;

        LOGGER.info("[FST-GameTest] reaction power x{}: naked(0.1)={}, iron(1.3)={}, endgame(5.3)={}, "
                        + "x50={}, base at endgame={}",
                scale, naked, iron, endgame, capped, base + endgame * perPower);

        context.assertTrue(Math.abs(naked - 0.5F) < 0.001F, "naked power should be ~0.5, got " + naked);
        context.assertTrue(Math.abs(iron - 6.5F) < 0.001F, "iron power should be ~6.5, got " + iron);
        context.assertTrue(Math.abs(endgame - 26.5F) < 0.001F, "endgame power should be ~26.5, got " + endgame);
        // Deliberately uncapped: a heavier build must keep scaling, exactly like the difficulty curve.
        context.assertTrue(Math.abs(capped - 250.0F) < 0.001F,
                "gear power must not be clamped, got " + capped);
        context.complete();
    }

    /**
     * 处决 (`execute`) must not turn an inflated health pool into free damage.
     *
     * <p>The formula reads the **target's** missing health, so a 1,000,000 HP target made one level-5
     * swing add up to 100,000 — and because that number is added to `e.amount` before the crit / combo /
     * true-damage multipliers, they multiplied the health pool too (the author measured 494,731 in one
     * hit). The cap is `1.25 × hit` (RARE tier), which for vanilla mobs is never reached, so nothing
     * changes there.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void executeBonusIsBoundedByTheHit(TestContext context) {
        var execute = dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExecuteHandler.INSTANCE;
        float vanillaMob = execute.executeBonus(20.0F, 10.0F, 5);
        float smallHit = execute.executeBonus(6.0F, 10.0F, 5);
        float hugePool = execute.executeBonus(100.0F, 990_000.0F, 5);
        float cap = 1.25F * 100.0F;
        float noLevel = execute.executeBonus(100.0F, 990_000.0F, 0);
        float fullHealth = execute.executeBonus(100.0F, 0.0F, 5);

        LOGGER.info("[FST-GameTest] execute bonus: vanilla(20 hit, 10 missing, lvl5)={} (expect 1.0), "
                        + "huge(100 hit, 990000 missing, lvl5)={} (expect cap {}), lvl0={}, full={}",
                vanillaMob, hugePool, cap, noLevel, fullHealth);

        context.assertTrue(Math.abs(vanillaMob - 1.0F) < 0.001F,
                "a normal mob must keep 2% x level x missing health, got " + vanillaMob);
        context.assertTrue(Math.abs(smallHit - Math.min(1.0F, Math.max(6.0F * 1.25F, 4.0F))) < 0.001F,
                "tiny hit: expected the uncapped value, got " + smallHit);
        context.assertTrue(Math.abs(hugePool - cap) < 0.001F,
                "an inflated health pool must clamp to " + cap + ", got " + hugePool);
        context.assertTrue(noLevel == 0.0F, "level 0 must add nothing, got " + noLevel);
        context.assertTrue(fullHealth == 0.0F, "an undamaged target must add nothing, got " + fullHealth);
        context.complete();
    }

    /**
     * Does the enchantment registered as [path] accept [item]?
     *
     * <p>An empty `primary_items` tag is invisible at runtime (the enchantment just never shows up), so
     * the callers above name the item each newly-added tag is supposed to cover.
     */
    private static boolean accepts(
            net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> registry,
            String path, net.minecraft.item.Item item) {
        var entry = enchantmentByPath(registry, path);
        return entry != null && entry.value().isPrimaryItem(new net.minecraft.item.ItemStack(item));
    }

    /**
     * 吞噬's stacks must live in the item's `CUSTOM_DATA` and survive a component round-trip.
     *
     * <p>`devour` is the only enchantment whose progression is stored on the stack (`build=cleanup77`).
     * If the NBT key or the component handling were wrong, the bonus would silently stay at 0 forever —
     * and the tooltip line would stay hidden, so nothing would look broken. This test writes the same
     * component the handler writes and reads it back through the handler's own accessor.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void devourStacksRoundTripThroughItemNbt(TestContext context) {
        var stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD);
        var handler = dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantDevourHandler.INSTANCE;

        int empty = handler.stacksOf(stack);
        var compound = new net.minecraft.nbt.NbtCompound();
        compound.putInt(dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantDevourHandler.KEY, 12);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(compound));
        int stored = handler.stacksOf(stack);

        // A second, unrelated custom-data field must not disturb the read (other mods write there too).
        compound.putInt("othermod:flag", 7);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(compound));
        int afterOtherKey = handler.stacksOf(stack);

        LOGGER.info("[FST-GameTest] devour stacks: empty={}, stored(12)={}, after-foreign-key={}, "
                        + "bonus at 12 stacks={}",
                empty, stored, afterOtherKey, 12 * 0.02F);

        context.assertEquals(0, empty, "a fresh weapon must have no stacks");
        context.assertEquals(12, stored, "stacks must survive the CUSTOM_DATA round-trip");
        context.assertEquals(12, afterOtherKey, "an unrelated NBT key must not disturb the count");
        context.complete();
    }

    /**
     * 因果's level maths: `max_level = 16` means **four pieces at IV**, not one item at 16.
     *
     * <p>The equipped level is the sum across the four armour slots, clamped to 16, and every formula
     * uses `sum / 4`. So a perfect set (4 × 4) and a single forced-16 item both resolve to effective 4 —
     * otherwise one `/fst enchant … karma 16 armor` would hand out three times the designed strength.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void karmaLevelMapsAFullSetToTheDesignCeiling(TestContext context) {
        var karma = dev.firefly.simpletweaks.enchantments.handlers.mystery.EnchantKarmaHandler.INSTANCE;
        int none = karma.equippedLevelOf(0);
        int one = karma.equippedLevelOf(4);
        int fullSet = karma.equippedLevelOf(4 * 4);
        int forced = karma.equippedLevelOf(64);
        float fullEffective = karma.effectiveLevelOf(fullSet);
        float shareAtFull = 0.20F + 0.10F * fullEffective;

        LOGGER.info("[FST-GameTest] karma levels: none={}, onePiece(4)={}, fullSet(4x4)={}, forced(64)={}, "
                        + "effectiveAtFull={}, poolShare={}",
                none, one, fullSet, forced, fullEffective, shareAtFull);

        context.assertEquals(0, none, "no armour must mean level 0");
        context.assertEquals(4, one, "one IV piece is level 4");
        context.assertEquals(16, fullSet, "four IV pieces must sum to the declared max of 16");
        context.assertEquals(16, forced, "an out-of-range sum must clamp to 16");
        context.assertTrue(Math.abs(fullEffective - 4.0F) < 0.001F,
                "a full set must resolve to effective level 4, got " + fullEffective);
        context.assertTrue(Math.abs(shareAtFull - 0.60F) < 0.001F,
                "the designed pool share at a full set is 60%, got " + shareAtFull);
        context.complete();
    }

    /**
     * 万法归元's counter table: the element that answers each incoming element.
     *
     * <p>0.5 units of the *countering* element are attached onto the attacker, vs the 1.0 unit an active
     * hit applies. The table must stay derived from the 克制链 (water beats fire, fire beats frost, frost
     * beats water, fire burns nature, lightning conducts on water, light and dark annihilate); a counter
     * that does not actually react would make the whole mechanic invisible.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void supremacyCounterAnswersEachElement(TestContext context) {
        var reactions = dev.firefly.simpletweaks.util.elements.ElementReactions.INSTANCE;
        var fire = dev.firefly.simpletweaks.util.elements.ElementType.FIRE;
        var frost = dev.firefly.simpletweaks.util.elements.ElementType.FROST;
        var water = dev.firefly.simpletweaks.util.elements.ElementType.WATER;
        var nature = dev.firefly.simpletweaks.util.elements.ElementType.NATURE;
        var lightning = dev.firefly.simpletweaks.util.elements.ElementType.LIGHTNING;
        var light = dev.firefly.simpletweaks.util.elements.ElementType.LIGHT;
        var dark = dev.firefly.simpletweaks.util.elements.ElementType.DARK;

        LOGGER.info("[FST-GameTest] supremacy counter: fire->{}, frost->{}, water->{}, nature->{}, "
                        + "lightning->{}, light->{}, dark->{}",
                reactions.counterOf(fire), reactions.counterOf(frost), reactions.counterOf(water),
                reactions.counterOf(nature), reactions.counterOf(lightning), reactions.counterOf(light),
                reactions.counterOf(dark));

        context.assertEquals(water, reactions.counterOf(fire), "fire is answered by water");
        context.assertEquals(fire, reactions.counterOf(frost), "frost is answered by fire");
        context.assertEquals(frost, reactions.counterOf(water), "water is answered by frost");
        context.assertEquals(fire, reactions.counterOf(nature), "nature is answered by fire");
        context.assertEquals(water, reactions.counterOf(lightning), "lightning is answered by water");
        context.assertEquals(dark, reactions.counterOf(light), "light is answered by dark");
        context.assertEquals(light, reactions.counterOf(dark), "dark is answered by light");

        // Every non-null answer must actually react against the element it answers.
        for (var incoming : new Object[] {fire, frost, water, nature, lightning, light, dark}) {
            var e = (dev.firefly.simpletweaks.util.elements.ElementType) incoming;
            var answer = reactions.counterOf(e);
            context.assertTrue(answer != null && reactions.of(e, answer) != null,
                    "counter " + answer + " must form a reaction on a " + e + " aura");
        }
        context.complete();
    }

    /**
     * The element layer's shape after `PHYSICAL` was retired.
     *
     * <p>Removing an enum constant is a silent, wide-reaching change: ordinal-shifted NBT, a conversion
     * enchantment that no longer exists, a protected tag with a dangling member. This test pins the facts
     * that code above it assumes — seven elements, a sentinel that really means "none", and exactly one
     * `<element>_conversion` / `<element>_protection` pair generated per element (14, not the old 16).
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void elementLayerHasSevenElementsAndNoPhysicalLeftovers(TestContext context) {
        var elements = dev.firefly.simpletweaks.util.elements.ElementType.values();
        var companion = dev.firefly.simpletweaks.util.elements.ElementType.Companion;
        var generated = GeneratedEnchantments.INSTANCE;

        int conversions = 0;
        int protections = 0;
        for (var element : elements) {
            if (generated.keyOfElement(element.getId(), false) != null) conversions++;
            if (generated.keyOfElement(element.getId(), true) != null) protections++;
        }

        LOGGER.info("[FST-GameTest] element layer: elements={}, noneOrdinal={}, conversions={}, "
                        + "protections={}, ordinals {}..{}",
                elements.length, dev.firefly.simpletweaks.util.elements.ElementType.NONE_ORDINAL,
                conversions, protections,
                elements[0].ordinal(), elements[elements.length - 1].ordinal());

        context.assertEquals(7, elements.length, "PHYSICAL is retired: seven elements remain");
        context.assertEquals(-1, dev.firefly.simpletweaks.util.elements.ElementType.NONE_ORDINAL,
                "the no-element sentinel stays -1");
        context.assertEquals(7, conversions, "one <element>_conversion per element");
        context.assertEquals(7, protections, "one <element>_protection per element");

        // The sentinel and every out-of-range value must resolve to null, not to element 0 or an exception.
        context.assertTrue(
                companion.ofOrdinal(dev.firefly.simpletweaks.util.elements.ElementType.NONE_ORDINAL) == null,
                "NONE_ORDINAL must resolve to null");
        context.assertTrue(companion.ofOrdinal(7) == null, "an out-of-range ordinal must resolve to null");
        context.assertEquals(elements[0], companion.ofOrdinal(0), "ordinal 0 is the first real element");
        context.complete();
    }

    /**
     * The damage-type -> element memo must agree with a fresh scan, for both hits and misses.
     *
     * <p>The memo is the only place in the engine that can go stale without an exception: a wrong answer
     * silently changes which protection applies. Misses are stored too (as a marker), so the failure mode
     * this pins is "a non-element damage type was cached as an element" or vice versa.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void elementClassificationMemoMatchesAFreshScan(TestContext context) {
        var registry = context.getWorld().getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE);
        var companion = dev.firefly.simpletweaks.util.elements.ElementType.Companion;

        int checked = 0;
        int elemental = 0;
        for (var entry : registry.streamEntries().toList()) {
            var first = companion.classify(entry);
            var second = companion.classify(entry);
            // assertEquals dereferences both sides, so a `null` answer (the expected one for most types)
            // has to be compared as a boolean.
            context.assertTrue(first == second, "classify is stable for " + entry.getKey().orElseThrow());
            if (first != null) elemental++;
            checked++;
        }

        // A known elemental type and a known non-elemental one, so "everything returned null" cannot pass.
        var fire = registry.getEntry(net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
                net.minecraft.util.Identifier.of("simple_tweaks", "element_fire")));
        var trueDamage = registry.getEntry(net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
                net.minecraft.util.Identifier.of("simple_tweaks", "true_damage")));

        LOGGER.info("[FST-GameTest] element classify: {} damage type(s), {} elemental, fire={}, "
                        + "true_damage={}", checked, elemental,
                fire.map(companion::classify).orElse(null),
                trueDamage.map(companion::classify).orElse(null));

        context.assertTrue(fire.isPresent(), "element_fire is registered");
        context.assertTrue(fire.map(e -> companion.classify(e) != null).orElse(false),
                "element_fire must classify as an element");
        context.assertTrue(trueDamage.isPresent(), "true_damage is registered");
        context.assertTrue(trueDamage.map(e -> companion.classify(e) == null).orElse(false),
                "true_damage claims no element");

        companion.clearCache();
        context.complete();
    }

    /**
     * The armour aggregates on {@code EnchantPresence} must equal the per-key calls they replace.
     *
     * <p>The element engine's mitigation row was moved onto these aggregates (`build=cleanup12`); if the
     * sum and the max ever disagree with the single-key answers, protection would be under- or
     * over-counted with no log line.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void enchantPresenceAggregatesAgreeWithSingleKeys(TestContext context) {
        var registry = context.getWorld().getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        var presence = dev.firefly.simpletweaks.util.EnchantPresence.INSTANCE;
        var mob = net.minecraft.entity.EntityType.ZOMBIE.create(context.getWorld());

        context.assertTrue(mob != null, "zombie created");
        var helm = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_HELMET);
        var chest = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE);
        var crit = enchantmentKeyByPath("crit");
        var ward = enchantmentKeyByPath("elemental_ward");
        helm.addEnchantment(enchantmentByPath(registry, "crit"), 2);
        helm.addEnchantment(enchantmentByPath(registry, "elemental_ward"), 1);
        chest.addEnchantment(enchantmentByPath(registry, "elemental_ward"), 3);
        mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, helm);
        mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, chest);

        int critSum = presence.armorSum(mob, new net.minecraft.registry.RegistryKey[] { crit });
        int wardSum = presence.armorSum(mob, new net.minecraft.registry.RegistryKey[] { ward });
        int wardMax = presence.armorMax(mob, ward);
        int wardAll = presence.totalSum(mob, new net.minecraft.registry.RegistryKey[] { ward });

        LOGGER.info("[FST-GameTest] presence aggregates: critSum={}, wardSum={} (max {}), wardTotal={}",
                critSum, wardSum, wardMax, wardAll);

        context.assertEquals(2, critSum, "one piece carries crit 2");
        context.assertEquals(4, wardSum, "ward sums across pieces: 1 + 3");
        context.assertEquals(3, wardMax, "ward max is the chest piece");
        context.assertEquals(4, wardAll, "ward over all six slots is still 4");
        context.complete();
    }

    /**
     * `enchant_with_levels` / `enchant_randomly` roll from the whole enchantment registry when a loot table
     * leaves `options` out, so the creative-only enchantment can end up in a chest. Both loot functions hand
     * their result through the guard; this pins the strip and the "everything else survives" side.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void lootGuardStripsCreativeOnlyEnchantments(TestContext context) {
        var registry = context.getWorld().getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        var stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);
        stack.addEnchantment(enchantmentByPath(registry, "infinite_power"), 1);
        stack.addEnchantment(enchantmentByPath(registry, "venom"), 2);

        var stripped = dev.firefly.simpletweaks.util.LootEnchantmentGuard.strip(stack);
        var left = stripped.get(net.minecraft.component.DataComponentTypes.ENCHANTMENTS);
        boolean stillCreative = left != null && left.getEnchantments().stream()
                .anyMatch(entry -> entry.matchesKey(enchantmentKeyByPath("infinite_power")));
        int venom = left == null ? 0 : left.getLevel(enchantmentByPath(registry, "venom"));

        LOGGER.info("[FST-GameTest] loot guard: creative={}, venom={} (expect false/2)", stillCreative, venom);

        context.assertFalse(stillCreative, "infinite_power must never come out of a loot function");
        context.assertEquals(2, venom, "the ordinary enchantment must survive the strip");

        boolean strippedAgain = dev.firefly.simpletweaks.util.LootEnchantmentGuard.stripInPlace(stripped);
        LOGGER.info("[FST-GameTest] loot guard second pass: stripped={} (expect false)", strippedAgain);
        context.assertFalse(strippedAgain, "a clean stack must not be rewritten on every sweep");

        // Chests mostly roll books, and a book keeps its roll in `stored_enchantments`, not `enchantments`.
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new net.minecraft.component.type.ItemEnchantmentsComponent.Builder(
                net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
        stored.add(enchantmentByPath(registry, "infinite_power"), 1);
        stored.add(enchantmentByPath(registry, "venom"), 2);
        book.set(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS, stored.build());

        var strippedBook = dev.firefly.simpletweaks.util.LootEnchantmentGuard.strip(book);
        var bookLeft = strippedBook.get(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);
        boolean bookStillCreative = bookLeft != null && bookLeft.getEnchantments().stream()
                .anyMatch(entry -> entry.matchesKey(enchantmentKeyByPath("infinite_power")));
        int bookVenom = bookLeft == null ? 0 : bookLeft.getLevel(enchantmentByPath(registry, "venom"));
        LOGGER.info("[FST-GameTest] loot guard book: creative={}, venom={} (expect false/2)",
                bookStillCreative, bookVenom);

        context.assertFalse(bookStillCreative, "a rolled book must lose the creative-only enchantment too");
        context.assertEquals(2, bookVenom, "the book's ordinary enchantment must survive");

        context.complete();
    }

    /**
     * Both loot mixins hand their result back through {@code CallbackInfoReturnable#setReturnValue}. Without
     * {@code cancellable = true} on the injection that call throws {@code CancellationException: The call
     * method_522 is not cancellable}; the packet handler swallows it as "Failed to handle packet" and the chest
     * simply opens empty. Running the real loot function is the only way to see that from a test.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void lootFunctionStripInjectionIsCancellable(TestContext context) {
        var world = context.getWorld();
        var parameters = new net.minecraft.loot.context.LootContextParameterSet.Builder(world)
                .add(net.minecraft.loot.context.LootContextParameters.ORIGIN, net.minecraft.util.math.Vec3d.ZERO)
                .build(net.minecraft.loot.context.LootContextTypes.CHEST);
        var lootContext = new net.minecraft.loot.context.LootContext.Builder(parameters)
                .random(net.minecraft.util.math.random.Random.create(7L))
                .build(java.util.Optional.empty());

        var levels = (net.minecraft.loot.function.EnchantWithLevelsLootFunction)
                new net.minecraft.loot.function.EnchantWithLevelsLootFunction.Builder(
                        net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(30.0f)).build();
        var randomly = (net.minecraft.loot.function.EnchantRandomlyLootFunction)
                new net.minecraft.loot.function.EnchantRandomlyLootFunction.Builder().build();

        var first = levels.process(new ItemStack(Items.DIAMOND_SWORD), lootContext);
        var second = randomly.process(new ItemStack(Items.DIAMOND_SWORD), lootContext);
        LOGGER.info("[FST-GameTest] loot functions ran: levels={}, random={}, (expect both to return a sword)",
                first.getItem(), second.getItem());

        context.assertEquals(Items.DIAMOND_SWORD, first.getItem(), "enchant_with_levels must survive the guard");
        context.assertEquals(Items.DIAMOND_SWORD, second.getItem(), "enchant_randomly must survive the guard");
        context.complete();
    }

    /**
     * The mob-level announce bookkeeping must collapse the two seams' duplicate into one packet.
     *
     * <p>{@code MobLevelTracker} and {@code MobSpawnEnhancer} both announce the same level on the normal
     * mob-load path; without the dedupe every spawn costs two encodes. A real level change must still go
     * out.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void mobLevelAnnounceIsDeduplicated(TestContext context) {
        var sync = dev.firefly.simpletweaks.util.mobs.MobLevelSync.INSTANCE;
        var playerId = java.util.UUID.randomUUID();
        int entityId = 4242;

        boolean first = sync.changed(playerId, entityId, 12);
        boolean repeat = sync.changed(playerId, entityId, 12);
        boolean changed = sync.changed(playerId, entityId, 13);
        boolean otherPlayer = sync.changed(java.util.UUID.randomUUID(), entityId, 12);

        LOGGER.info("[FST-GameTest] announce dedupe: first={}, repeat={}, changed={}, otherPlayer={}",
                first, repeat, changed, otherPlayer);

        context.assertTrue(first, "the first announce goes out");
        context.assertTrue(!repeat, "the same level is not sent twice");
        context.assertTrue(changed, "a real level change is still sent");
        context.assertTrue(otherPlayer, "another player has its own bookkeeping");
        sync.forget(playerId);
        context.complete();
    }

    /**
     * 共鸣核心's stack maths: `2 + level` stacks, `+4 %` per stack, `×2` at the cap.
     *
     * <p>Pure functions only — the amplification itself lives behind `ElementDamageSeam` and needs a
     * player, which a game test has none of. What this pins is the part that would silently drift: the
     * cap scaling with level and the multiplier at the cap.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void resonanceCoreScalesWithLevel(TestContext context) {
        var core = dev.firefly.simpletweaks.enchantments.handlers.mystery.EnchantResonanceCoreHandler.INSTANCE;
        int capI = core.capFor(1);
        int capIII = core.capFor(3);
        float at0 = core.multiplierFor(0);
        float at5 = core.multiplierFor(5);
        float overflow = core.multiplierFor(9);
        float critAtCap = at5 * dev.firefly.simpletweaks.enchantments.handlers.mystery
                .EnchantResonanceCoreHandler.CRIT_MULTIPLIER;

        LOGGER.info("[FST-GameTest] resonance core: cap(I)={}, cap(III)={}, mult(0)={}, mult(5)={}, "
                        + "mult(9 clamped)={}, crit total at cap={}",
                capI, capIII, at0, at5, overflow, critAtCap);

        context.assertEquals(3, capI, "level I caps at 3 stacks");
        context.assertEquals(5, capIII, "level III caps at 5 stacks");
        context.assertTrue(Math.abs(at0 - 1.0F) < 0.001F, "no stacks must be x1, got " + at0);
        context.assertTrue(Math.abs(at5 - 1.2F) < 0.001F, "5 stacks must be x1.2, got " + at5);
        context.assertTrue(Math.abs(overflow - at5) < 0.001F,
                "stacks above the cap must clamp to the capped multiplier, got " + overflow);
        context.assertTrue(Math.abs(critAtCap - 2.4F) < 0.001F,
                "the guaranteed crit at full stacks is x2.4 total, got " + critAtCap);
        context.complete();
    }

    /**
     * The moon and star blessings each answer to a fixed set of reaction keys.
     *
     * <p>Those keys are strings: a typo (`"conducts"`) compiles, runs, and silently never fires — the
     * enchantment would look broken with no log line to show for it. This test resolves every key the two
     * handlers claim against the reaction table, and pins that the families stay disjoint (the moon owns
     * the water family, the star the frost one).
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blessingReactionKeysAllExist(TestContext context) {
        var reactions = dev.firefly.simpletweaks.util.elements.ElementReactions.INSTANCE;
        var lunar = dev.firefly.simpletweaks.enchantments.handlers.mystery
                .EnchantMoonBlessingHandler.reactionKeys();
        var stellar = dev.firefly.simpletweaks.enchantments.handlers.mystery
                .EnchantStarBlessingHandler.reactionKeys();
        var solar = dev.firefly.simpletweaks.enchantments.handlers.mystery
                .EnchantSunBlessingHandler.reactionKeys();

        LOGGER.info("[FST-GameTest] blessing keys: lunar={}, stellar={}, solar={}", lunar, stellar, solar);

        context.assertEquals(4, lunar.size(), "the moon answers four water reactions");
        context.assertEquals(6, stellar.size(), "the star answers six frost reactions");
        context.assertEquals(6, solar.size(), "the sun answers six fire/light reactions");
        for (var key : lunar) {
            context.assertTrue(reactions.byKey(key) != null, "unknown lunar reaction key: " + key);
        }
        for (var key : stellar) {
            context.assertTrue(reactions.byKey(key) != null, "unknown stellar reaction key: " + key);
            context.assertTrue(!lunar.contains(key), "a reaction cannot be both lunar and stellar: " + key);
        }
        for (var key : solar) {
            context.assertTrue(reactions.byKey(key) != null, "unknown solar reaction key: " + key);
            context.assertTrue(!lunar.contains(key), "a reaction cannot be both lunar and solar: " + key);
            context.assertTrue(!stellar.contains(key), "a reaction cannot be both stellar and solar: " + key);
        }
        context.complete();
    }

    /**
     * Bar numbers must round to the configured place count and drop the padding, so 0.1 with four places
     * prints "0.1" rather than "0.1000". Formatting runs on the client, but [BarNumber] carries no Minecraft
     * types, so the rule can be pinned here instead of only by eyeballing the HUD.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void barNumbersTrimTrailingZeros(TestContext context) {
        String[] cases = {
                dev.firefly.simpletweaks.client.hud.BarNumber.INSTANCE.of(0.1, 4),
                dev.firefly.simpletweaks.client.hud.BarNumber.INSTANCE.of(20.0, 2),
                dev.firefly.simpletweaks.client.hud.BarNumber.INSTANCE.of(10.75, 1),
                dev.firefly.simpletweaks.client.hud.BarNumber.INSTANCE.of(4096.0, 0),
                dev.firefly.simpletweaks.client.hud.BarNumber.INSTANCE.of(892.28, 1),
                dev.firefly.simpletweaks.client.hud.BarNumber.INSTANCE.of(-0.001, 2),
        };
        String[] expected = {"0.1", "20", "10.8", "4096", "892.3", "0"};

        LOGGER.info("[FST-GameTest] bar numbers: {}", String.join(" / ", cases));
        for (int i = 0; i < expected.length; i++) {
            context.assertEquals(expected[i], cases[i], "bar number " + i);
        }
        context.complete();
    }

    /**
     * A four-digit health pool must not print "1093.84/1093.84" (90 px) inside a 78 px track: the HUD falls
     * back to whole numbers first, then to the compact k/M form. [HudBar.compact] is the last rung.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void compactBarNumbersFitTheTrack(TestContext context) {
        String[] cases = {
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(999.0),
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(1093.84),
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(2000.0),
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(1234567.0),
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(2_500_000_000.0),
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(3_200_000_000_000.0),
                dev.firefly.simpletweaks.client.hud.HudBar.INSTANCE.compact(0.0),
        };
        String[] expected = {"999", "1.09k", "2k", "1.23M", "2.5B", "3.2T", "0"};

        LOGGER.info("[FST-GameTest] compact bar numbers: {}", String.join(" / ", cases));
        for (int i = 0; i < expected.length; i++) {
            context.assertEquals(expected[i], cases[i], "compact bar number " + i);
        }
        context.complete();
    }

    /**
     * The `barCompactNumbers` / `compactNumbers` switches must only ever <em>shorten</em> a number, and only
     * above 1 000: a mod that turned `999` into `1.00k` (or dropped the flag on the floor) would look like a
     * formatting bug in the HUD rather than a settings bug.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void compactNumberSwitchOnlyShortensAboveAThousand(TestContext context) {
        String[] cases = {
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.format(999.0, 2, true),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.format(1240.0, 2, true),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.format(1240.0, 2, false),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.format(1_500_000.0, 0, true),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(999_999.0, 2),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(999_999_999.0, 2),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(1_500_000_000_000.0, 2),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(2.0e18, 2),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(3.4e38, 2),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(1_240.0, 2, true),
                dev.firefly.simpletweaks.client.hud.CompactNumber.INSTANCE.of(3.4e38, 2, true),
        };
        String[] expected = {"999", "1.24k", "1240", "1.5M", "1M", "1B", "1.5T", "2E", "3.4e38", "1.24e3", "3.4e38"};

        LOGGER.info("[FST-GameTest] compact number switch: {}", String.join(" / ", cases));
        for (int i = 0; i < expected.length; i++) {
            context.assertEquals(expected[i], cases[i], "compact number switch " + i);
        }
        context.complete();
    }

    /**
     * The equipment snapshot must follow equipment, including the swap-away case, and it must not answer
     * for a slot it was not asked about.
     *
     * <p>This is the whole risk of {@code EnchantPresence}: it caches "what are this entity's enchantments"
     * and every handler in the mod now trusts it. A stale snapshot would not crash — it would silently
     * answer with the gear the entity used to wear, which is exactly the class of bug that survives a
     * playtest. Three things are pinned here:
     *
     * <ol>
     *   <li>a piece equipped after the fact shows up immediately ({@code onEquipStack} invalidated),</li>
     *   <li>armour levels sum across pieces while the main hand stays independent,</li>
     *   <li>removing a piece is seen on the very next question.</li>
     * </ol>
     *
     * <p>The last assertion documents the one blind spot on purpose: writing enchantments onto the
     * <em>already equipped</em> stack in place changes no stack identity, so the snapshot keeps the old
     * answer until the writer calls {@code EnchantPresence.invalidate} — which is why
     * {@code /fst enchant}, the mob gear roll/strip and {@code void_protection} do exactly that.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void enchantPresenceFollowsEquipmentChanges(TestContext context) {
        var registry = context.getWorld().getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        var critEntry = enchantmentByPath(registry, "crit");
        context.assertTrue(critEntry != null, "crit must exist in the enchantment registry");
        if (critEntry == null) {
            context.complete();
            return;
        }
        var critKey = critEntry.getKey().orElse(null);
        context.assertTrue(critKey != null, "crit registry entry must carry a key");

        ZombieEntity zombie = EntityType.ZOMBIE.create(context.getWorld());
        context.assertTrue(zombie != null, "could not create a zombie for the presence test");
        if (zombie == null || critKey == null) {
            context.complete();
            return;
        }
        var presence = dev.firefly.simpletweaks.util.EnchantPresence.INSTANCE;

        context.assertEquals(0, presence.armorLevelOf(zombie, critKey),
                "a bare zombie must report no armour enchantment");

        var helmet = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_HELMET);
        helmet.addEnchantment(critEntry, 2);
        zombie.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, helmet);

        int afterHelmet = presence.armorLevelOf(zombie, critKey);
        context.assertEquals(2, afterHelmet, "two levels of crit on the helmet must be visible at once");
        context.assertEquals(0, presence.mainHandLevelOf(zombie, critKey),
                "the armour aggregate must not leak into the main hand");

        var chest = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE);
        chest.addEnchantment(critEntry, 3);
        zombie.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, chest);

        int afterChest = presence.armorLevelOf(zombie, critKey);
        int total = presence.totalLevel(zombie, critKey);
        context.assertEquals(5, afterChest, "armour levels must sum across the four pieces");
        context.assertEquals(5, total, "all-slot total must match the armour total here");

        zombie.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
        int afterRemoval = presence.armorLevelOf(zombie, critKey);

        // In-place write: no stack identity changes, so the snapshot keeps the stale answer until told.
        chest.addEnchantment(critEntry, 4);
        int stale = presence.armorLevelOf(zombie, critKey);
        presence.invalidate(zombie);
        int refreshed = presence.armorLevelOf(zombie, critKey);

        LOGGER.info("[FST-GameTest] presence: helmet={} then chest={} (sum), helmet removed={}, "
                        + "in-place write without invalidate={}, after invalidate={}",
                afterHelmet, afterChest, afterRemoval, stale, refreshed);

        context.assertEquals(3, afterRemoval, "removing the helmet must be seen immediately");
        context.assertEquals(3, stale, "an in-place write without invalidate must stay invisible (documented)");
        context.assertEquals(4, refreshed, "invalidate must make an in-place write visible");
        context.complete();
    }

    private static void assertReduction(TestContext context, float damage, float armor, float expected) {
        float actual = ArmorCurve.INSTANCE.reductionFor(armor, damage);
        LOGGER.info("[FST-GameTest] damage {} armor {} -> reduction {} (expected {})",
                damage, armor, actual, expected);
        context.assertTrue(Math.abs(actual - expected) < 0.001f,
                "armor " + armor + " vs " + damage + " damage should reduce by " + expected
                        + " but was " + actual);
    }

    /**
     * A thrown potion, snowball or fireball is not a swing: `DamageSource#weaponStack` resolves through
     * the attacker's main hand, so before `build=cleanup16` a thrown potion inherited whatever sword the
     * thrower was holding and could fire `venom` / `fire_master` / the blessings. A persistent projectile
     * must still resolve to the weapon it was fired with.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void shotWeaponIgnoresTheHandForThrownProjectiles(TestContext context) {
        World world = context.getWorld();
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        context.assertTrue(zombie != null, "could not create a zombie without spawning it");
        if (zombie == null) {
            context.complete();
            return;
        }

        // 1. An arrow keeps the weapon it was fired with, snapshot or vanilla field.
        ArrowEntity arrow = new ArrowEntity(world, 0.0, 64.0, 0.0, new ItemStack(Items.ARROW), null);
        ItemStack bow = new ItemStack(Items.BOW);
        ShotWeapon.INSTANCE.record(arrow, bow);
        ItemStack fromArrow = ShotWeapon.INSTANCE.weaponFor(world.getDamageSources().arrow(arrow, zombie));

        // 2. A thrown potion resolves to "no weapon" — empty, not the thrower's hand.
        PotionEntity potion = new PotionEntity(world, zombie);
        ItemStack fromPotion = ShotWeapon.INSTANCE.weaponFor(world.getDamageSources().indirectMagic(potion, zombie));

        // 3. A plain melee hit carries no override at all.
        ItemStack fromMelee = ShotWeapon.INSTANCE.weaponFor(world.getDamageSources().mobAttack(zombie));

        // 4. Nor does anything else whose direct entity is not the attacker: an area-effect cloud is the
        //    case `build=cleanup17` broadened the rule to, because it used to fall through to the hand too.
        AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(world, 0.0, 64.0, 0.0);
        ItemStack fromCloud = ShotWeapon.INSTANCE.weaponFor(world.getDamageSources().indirectMagic(cloud, zombie));

        LOGGER.info("[FST-GameTest] shot weapon: arrow={} (expect bow), potion={} (expect empty), "
                        + "cloud={} (expect empty), melee={} (expect none)",
                fromArrow, fromPotion, fromCloud, fromMelee);

        context.assertTrue(fromArrow != null && fromArrow.isOf(Items.BOW),
                "an arrow must resolve to the bow it was fired with, got " + fromArrow);
        context.assertTrue(fromPotion != null && fromPotion.isEmpty(),
                "a thrown potion must resolve to an empty weapon, got " + fromPotion);
        context.assertTrue(fromCloud != null && fromCloud.isEmpty(),
                "an area-effect cloud must resolve to an empty weapon, got " + fromCloud);
        context.assertTrue(fromMelee == null || fromMelee.isEmpty(),
                "a melee hit must not carry an override, got " + fromMelee);
        context.complete();
    }

    /**
     * `setOwner` runs **inside** `ProjectileEntity`'s constructor, so the first snapshot is a guess taken
     * from the shooter's main hand. With the bow in the off hand that guess is the sword — and before
     * `build=cleanup23` it stuck, so a plain bow fired the sword's enchantments. The constructor injection
     * records vanilla's own `weapon` argument authoritatively and replaces the guess; later non-authoritative
     * guesses must still not overwrite it.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void shotWeaponConstructorOverridesTheHandGuess(TestContext context) {
        World world = context.getWorld();
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        context.assertTrue(zombie != null, "could not create a zombie without spawning it");
        if (zombie == null) {
            context.complete();
            return;
        }

        ArrowEntity arrow = new ArrowEntity(world, 0.0, 64.0, 0.0, new ItemStack(Items.ARROW), null);
        ShotWeapon.INSTANCE.record(arrow, new ItemStack(Items.NETHERITE_SWORD));
        ShotWeapon.INSTANCE.record(arrow, new ItemStack(Items.BOW), true);
        ItemStack resolved = ShotWeapon.INSTANCE.weaponFor(world.getDamageSources().arrow(arrow, zombie));

        ShotWeapon.INSTANCE.record(arrow, new ItemStack(Items.DIAMOND_SWORD));
        ItemStack afterLaterGuess = ShotWeapon.INSTANCE.weaponFor(world.getDamageSources().arrow(arrow, zombie));

        LOGGER.info("[FST-GameTest] shot weapon overwrite: resolved={} (expect bow), after later guess={} "
                + "(expect bow)", resolved, afterLaterGuess);

        context.assertTrue(resolved != null && resolved.isOf(Items.BOW),
                "the constructor's weapon must replace the hand guess, got " + resolved);
        context.assertTrue(afterLaterGuess != null && afterLaterGuess.isOf(Items.BOW),
                "a later hand guess must not overwrite the recorded weapon, got " + afterLaterGuess);
        context.complete();
    }

    /**
     * Why `arcane_echo`'s attribute modifiers are persistent: vanilla reads the `attributes` tag **before**
     * `Health` (`LivingEntity#readCustomDataFromNbt`), so a max-health bonus living only in a temporary
     * modifier is not there yet when the saved health is applied — vanilla clamps the save down to the base
     * 20 on every re-join. This writes an entity to NBT and reads it back: the bonus must survive the round
     * trip, and the health written with it must come back too.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void persistentMaxHealthSurvivesReloadSoHealthIsNotClamped(TestContext context) {
        World world = context.getWorld();
        ZombieEntity before = EntityType.ZOMBIE.create(world);
        context.assertTrue(before != null, "could not create a zombie without spawning it");
        if (before == null) {
            context.complete();
            return;
        }

        Identifier id = Identifier.of("simple_tweaks", "arcane_echo_health");
        before.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).addPersistentModifier(
                new EntityAttributeModifier(id, 20.0, EntityAttributeModifier.Operation.ADD_VALUE));
        before.setHealth(before.getMaxHealth());

        NbtCompound nbt = new NbtCompound();
        before.writeNbt(nbt);

        ZombieEntity after = EntityType.ZOMBIE.create(world);
        context.assertTrue(after != null, "could not create the reload target");
        if (after == null) {
            context.complete();
            return;
        }
        after.readNbt(nbt);

        LOGGER.info("[FST-GameTest] persistent max health: written max={} health={} -> reloaded max={} health={} "
                        + "(expect 40/40/40/40)",
                before.getMaxHealth(), before.getHealth(), after.getMaxHealth(), after.getHealth());

        context.assertTrue(Math.abs(before.getHealth() - 40.0f) < 0.01f,
                "a +20 max health modifier must let the zombie sit at 40, got " + before.getHealth());
        context.assertTrue(Math.abs(after.getMaxHealth() - 40.0f) < 0.01f,
                "the max health bonus must be written to the attributes tag, got " + after.getMaxHealth());
        context.assertTrue(Math.abs(after.getHealth() - 40.0f) < 0.01f,
                "saved health must not be clamped to the base 20 on reload, got " + after.getHealth());

        // The same round trip for a wounded entity: the point is the *current* health, not the max. 35 is
        // above the base 20, so a clamp would show up as 20 here.
        before.setHealth(35.0f);
        NbtCompound wounded = new NbtCompound();
        before.writeNbt(wounded);
        ZombieEntity woundedAfter = EntityType.ZOMBIE.create(world);
        context.assertTrue(woundedAfter != null, "could not create the wounded reload target");
        if (woundedAfter != null) {
            woundedAfter.readNbt(wounded);
            LOGGER.info("[FST-GameTest] current health on reload: written={} -> reloaded max={} health={} "
                            + "(expect 35/40/35)",
                    before.getHealth(), woundedAfter.getMaxHealth(), woundedAfter.getHealth());
            context.assertTrue(Math.abs(woundedAfter.getHealth() - 35.0f) < 0.01f,
                    "current health above the base 20 must survive a reload, got " + woundedAfter.getHealth());
        }
        context.complete();
    }

    /**
     * The player-power half of the level curve, pinned to the numbers `MobLevels` documents:
     * a brand-new world stays at level 1 (dead zone), and an enchantment's **rarity** weighs more than a
     * common one (`build=cleanup17`).
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void difficultyCurveRewardsGearQuality(TestContext context) {
        double common = PlayerPower.qualityWeight(10);
        double uncommon = PlayerPower.qualityWeight(5);
        double mythic = PlayerPower.qualityWeight(1);
        float naked = MobLevels.playerDifficultyTerm(0.1f);
        float iron = MobLevels.playerDifficultyTerm(1.2f);
        float endgame = MobLevels.playerDifficultyTerm(6.0f);

        LOGGER.info("[FST-GameTest] quality: common={} uncommon={} mythic={} (expect 1.0 < {} < {} <= 2.2); "
                        + "term: naked={} (expect 0), iron={} (expect {}), endgame={} (expect {})",
                common, uncommon, mythic, uncommon, mythic,
                naked, iron, (1.2f - 0.4f) * 1.6f, endgame, (6.0f - 0.4f) * 1.6f);

        context.assertTrue(Math.abs(common - 1.0) < 0.001, "a common enchantment is worth exactly 1.0, got " + common);
        context.assertTrue(uncommon > common, "an uncommon enchantment must outweigh a common one");
        context.assertTrue(mythic > uncommon, "a mythic enchantment must outweigh an uncommon one");
        context.assertTrue(mythic <= 2.2 + 0.001, "quality weight must stay capped at 2.2, got " + mythic);
        context.assertTrue(naked == 0f, "a naked player must add no difficulty at all, got " + naked);
        context.assertTrue(Math.abs(iron - 1.28f) < 0.01f,
                "1.2 power must convert to 1.28 difficulty (dead zone 0.4, weight 1.6), got " + iron);
        context.assertTrue(Math.abs(endgame - 8.96f) < 0.01f,
                "6.0 power must convert to 8.96 difficulty, got " + endgame);
        context.complete();
    }

    /**
     * `arcane_echo`'s numbers, pinned: the bonus is `units × effective level`; only movement speed carries a
     * ceiling (it changes how the game feels), everything else grows (`build=cleanup21`).
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void arcaneEchoScalesWithEnchantmentLevelsAndCaps(TestContext context) {
        ArcaneEcho.Stats idle = ArcaneEcho.statsOf(0, 0);
        ArcaneEcho.Stats mid = ArcaneEcho.statsOf(50, 3);
        ArcaneEcho.Stats top = ArcaneEcho.statsOf(60, 10);
        ArcaneEcho.Stats maxed = ArcaneEcho.statsOf(400, 3);

        LOGGER.info("[FST-GameTest] arcane echo: idle active={} (expect false); mid health={} (expect 21.6) "
                        + "element={} (expect 0.225) reaction={} (expect 0.315); maxed attackSpeed={} (expect 0.864) "
                        + "moveSpeed={} (expect 0.2) flat={} (expect 36.0) element={} (expect 1.8) reaction={} (expect 2.52)",
                idle.getActive(), mid.getHealth(), mid.getElement(), mid.getReaction(),
                maxed.getAttackSpeed(), maxed.getMoveSpeed(), maxed.getDamageReduction(),
                maxed.getElement(), maxed.getReaction());

        context.assertTrue(!idle.getActive(), "no arcane_echo level must mean no bonus at all");
        context.assertTrue(Math.abs(mid.getHealth() - 21.6) < 0.001,
                "50 units at level III must give 21.6 max health, got " + mid.getHealth());
        context.assertTrue(Math.abs(mid.getElement() - 0.225) < 0.001,
                "50 units at level III must give +22.5% elemental, got " + mid.getElement());
        context.assertTrue(Math.abs(mid.getReaction() - 0.315) < 0.001,
                "50 units at level III must give +31.5% reaction, got " + mid.getReaction());
        // 60 units at level X is the reference "maxed" set: the curve is tuned so ten levels pay far more
        // than the old level-III ceiling, which is the point of the climb.
        context.assertTrue(Math.abs(top.getHealth() - 86.4) < 0.001,
                "60 units at level X must give 86.4 max health, got " + top.getHealth());
        context.assertTrue(Math.abs(top.getFinalDamage() - 0.18) < 0.001,
                "60 units at level X must give +18% final damage, got " + top.getFinalDamage());
        context.assertTrue(Math.abs(top.getFinalReduction() - 0.41860464) < 0.001,
                "60 units at level X must give 41.86% final reduction, got " + top.getFinalReduction());
        context.assertTrue(Math.abs(top.getAttackSpeed() - 0.432) < 0.001,
                "60 units at level X must give +43.2% attack speed, got " + top.getAttackSpeed());
        context.assertTrue(Math.abs(maxed.getAttackSpeed() - 0.864) < 0.001,
                "attack speed must not cap (0.0012 x 400 x 3 x 0.6 = 0.864), got " + maxed.getAttackSpeed());
        context.assertTrue(Math.abs(maxed.getMoveSpeed() - 0.20) < 0.001,
                "movement speed must still cap at 20%, got " + maxed.getMoveSpeed());
        context.assertTrue(Math.abs(maxed.getDamageReduction() - 36.0) < 0.001,
                "flat damage reduction must not cap (0.05 x 400 x 3 x 0.6 = 36), got " + maxed.getDamageReduction());
        context.assertTrue(Math.abs(maxed.getElement() - 1.8) < 0.001,
                "elemental bonus must not cap (0.0025 x 400 x 3 x 0.6 = 1.8), got " + maxed.getElement());
        context.assertTrue(Math.abs(maxed.getReaction() - 2.52) < 0.001,
                "reaction bonus must not cap (0.0035 x 400 x 3 x 0.6 = 2.52), got " + maxed.getReaction());
        context.assertTrue(Math.abs(mid.getFinalDamage() - 0.045) < 0.001,
                "final damage must be 0.0005 x 50 x 3 x 0.6 = 0.045, got " + mid.getFinalDamage());
        context.assertTrue(Math.abs(mid.getFinalReduction() - 0.15254237) < 0.001,
                "final reduction must saturate: 0.09 / (0.09 + 0.5), got " + mid.getFinalReduction());
        context.assertTrue(Math.abs(mid.getRangedDamage() - 0.135) < 0.001,
                "ranged damage must be 0.0015 x 50 x 3 x 0.6 = 0.135, got " + mid.getRangedDamage());
        context.assertTrue(Math.abs(mid.getHealing() - 0.225) < 0.001,
                "healing must be 0.0025 x 50 x 3 x 0.6 = 0.225, got " + mid.getHealing());
        context.assertTrue(Math.abs(mid.getMiningSpeed() - 0.09) < 0.001,
                "mining speed must be 0.001 x 50 x 3 x 0.6 = 0.09, got " + mid.getMiningSpeed());
        context.assertTrue(Math.abs(top.getAttack() - 18.0) < 0.001,
                "attack must be 0.05 x 60 x 10 x 0.6 = 18, got " + top.getAttack());
        LOGGER.info("[FST-GameTest] arcane echo maxed: health={} (expect 86.4) finalDamage={} (expect 0.18) "
                        + "finalReduction={} (expect 0.41860464)", top.getHealth(), top.getFinalDamage(), top.getFinalReduction());
        context.complete();
    }
    /**
     * `experience_stealer` must pay out per *health removed*, not per damage number: a 100 000 damage hit on
     * a 100 health mob is one kill, not a thousand.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void experienceStealerPaysPerHealthRemoved(TestContext context) {
        float smallMob = dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExperienceStealerHandler.INSTANCE.stolenXp(100f, 100f, 8);
        float overkill = dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExperienceStealerHandler.INSTANCE.stolenXp(100_000f, 100f, 8);
        float chip = dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExperienceStealerHandler.INSTANCE.stolenXp(10f, 100f, 8);
        float warden = dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExperienceStealerHandler.INSTANCE.stolenXp(1024f, 1024f, 8);
        float unenchanted = dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExperienceStealerHandler.INSTANCE.stolenXp(100f, 100f, 0);

        LOGGER.info("[FST-GameTest] experience stealer: kill={} overkill={} (expect equal), chip={} (expect 1.2), "
                        + "warden={} (expect 67.44), level0={} (expect 0)",
                smallMob, overkill, chip, warden, unenchanted);

        context.assertTrue(Math.abs(smallMob - 12.0f) < 0.01f, "100/100 hp at level 8 must pay 12 xp, got " + smallMob);
        context.assertTrue(Math.abs(overkill - smallMob) < 0.01f, "overkill must not pay more than a clean kill");
        context.assertTrue(Math.abs(chip - 1.2f) < 0.01f, "10 of 100 hp must pay 1.2 xp, got " + chip);
        context.assertTrue(Math.abs(warden - 67.44f) < 0.05f, "a warden kill must pay ~67 xp, got " + warden);
        context.assertTrue(unenchanted == 0f, "no levels must pay nothing");
        context.complete();
    }
    private static void assertTaken(TestContext context, float damage, float armor, float expected) {
        float actual = ArmorCurve.INSTANCE.damageTaken(damage, armor, Math.min(armor, ArmorCurve.VANILLA_CAP));
        LOGGER.info("[FST-GameTest] damage {} armor {} -> taken {} (expected {})",
                damage, armor, actual, expected);
        context.assertTrue(Math.abs(actual - expected) < 0.01f,
                "armor " + armor + " vs " + damage + " damage should take " + expected
                        + " but took " + actual);
    }

    /**
     * Pins the whole chain: with no entry the attribute is unbounded (not "whatever the field says",
     * which is what lets a pack mod's 2169483647 ceiling survive), a typed ceiling wins, and clearing it
     * goes back to unbounded.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void attributeCapOverrideRaisesTheVanillaCeiling(TestContext context) {
        var zombie = new net.minecraft.entity.mob.ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE, context.getWorld());
        var instance = zombie.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
        instance.setBaseValue(5000.0);
        float unbounded = zombie.getMaxHealth();

        dev.firefly.simpletweaks.core.config.AttributeCapConfig.put(
                "minecraft:generic.max_health",
                new dev.firefly.simpletweaks.core.config.AttributeCapConfig.Cap(null, 4000.0));
        instance.setBaseValue(5001.0);
        float capped = zombie.getMaxHealth();

        dev.firefly.simpletweaks.core.config.AttributeCapConfig.remove("minecraft:generic.max_health");
        instance.setBaseValue(1.0e10);
        float huge = zombie.getMaxHealth();

        LOGGER.info("[FST-GameTest] attribute cap: unbounded={}, capped={}, huge={} (expect 5000/4000/1.0E10)",
                unbounded, capped, huge);

        context.assertEquals(5000.0f, unbounded, "no entry must mean no ceiling, so a 5000 base survives");
        context.assertEquals(4000.0f, capped, "a typed ceiling must squeeze the base back to 4000");
        context.assertTrue(huge > 2.1694836e9f,
                "clearing the entry must go past Integer.MAX_VALUE, got " + huge);

        context.complete();
    }

    /**
     * The stats screen shows one number per axis, so the axes have to be combined the way the
     * handlers apply them: as factors, not as a sum of percentages. A +100 % and a +100 % are
     * x4 together, and Heavenly Punishment is a factor on the same swing rather than a bonus.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void combatStatsCombineFactors(TestContext context) {
        double doubled = dev.firefly.simpletweaks.util.CombatStats.combine(1.0, 1.0);
        double nested = dev.firefly.simpletweaks.util.CombatStats.combine(doubled, 0.5);
        double heavenly = dev.firefly.simpletweaks.util.CombatStats.heavenlyMultiplier(2);

        LOGGER.info("[FST-GameTest] combat stats: doubled={}, nested={}, heavenly={} (expect 3.0/5.0/3.0)",
                doubled, nested, heavenly);

        context.assertTrue(Math.abs(doubled - 3.0) < 1.0e-9, "two +100 % factors are x4, not +200 %, got " + doubled);
        context.assertTrue(Math.abs(nested - 5.0) < 1.0e-9, "a further +50 % makes x6 total, i.e. a +500 % bonus, got " + nested);
        context.assertTrue(Math.abs(heavenly - 3.0) < 1.0e-9, "level 2 pays two extra swings, so x3, got " + heavenly);

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void devourFeedsByVictimSizeWithinAFixedCap(TestContext context) {
        int chicken = EnchantDevourHandler.stacksGained(4.0f);
        int zombie = EnchantDevourHandler.stacksGained(20.0f);
        int warden = EnchantDevourHandler.stacksGained(1024.0f);
        int giant = EnchantDevourHandler.stacksGained(100_000.0f);
        float full = EnchantDevourHandler.multiplierFor(15);
        float none = EnchantDevourHandler.multiplierFor(0);

        LOGGER.info("[FST-GameTest] devour: chicken=" + chicken + " zombie=" + zombie
                + " warden=" + warden + " giant=" + giant
                + " full=x" + full + " none=x" + none);

        context.assertEquals(1, chicken, "a 4-health chicken is one stack");
        context.assertEquals(1, zombie, "a 20-health zombie is still one stack");
        context.assertEquals(4, warden, "1024 health is 1 + 4, clamped to 4");
        context.assertEquals(4, giant, "no victim feeds more than four stacks");
        context.assertTrue(Math.abs(full - 2.2f) < 1.0e-5, "15 stacks at 8 % is x2.2, got " + full);
        context.assertEquals(1.0f, none, "no stacks is no bonus");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void greaterVitalityScalesAsAnUpgradedStep(TestContext context) {
        double full = EnchantGreaterVitalityHandler.modifierValue(80);
        double ten = EnchantGreaterVitalityHandler.modifierValue(10);

        LOGGER.info("[FST-GameTest] greater vitality: full=" + full + " ten=" + ten);

        // Four pieces at II count 10 + 10 = 20 ladder levels each, i.e. 80 in total.
        context.assertTrue(Math.abs(full - 1848.0) < 1.0e-9, "(8 x 80 + 25 x 8) x 2.2 = 1848, got " + full);
        context.assertTrue(Math.abs(ten - 120.75) < 1.0e-9, "(8 x 10 + 25) x 1.15 = 120.75, got " + ten);
        context.assertEquals(1.0, EnchantGreaterVitalityHandler.scaleFor(0), "no levels means no scaling");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void greaterArmorScalesAsAnUpgradedStep(TestContext context) {
        double armor = EnchantGreaterArmorHandler.armorValue(64);
        double toughness = EnchantGreaterArmorHandler.toughnessValue(64);

        LOGGER.info("[FST-GameTest] greater armor: armor=" + armor + " toughness=" + toughness);

        context.assertTrue(Math.abs(armor - 439.04) < 1.0e-9, "3.5 x 64 x 1.96 = 439.04, got " + armor);
        context.assertTrue(Math.abs(toughness - 351.232) < 1.0e-9, "2.8 x 64 x 1.96 = 351.232, got " + toughness);
        context.assertEquals(0.0, EnchantGreaterArmorHandler.armorValue(0), "no levels means no armour");
        context.assertEquals(1.0, EnchantGreaterArmorHandler.scaleFor(0), "no levels means no scaling");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void advancedEnchantModeChargesTheDisplayedPower(TestContext context) {
        int cheap = AdvancedEnchantMode.lapisCost(1);
        int mid = AdvancedEnchantMode.lapisCost(30);
        int huge = AdvancedEnchantMode.lapisCost(300);
        LOGGER.info("[FST-GameTest] advanced cost: xp(30)={} lapis: cheap={} mid={} huge={} (expect 30/1/10/64)",
                AdvancedEnchantMode.xpCost(30), cheap, mid, huge);
        LOGGER.info("[FST-GameTest] advanced budget: 29={} 30={} 64={} (expect 0/1/2)",
                AdvancedEnchantMode.advancedBudget(29), AdvancedEnchantMode.advancedBudget(30),
                AdvancedEnchantMode.advancedBudget(64));
        context.assertTrue(AdvancedEnchantMode.advancedBudget(29) == 0, "under 30 power no advanced entry fits");
        context.assertTrue(AdvancedEnchantMode.advancedBudget(30) == 1, "thirty power allows one");
        context.assertTrue(AdvancedEnchantMode.advancedBudget(64) == 2, "sixty-four allows two");
        context.assertTrue(AdvancedEnchantMode.xpCost(30) == 30, "xp cost should equal the power");
        context.assertTrue(cheap == 1, "one level costs one lapis");
        context.assertTrue(mid == 10, "thirty levels cost ten lapis");
        context.assertTrue(huge == 64, "the lapis cost is capped at a stack");
        context.complete();
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void upgradeLadderReplacesItsBase(TestContext context) {
        UpgradeEnchantments.Rule rule = UpgradeEnchantments.INSTANCE
                .ruleOf(key("greater_vitality"));
        if (rule == null) {
            context.throwGameTestException("greater_vitality must sit on the upgrade ladder");
            return;
        }

        ItemStack base = new ItemStack(Items.DIAMOND_CHESTPLATE);
        base.addEnchantment(
                entry(context, "vitality"),
                10);
        ItemStack upgraded = new ItemStack(Items.DIAMOND_CHESTPLATE);
        upgraded.addEnchantment(
                entry(context, "greater_vitality"),
                1);

        int baseLadder = UpgradeEnchantments.INSTANCE.ladderLevel(base, rule);
        int upgradeLadder = UpgradeEnchantments.INSTANCE.ladderLevel(upgraded, rule);
        int echo = UpgradeEnchantments.INSTANCE.echoLevel(key("greater_vitality"), 1);

        LOGGER.info("[FST-GameTest] upgrade ladder: base=" + baseLadder + " upgraded=" + upgradeLadder
                + " echo=" + echo);

        context.assertEquals(10, baseLadder, "vitality X alone is 10 ladder levels");
        context.assertEquals(11, upgradeLadder, "greater_vitality I replaces vitality X and adds one");
        context.assertEquals(11, echo, "Arcane Echo sees eleven levels");

        // A book may climb the ladder only when the item already holds the base at its maximum.
        ItemStack bookSlot = new ItemStack(Items.ENCHANTED_BOOK);
        bookSlot.addEnchantment(
                entry(context, "greater_vitality"),
                1);
        ItemStack tooLow = new ItemStack(Items.DIAMOND_CHESTPLATE);
        tooLow.addEnchantment(
                entry(context, "vitality"),
                3);
        ItemStack illegal = tooLow.copy();
        illegal.addEnchantment(
                entry(context, "greater_vitality"),
                1);
        UpgradeEnchantments.INSTANCE.sanitizeAnvilResult(illegal, tooLow, bookSlot);
        int illegalLevel = UpgradeEnchantments.INSTANCE.ladderLevel(illegal, rule);

        ItemStack legal = base.copy();
        legal.addEnchantment(
                entry(context, "greater_vitality"),
                1);
        UpgradeEnchantments.INSTANCE.sanitizeAnvilResult(legal, base, bookSlot);
        int legalLevel = UpgradeEnchantments.INSTANCE.ladderLevel(legal, rule);

        LOGGER.info("[FST-GameTest] upgrade ladder anvil: illegal=" + illegalLevel + " legal=" + legalLevel);

        context.assertEquals(3, illegalLevel, "vitality III must not be upgraded by a book");
        context.assertEquals(11, legalLevel, "vitality X is upgraded and consumed");

        context.complete();
    }

    private static RegistryEntry<Enchantment> entry(TestContext context, String id) {
        return context.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT)
                .getEntry(key(id)).orElseThrow();
    }

    private static RegistryKey<Enchantment> key(String id) {
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(SimpleTweaks.MOD_ID, id));
    }
}
