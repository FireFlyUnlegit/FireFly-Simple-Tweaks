package dev.firefly.simpletweaks.util

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack

/**
 * The weapon stack that fired [this] projectile, falling back to [shooter]'s main hand.
 *
 * <h2>Why this exists (`build=cleanup27`)</h2>
 * Five bow enchantments (`tracking_arrow` / `piercing_arrow` / `starfall` / `echo_shot` /
 * `hunters_mark`) used to read `shooter.mainHandStack` when the arrow spawned or landed. That is
 * wrong twice over:
 *
 * 1. **Off-hand bows were silently dead.** All seven BOW enchantments declare
 *    `slots = [MAINHAND, OFFHAND]`, and vanilla's own shot path is hand-agnostic
 *    (`BowItem#onStoppedUsing` receives the used stack), so a bow in the off-hand fires normally —
 *    but the handler then read the *main* hand, found no enchantment, and returned early. No log, no
 *    error: exactly the "没生效 vs 没触发" trap in `AGENTS.md` §4.4.
 * 2. **Even a main-hand bow could be lost before the effect resolved.** `echo_shot` / `starfall` /
 *    `hunters_mark` read the hand on the *hit*, so swapping to a sword mid-flight dropped the effect.
 *
 * <p>Vanilla already records the answer on the arrow: `RangedWeaponItem#shootAll` passes the used
 * weapon into `ArrowItem#createArrow(world, ammo, shooter, weapon)`, which stores it in
 * `PersistentProjectileEntity`'s `weapon` field — the very field `getDamage()` uses for
 * `minecraft:damage` enchantment effects. That field is therefore the authoritative "which bow fired
 * this" and is what every bow handler must read.
 *
 * <p>The main-hand fallback only covers projectiles whose weapon field is empty (spawned by something
 * other than a ranged weapon); it keeps the previous behaviour for those instead of dropping it.
 *
 * <p>1.12.2 read the main hand here too, so this is a deliberate **deviation** from the 1.12.2 code —
 * the original simply could not support an off-hand bow either.
 */
fun PersistentProjectileEntity.firingWeapon(shooter: LivingEntity): ItemStack {
    val weapon = getWeaponStack()
    if (weapon != null && !weapon.isEmpty) {
        return weapon
    }
    return shooter.mainHandStack
}
