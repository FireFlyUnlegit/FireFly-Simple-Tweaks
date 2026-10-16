package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.EventSeams
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketResetAttackCooldown
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.random.Random

/**
 * Partial 1.21 port of `util/PlayerUtils.kt` — only the helpers the currently-ported handlers use.
 *
 * Deliberately NOT ported yet: `runVanillaAttack` / `applyDamage` / `hurtFeedback` / `canCrit`.
 * `applyDamage` / `hurtFeedback` are the hand-written 1.12.2 damage pipeline; see [runPlayerAttack]
 * for why 1.21 does not need them.
 *
 * `runPlayerAttack` and `attackCharge` were added with the legendary batch — both are documented
 * forced mappings, so read their KDoc before use. `EnchantAoeAttackHandler` (still in `deferred/`)
 * keeps its 1:1 call to [runPlayerAttack].
 */

/**
 * 1.12.2 `EntityPlayer.getRandomArmor()` read `inventory.armorInventory`. In 1.21 that is the
 * `PlayerInventory.armor` field (`field_7548`); `getArmorStack(index)` (`method_7372`) is used here
 * because it is the public accessor and avoids touching the `DefaultedList` directly.
 */
fun PlayerEntity.getRandomArmor(): ItemStack {
    val candidates = (0 until 4).map { this.inventory.getArmorStack(it) }.filter { !it.isEmpty }
    return if (candidates.isEmpty()) ItemStack.EMPTY else candidates[Random.nextInt(candidates.size)]
}

/**
 * 1.12.2 `EntityPlayer.tp(...)` delegated to `setPositionAndUpdate` on the server and `setPosition`
 * on the client. In 1.21 the server path is
 * `ServerPlayerEntity.teleport(ServerWorld, x, y, z, yaw, pitch)` (`method_14251`) — which also
 * syncs the position to the client, which matters for `EnchantVoidProtectionHandler` (it yanks the
 * player up out of the void) — and the client path is `Entity.requestTeleport(x, y, z)`
 * (`method_5859`).
 */
fun PlayerEntity.tp(
    x: Double = this.x,
    y: Double = this.y,
    z: Double = this.z,
    update: Boolean = true
) {
    if (update && this is ServerPlayerEntity) {
        this.teleport(this.serverWorld, x, y, z, this.yaw, this.pitch)
    } else {
        this.requestTeleport(x, y, z)
    }
}

// Armour accessors. The 1.12.2 file declared these twice (once for EntityLivingBase, once for
// EntityPlayer); since PlayerEntity IS a LivingEntity, one set of LivingEntity extensions covers both.
val LivingEntity.boots: ItemStack
    get() = this.getEquippedStack(EquipmentSlot.FEET)

val LivingEntity.leggings: ItemStack
    get() = this.getEquippedStack(EquipmentSlot.LEGS)

val LivingEntity.chestplate: ItemStack
    get() = this.getEquippedStack(EquipmentSlot.CHEST)

val LivingEntity.helmet: ItemStack
    get() = this.getEquippedStack(EquipmentSlot.HEAD)

/**
 * 1.12.2 `EntityLivingBase.displayedItem` (helmet / chestplate / leggings / boots / main hand /
 * off hand, empty stacks filtered out) — used by `EnchantItemFixerHandler`.
 *
 * The four armour accessors above are reused unchanged; only `heldItemMainhand` / `heldItemOffhand`
 * needed renaming, to `mainHandStack` (`method_6047`) / `offHandStack` (`method_6001`), both declared
 * on `LivingEntity` in Yarn 1.21.1.
 */
val LivingEntity.displayedItem: List<ItemStack>
    get() = listOf(
        this.helmet,
        this.chestplate,
        this.leggings,
        this.boots,
        this.mainHandStack,
        this.offHandStack
    ).filter { !it.isEmpty }

/**
 * 1.12.2 `EntityPlayer.syncAttributes()` sent `SPacketEntityProperties(entityId, allAttributes)`
 * down the player's own connection after a handler had mutated one of their attributes
 * (used by `EnchantExtraArmorHandler` / `EnchantVitalityHandler`).
 *
 * 1.21.1 has no `SPacketEntityProperties`. The replacement packet is
 * `EntityAttributesS2CPacket(int entityId, Collection<EntityAttributeInstance>)` (`method_12464`),
 * and `AttributeContainer.getAttributesToSend()` (`method_61993`) hands back exactly the collection
 * vanilla itself uses for that packet, so the body maps 1:1.
 *
 * ⚠️ Unlike 1.12.2, 1.21.1 *also* pushes tracked attribute instances automatically through
 * `EntityTrackerEntry`, so this explicit send is redundant with vanilla — it is kept only because
 * the 1.12.2 handlers call it and dropping it would be a (small) behavioural change.
 */
fun PlayerEntity.syncAttributes() {
    if (this is ServerPlayerEntity) {
        this.networkHandler.sendPacket(
            EntityAttributesS2CPacket(this.id, this.attributes.attributesToSend)
        )
    }
}

/**
 * 1.21 replacement for the mixin-captured `EntityPlayer.attackCharge`
 * (`interfaces/AttackChargeAccessor` + `mixin/MixinEntityPlayer.java`, which `@Redirect`-ed
 * `EntityPlayer#getCooledAttackStrength(float)` inside `attackTargetEntityWithCurrentItem`).
 *
 * The captured value was always that call with vanilla's constant `0.5F`, and 1.21's
 * `PlayerEntity#getAttackCooldownProgress(float)` (`method_7261`) is the renamed method with an
 * identical formula (`clamp((lastAttackedTicks + baseTime) / cooldownPeriod, 0, 1)` vs the 1.12.2
 * `clamp((ticksSinceLastSwing + adjustTicks) / cooldownPeriod, 0, 1)`). It is public, so the accessor
 * interface **and** the redirect mixin are both deleted — the value can simply be read.
 *
 * Timing note: `EnchantDoubleStrikeHandler` reads this from `AttackEntityEvent`, which Fabric fires
 * at the `HEAD` of `ServerPlayerEntity#attack(Entity)`; that method does not touch
 * `lastAttackedTicks` (verified in the yarn bytecode: the reset lives in `PlayerEntity#attack`, after
 * the cooldown read), so this returns the same number the 1.12.2 mixin captured.
 */
val PlayerEntity.attackCharge: Float
    get() = this.getAttackCooldownProgress(0.5f)

/**
 * 1.21 port of `EntityLivingBase.runPlayerAttack(...)` (1.12.2 `util/PlayerUtils.kt`, 75 lines).
 *
 * The 1.12.2 body re-created the vanilla damage pipeline by hand, because Forge's `attackEntityFrom`
 * did not post the events the handlers needed, in the order they needed them:
 *  1. pre-checks (alive / i-frames / creative),
 *  2. crit: `ForgeHooks.getCriticalHit` -> `CriticalHitEvent.damageModifier`,
 *  3. `AttackEntityEvent` -> `LivingAttackEvent` -> `LivingHurtEvent`,
 *  4. armour + potion calculations,
 *  5. `LivingDamageEvent`,
 *  6. its own `applyDamage` (absorption, health, i-frames, knockback/hurt feedback, death).
 *
 * In 1.21 the project's own seams already reproduce 3/4/5/6 inside
 * `LivingEntity#damage(DamageSource, float)`, in the same order:
 *  - `EntityDamageMixin` fires `LivingAttackEvent` + `LivingHurtEvent` at the head of `Entity#damage`
 *    (pre-armour) and rewrites the amount,
 *  - `LivingEntityApplyDamageMixin` fires `LivingDamageEvent` inside `applyDamage` (post-armour),
 *  - `LivingEntity#damage` itself does the armour/absorption/feedback/death work.
 * So the body collapses to step 1 plus `damage(source, damage)`.
 *
 * ⚠️ **Parameter semantics in 1.21 — every parameter below does something** (`build=cleanup21`; two
 * inert ones were deleted rather than kept as decoration):
 *
 * | parameter | 1.21 behaviour |
 * |---|---|
 * | `attacker` | the player the hit is attributed to: default source, crit roll, creative exemption |
 * | `rawDamage` | pre-armour damage, then the crit multiplier and (optionally) the attack charge |
 * | `forceHit` | `true` clears `timeUntilRegen` first, so i-frames cannot swallow the hit |
 * | `source` | the source to deal it with — **and where "ignores armour" lives** now, see below |
 * | `triggerEvent` | `false` suppresses `LivingAttackEvent`/`LivingHurtEvent`/`LivingDamageEvent` for this hit via [EventSeams.pushSuppressDamageEvents] |
 * | `allowCrit` | gates only the *vanilla* crit roll; the event still fires, so a handler may force one |
 * | `damageCreativePlayer` | `false` (default) refuses to damage a creative-mode player, as 1.12.2 did |
 * | `simulateCooldown` | `true` behaves like a manual swing: the damage is scaled by the attack charge, and the charge is consumed — on **both** sides, see [PacketResetAttackCooldown] |
 *
 * **Deleted, and why** (they were inert — "parameter exists" is not "something reads it"):
 *  1. **`lastHitKeepDamagePlayerSource`** only ever fed 1.12.2's hand-written `applyDamage`, where it
 *     chose the *death* source (`if (keepPlayerSource) causePlayerDamage(attacker) else source`). In
 *     1.21 the source a hit is dealt with **is** the source recorded for the death, so the switch has
 *     nothing left to decide: pass the source you want the kill credited to.
 *  2. **`ignoreArmorAndPotion`** cannot be honoured as a boolean without *overriding* the caller's
 *     `source`, because in 1.21 skipping armour/resistance is a property of the **damage type's tags**,
 *     not of the call. The capability is unchanged and lives in the data layer: use a source whose type
 *     is in `#minecraft:bypasses_armor` / `bypasses_resistance` / `bypasses_enchantments` (see
 *     `util/damagesource/ModDamageTypes.kt` — [dev.firefly.simpletweaks.util.damagesource.damageBypassingArmor]
 *     is exactly this for Echo Shield's reflection). Keeping both would be two ways to say one thing.
 *
 * ✅ **The crit block is honoured.** It calls [EventSeams.forgeCriticalHit], a reproduction of
 * `ForgeHooks.getCriticalHit`, so `allowCrit` and a handler's `damageModifier` both take effect.
 * `vanillaCrit` comes from [canCrit], a port of the 1.12.2 helper.
 *
 * ⚠️ **The return value is still approximate** (and nothing reads it today): 1.12.2 returned the
 * *applied* damage or `-1f` for "hit refused". `LivingEntity#damage` returns a boolean, so the `-1f`
 * sentinels are preserved exactly but a landed hit returns the pre-armour figure rather than the
 * post-absorption one.
 */
fun LivingEntity.runPlayerAttack(
    attacker: PlayerEntity,
    rawDamage: Number,
    forceHit: Boolean = true,
    source: DamageSource = attacker.damageSources.playerAttack(attacker),
    triggerEvent: Boolean = true,
    allowCrit: Boolean = true,
    damageCreativePlayer: Boolean = false,
    simulateCooldown: Boolean = false,
): Float {
    if (!this.isAlive) return 0f
    if (forceHit) this.timeUntilRegen = 0
    if (this.timeUntilRegen != 0) return -1f
    if (this is PlayerEntity && !damageCreativePlayer) {
        if (this.isCreative) return -1f
    }

    var damage = rawDamage.toFloat()

    // `simulateCooldown`: deal this hit the way a manual swing would. The whole formula is in
    // `PlayerEntity#attack`'s bytecode — `getAttackCooldownProgress(0.5F)`, then
    // `damage *= 0.2F + f * f * 0.8F`, then `resetLastAttackedTicks()`. [attackCharge] below is that
    // same `getAttackCooldownProgress(0.5f)` call, so the charge read here is vanilla's own value.
    if (simulateCooldown) {
        val charge = attacker.attackCharge
        damage *= 0.2f + charge * charge * 0.8f
    }

    // 1.12.2 `ForgeHooks.getCriticalHit` block. `allowCrit` gates only the *vanilla* part, exactly as in
    // 1.12.2: the event is still posted with vanillaCritical=false, so a handler may still force a crit.
    val vanillaCrit = allowCrit && attacker.canCrit()
    val critModifier = EventSeams.forgeCriticalHit(attacker, this, vanillaCrit)
    if (critModifier > 0f) damage *= critModifier

    // Vanilla consumes the charge *before* rolling the crit; here it is one statement later, on purpose:
    // `forgeCriticalHit` reads the charge live for `CriticalHitEvent`, and `EventSeams` documents that a
    // post-reset read returns ≈0.5 and breaks every `charge >= 0.848` gate. Resetting after the roll
    // keeps the event's value real while still spending the charge before the hit lands.
    if (simulateCooldown) {
        attacker.resetLastAttackedTicks()

        // ...and the spend has to be *announced*, because the charge is per-side state.
        //
        // `lastAttackedTicks` is a plain int that each side ticks and resets on its own player object;
        // it is not tracked/synced. Bytecode: vanilla clears the **client's** copy only from the
        // client's own attack path (`MinecraftClient#doAttack`,
        // `ClientPlayerInteractionManager#attackEntity` / `#cancelBlockBreaking`), and `InGameHud` draws
        // the attack indicator from `ClientPlayerEntity#getAttackCooldownProgress`.
        //
        // An enchantment swing is not that path: `EnchantKillAuraHandler` bails out on the client
        // (`WorldSide.isClient`) and hits from the server, so without this the server spends the swing
        // while the indicator keeps showing a full one -- and the player's next manual hit lands at
        // `0.2 + charge² × 0.8` of its damage against an indicator that said "ready".
        // See [dev.firefly.simpletweaks.network.packets.PacketResetAttackCooldown].
        if (attacker is ServerPlayerEntity) {
            // `INSTANCE`, never a fresh `PacketResetAttackCooldown()`: `PacketCodec.unit`'s encoder
            // asserts identity and kicks the sender otherwise (see that class's KDoc).
            NetworkManager.sendToClient(PacketResetAttackCooldown.INSTANCE, attacker)
        }
    }

    if (triggerEvent) {
        return if (this.damage(source, damage)) damage else 0f
    }

    // 1.12.2's `triggerEvent = false`: deal the damage without the mod's own handlers seeing it. The
    // scope is released in a `finally`, so an exception inside `damage()` cannot leave the bus blind.
    EventSeams.pushSuppressDamageEvents()
    try {
        return if (this.damage(source, damage)) damage else 0f
    } finally {
        EventSeams.popSuppressDamageEvents()
    }
}

/**
 * 1.12.2 `EntityPlayer.canCrit()` — the vanilla "would this swing crit" condition, used only by
 * [runPlayerAttack] (the vanilla attack path learns it from `@ModifyConstant` in
 * `PlayerEntityAttackMixin` instead of recomputing it).
 *
 * Field mapping: `onGround` → `isOnGround` (`method_24828`), `isOnLadder` → `isClimbing`
 * (`method_5797`), `isInWater` → `isTouchingWater` (`method_5799`), `isRiding` → `hasVehicle`
 * (`method_5765`), `isPotionActive(BLINDNESS)` → `hasStatusEffect(StatusEffects.BLINDNESS)`.
 *
 * Note `hasVehicle()` has no `get`/`is` prefix, so Kotlin cannot see it as a property — it stays a
 * call, exactly as documented in `EnchantSuperKnockbackHandler`.
 */
fun PlayerEntity.canCrit(): Boolean =
    this.fallDistance > 0.0f &&
        !this.isOnGround &&
        !this.isClimbing &&
        !this.isTouchingWater &&
        !this.hasStatusEffect(StatusEffects.BLINDNESS) &&
        !this.hasVehicle() &&
        !this.isSprinting

val LivingEntity.armorToughness: Double
    get() = this.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)
