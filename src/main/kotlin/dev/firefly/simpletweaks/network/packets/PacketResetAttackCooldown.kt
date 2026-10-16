package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -&gt; client: spend *your* attack charge — the client half of
 * [dev.firefly.simpletweaks.util.runPlayerAttack]'s `simulateCooldown`.
 *
 * <h2>Why the server cannot do this alone</h2>
 * The attack charge is `PlayerEntity#lastAttackedTicks`, an ordinary `int` that each side ticks and
 * resets for **its own player object**; it is not a tracked/synced field. Bytecode
 * (`minecraft-merged-1.21.1-net.fabricmc.yarn…`, `javap -c`):
 * <pre>
 *   PlayerEntity#resetLastAttackedTicks()V        -> lastAttackedTicks = 0
 *   PlayerEntity#attack(Entity)V                  -> getAttackCooldownProgress(0.5F)
 *                                                    damage *= 0.2F + charge² × 0.8F
 *                                                    resetLastAttackedTicks()
 *   MinecraftClient#doAttack()Z                   -> ClientPlayerEntity.resetLastAttackedTicks()
 *   ClientPlayerInteractionManager#attackEntity   -> PlayerEntity.resetLastAttackedTicks()
 *   ClientPlayerInteractionManager#cancelBlockBreaking
 *                                                 -> ClientPlayerEntity.resetLastAttackedTicks()
 *   InGameHud                                     -> ClientPlayerEntity.getAttackCooldownProgress(F)
 * </pre>
 * So vanilla only ever clears the client's charge from the client's **own** attack path, and the HUD
 * attack indicator (the crosshair charge bar) reads the **client's** value.
 *
 * <p>An enchantment-driven swing is not that path. `EnchantKillAuraHandler` returns early on the
 * client (`WorldSide.isClient`) and deals its hit through `runPlayerAttack` on the server, so nothing
 * on the client ever resets its charge: the server spends the swing while the indicator keeps showing
 * a full one, and the player's next manual hit deals `0.2 + charge² × 0.8` of its damage against an
 * indicator that said "ready". This packet closes that gap.
 *
 * <h2>Wire format</h2>
 * Empty. The message *is* the event (1.12.2's payload-less `PacketOpenEnchantInfo` is the same idea),
 * and there is nothing to carry: the client resets its own local player and nobody else's.
 * [PacketCodec.unit] is the codec for exactly that shape.
 *
 * <h2>⚠️ It has to be a singleton — [PacketCodec.unit]'s encoder checks the value</h2>
 * `unit`'s encoder is not "write nothing": it asserts `value.equals(theInstanceItWasBuiltWith)` and
 * throws `IllegalStateException: Can't encode 'X@1dde5184', expected 'X@306b7db2'` otherwise
 * (`PacketCodec$7.encode` = `Object.equals` against its stored `field_48586` — i.e. **identity** for a
 * payload that does not override `equals`, which this one does not). Because that happens
 * **while encoding an outgoing packet**, the failure is not a silent no-op — it kicks the player:
 * <pre>
 *   [Netty Server IO #1/ERROR]: Error sending packet clientbound/minecraft:custom_payload
 *   io.netty.handler.codec.EncoderException: Failed to encode packet
 *       'clientbound/minecraft:custom_payload' (simple_tweaks:reset_attack_cooldown)
 *   Caused by: java.lang.IllegalStateException: Can't encode '…@1dde5184', expected '…@306b7db2'
 * </pre>
 * (That was `build=cleanup22`'s first cut, which built fine and only failed at the third gate —
 * see `AGENTS.md` §4.3.) Hence [INSTANCE] plus a **private constructor**: "send a fresh instance" is
 * now a compile error rather than a disconnect.
 */
class PacketResetAttackCooldown private constructor() : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketResetAttackCooldown> = ID

    companion object {

        val ID: CustomPayload.Id<PacketResetAttackCooldown> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "reset_attack_cooldown"))

        /** The only instance — the one [CODEC] encodes. See the class KDoc. */
        val INSTANCE = PacketResetAttackCooldown()

        /** No body, so the same instance is both what is sent and what is decoded back. */
        val CODEC: PacketCodec<RegistryByteBuf, PacketResetAttackCooldown> =
            PacketCodec.unit<RegistryByteBuf, PacketResetAttackCooldown>(INSTANCE)
    }
}
