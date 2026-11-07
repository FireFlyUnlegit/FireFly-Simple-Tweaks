package dev.firefly.simpletweaks.gametest;

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments;
import dev.firefly.simpletweaks.util.mobs.LevelledMob;
import dev.firefly.simpletweaks.util.ArmorCurve;
import dev.firefly.simpletweaks.util.mobs.MobLevels;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        context.assertEquals(113, keys, "GeneratedEnchantments.KEYS should list all 113 enchantments");
        context.assertEquals(113, categories, "GeneratedEnchantments.CATEGORY should cover every id");
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

    private static void assertReduction(TestContext context, float damage, float armor, float expected) {
        float actual = ArmorCurve.INSTANCE.reductionFor(armor, damage);
        LOGGER.info("[FST-GameTest] damage {} armor {} -> reduction {} (expected {})",
                damage, armor, actual, expected);
        context.assertTrue(Math.abs(actual - expected) < 0.001f,
                "armor " + armor + " vs " + damage + " damage should reduce by " + expected
                        + " but was " + actual);
    }

    private static void assertTaken(TestContext context, float damage, float armor, float expected) {
        float actual = ArmorCurve.INSTANCE.damageTaken(damage, armor, Math.min(armor, ArmorCurve.VANILLA_CAP));
        LOGGER.info("[FST-GameTest] damage {} armor {} -> taken {} (expected {})",
                damage, armor, actual, expected);
        context.assertTrue(Math.abs(actual - expected) < 0.01f,
                "armor " + armor + " vs " + damage + " damage should take " + expected
                        + " but took " + actual);
    }

}
