package dev.firefly.simpletweaks.gametest;

import dev.firefly.simpletweaks.util.mobs.LevelledMob;
import dev.firefly.simpletweaks.util.mobs.MobLevels;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

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
 * <h2>How it runs</h2>
 * Only on demand: {@code ./gradlew runGametest} (the {@code gametest} run config in build.gradle) or
 * the {@code gametest} GitHub workflow, which is scheduled/manual and deliberately <b>not</b> part of
 * {@code build} — it needs a world, a server tick loop and the Minecraft EULA, none of which belong in
 * a per-push build.
 *
 * <h2>Why the test class ships in the jar</h2>
 * The {@code fabric-gametest} entrypoint has to be declared in this mod's own {@code fabric.mod.json},
 * so the class has to be in the same artifact. Fabric Loader only instantiates that entrypoint inside
 * the game-test runtime, so a normal client/server never touches it.
 */
public class SimpleTweaksGameTests implements FabricGameTest {

    /**
     * A mob that spawns must come out levelled, with the health the level curve promises.
     *
     * <p>Regression this guards: {@code v2.2.1} fixed mobs that never reached
     * {@code MobLevels.ensureLevel} at all (only mobs with equipment did). If that seam breaks again,
     * this test fails on the very first assertion instead of needing a player to notice level-1
     * zombies in a world where everything else scales.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void spawnedMobGetsALevelAndItsStats(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);

        int level = ((LevelledMob) zombie).getMobLevel();
        context.assertTrue(level >= 1, "a spawned mob must have a level, got " + level);

        // MobLevels.apply(): max_health = base * (1 + HEALTH_PER_LEVEL * (level - 1))
        double expectedHealth = 20.0 * (1.0 + MobLevels.HEALTH_PER_LEVEL * (level - 1));
        context.assertTrue(
                Math.abs(zombie.getMaxHealth() - expectedHealth) < 0.01,
                "max health " + zombie.getMaxHealth() + " does not follow the level curve for level "
                        + level + " (expected " + expectedHealth + ")");
    }

    /**
     * Damage must actually land, and a smaller hit inside the invulnerability window must be
     * discarded.
     *
     * <p>This is the vanilla gate the elemental riders have to work around (they save and restore
     * {@code timeUntilRegen} / {@code lastDamageTaken}). If a future change breaks that accounting in
     * a way that also disables the gate, "combo" behaviour comes back — observed in real play as
     * "元素伤害无视无敌帧" — and this test is where it should fail first.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void damageLandsAndIFramesGateTheNextHit(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
        zombie.setHealth(zombie.getMaxHealth());
        context.assertTrue(zombie.getHealth() > 0.0f, "the test zombie must spawn alive");

        float before = zombie.getHealth();
        boolean firstHit = zombie.damage(context.getWorld().getDamageSources().generic(), 4.0f);
        context.assertTrue(firstHit, "the first hit must be applied");
        context.assertTrue(zombie.getHealth() < before, "health must drop after the first hit");

        float afterFirstHit = zombie.getHealth();
        boolean secondHit = zombie.damage(context.getWorld().getDamageSources().generic(), 1.0f);
        context.assertFalse(secondHit, "a smaller hit inside the i-frame window must be discarded");
        context.assertEquals(afterFirstHit, zombie.getHealth(),
                "a discarded hit must not change health");
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
    }

    private static void assertLevel(TestContext context, float difficulty, int expected) {
        int actual = MobLevels.INSTANCE.averageLevelFor(difficulty);
        context.assertEquals(expected, actual,
                "averageLevelFor(" + difficulty + ") should be " + expected + " but was " + actual);
    }
}
