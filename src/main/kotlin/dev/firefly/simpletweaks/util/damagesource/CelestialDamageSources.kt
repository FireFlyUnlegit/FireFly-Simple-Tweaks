package dev.firefly.simpletweaks.util.damagesource

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * 1:1 port of 1.12.2 `util/damagesource/CelestialDamageSources.kt` (18 lines).
 *
 * <h2>1.12.2 original</h2>
 * <pre>
 *   fun dealDamage(attacker: EntityPlayer) = object : DamageSource("celestial_particle") {
 *       override fun getTrueSource() = attacker
 *       override fun getImmediateSource() = attacker
 *       override fun isMagicDamage() = true
 *       override fun isUnblockable() = true
 *   }
 * </pre>
 *
 * <h2>What changed: `DamageSource` is an interface in 1.21</h2>
 * 1.12.2 could subclass `DamageSource` and override behaviour per instance. In 1.21 a
 * [DamageSource] is just a `(DamageType, sourceEntity, attackerEntity)` triple, and everything the
 * overrides expressed has to live in the **damage type**, which is a registry entry backed by a JSON
 * file and vanilla tags. So the anonymous class becomes:
 *
 * | 1.12.2 override | 1.21 equivalent |
 * |---|---|
 * | `getTrueSource()` / `getImmediateSource()` | `DamageSources#create(key, attacker)` — the same entity as both, matching 1.12.2 |
 * | `isUnblockable() = true` | `#minecraft:bypasses_armor` **and** `#minecraft:bypasses_shield` |
 * | `isMagicDamage() = true` | `#minecraft:witch_resistant_to` **and** `#minecraft:avoids_guardian_thorns` |
 * | — (author-requested addition, **not** a 1.12.2 behaviour) | `#minecraft:bypasses_resistance` — see §"Deliberate deviation" |
 * | death message | `message_id: "celestial_particle"`, which resolves to this mod's own `death.attack.celestial_particle` / `.player` lang keys (already present in `en_us`/`zh_cn`) |
 *
 * <h2>How both flags were resolved (bytecode, not inference)</h2>
 * Read out of the recompiled 1.12.2 Minecraft jar at
 * `build/rfg/recompiled_minecraft-1.12.2.jar`. Both `isUnblockable()` and `isMagicDamage()` are plain
 * getters over private fields, so the anonymous subclass's overrides are only meaningful through the
 * **consumers** — and a constant-pool scan of that jar gives the complete list.
 *
 * <p><b>`isUnblockable()` — 2 consumers:</b>
 * <table>
 *   <tr><th>consumer</th><th>effect</th><th>1.21 tag</th></tr>
 *   <tr><td>`EntityLivingBase#canBlockDamageSource`</td><td>shields do not block</td><td>`#minecraft:bypasses_shield`</td></tr>
 *   <tr><td>`EntityLivingBase#applyArmorCalculations`</td><td>armour step (and `damageArmor`) is skipped entirely</td><td>`#minecraft:bypasses_armor`</td></tr>
 * </table>
 *
 * <p><b>`isMagicDamage()` — 2 consumers, and *none* of them is the damage pipeline:</b>
 * <table>
 *   <tr><th>consumer</th><th>1.12.2 effect</th><th>1.21 tag</th></tr>
 *   <tr><td>`EntityWitch#applyPotionDamageCalculations`</td><td>`damage *= 0.15` — a witch takes 15% of magic damage</td><td>`#minecraft:witch_resistant_to`</td></tr>
 *   <tr><td>`EntityGuardian#attackEntityFrom`</td><td>the guardian does **not** retaliate with thorns against magic damage</td><td>`#minecraft:avoids_guardian_thorns`</td></tr>
 * </table>
 * The other two classes that mention the name are `DamageSource` (which declares it) and
 * `DamageSourcePredicate` (advancement conditions). In particular `applyArmorCalculations`,
 * `applyPotionDamageCalculations` and `EnchantmentProtection` **never call it** — so in 1.12.2 this
 * damage was **not** exempt from the Resistance potion or from Protection.
 *
 * <h2>Deliberate deviation: `#minecraft:bypasses_resistance` (author-requested)</h2>
 * Celestial damage is **additionally** added to `#minecraft:bypasses_resistance`, so the Resistance
 * potion no longer reduces it. 1.12.2 did not do this — the flag was resolved from its consumers and
 * none of them exempted it. This is recorded as a behaviour change, not presented as a port.
 *
 * <p><b>The tag that was NOT used, and why the distinction is real.</b> 1.21.1 has two overlapping
 * tags, and they are not synonyms. Read out of `LivingEntity#modifyAppliedDamage`:
 * <table>
 *   <tr><th>tag</th><th>1.21.1 effect (offsets)</th><th>vanilla members</th></tr>
 *   <tr><td>`#minecraft:bypasses_resistance`</td>
 *       <td>skips **only** the Resistance step (12..157); the Protection-enchantment step at
 *           165..224 still runs</td>
 *       <td>`out_of_world`, `generic_kill`</td></tr>
 *   <tr><td>`#minecraft:bypasses_effects`</td>
 *       <td>early-returns at 10..11, skipping the Resistance step **and** the Protection-enchantment
 *           step</td>
 *       <td>`starve` — and nothing else</td></tr>
 * </table>
 * So `bypasses_effects` is **wider** than what was asked for rather than equivalent: it would make
 * celestial damage ignore Protection IV too. `bypasses_resistance` expresses exactly "ignores the
 * Resistance potion" and nothing more, and this mod's own `simple_tweaks:reflection` type already
 * uses that tag for the same intent. `bypasses_enchantments` is still **not** added.
 *
 * <blockquote>An earlier revision of this file claimed `isMagicDamage()` affected
 * `applyPotionDamageCalculations` and left it unmapped as "unverifiable". That claim was wrong: the
 * flag has nothing to do with the potion step. The file was not modified on a guess either way — the
 * consumer list settled it.</blockquote>
 *
 * <h2>Both tags were then checked against the 1.21.1 vanilla data</h2>
 * `#minecraft:witch_resistant_to` ships `magic`, `indirect_magic`, `sonic_boom`, `thorns`, and
 * `#minecraft:avoids_guardian_thorns` ships `magic`, `thorns`, `#is_explosion` — i.e. both are exactly
 * the "this is magic damage" groupings the 1.12.2 consumers would have keyed off, so the mapping is
 * confirmed on both sides rather than only on the 1.12.2 side.
 *
 * <p>Note one redundancy, kept deliberately: vanilla's `#minecraft:bypasses_shield` already contains
 * `#minecraft:bypasses_armor`, so adding this type to `bypasses_armor` would transitively exempt it
 * from shields as well. The explicit `bypasses_shield` entry is retained so the intent is readable in
 * the mod's own data and does not silently depend on vanilla's tag composition.
 */
object CelestialDamageSources {

    val CELESTIAL_PARTICLE: RegistryKey<DamageType> =
        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "celestial_particle"))

    /**
     * Celestial (holy light) particle damage: attributed to [attacker] as both the immediate and the
     * true source, matching 1.12.2's two overrides.
     */
    @JvmStatic
    fun dealDamage(attacker: PlayerEntity): DamageSource =
        attacker.damageSources.create(CELESTIAL_PARTICLE, attacker)
}
