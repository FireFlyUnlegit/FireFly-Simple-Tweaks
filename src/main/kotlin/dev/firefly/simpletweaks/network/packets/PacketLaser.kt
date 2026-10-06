package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

/**
 * Client -&gt; server: fire the `infinite_power` laser along [direction].
 *
 * This is the project's **first C2S packet** — the other three are all S2C.
 *
 * <h2>Wire format, and the one field that was deliberately dropped</h2>
 * 1.12.2 wrote `int playerId` followed by three `double`s. The three doubles are kept in the same
 * order. The **`playerId` is not**: Forge's `MessageContext` did not carry a server-side player, so
 * the original had to ship the shooter's `entityId` and resolve it with
 * `world.getEntityByID(message.playerId)` — which also means a client could have claimed any id.
 * Fabric's `ServerPlayNetworking` context supplies the sender authoritatively, so the field is
 * redundant *and* a spoofing vector, and is dropped rather than reproduced.
 *
 * <p>Direction is the shooter's look vector, so the server never has to trust a target — the ray is
 * re-traced server-side in
 * [dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler.fireLaser].
 */
class PacketLaser(val direction: Vec3d) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketLaser> = ID

    companion object {

        val ID: CustomPayload.Id<PacketLaser> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "laser"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketLaser> = PacketCodec.of(
            { value: PacketLaser, buf: RegistryByteBuf ->
                buf.writeDouble(value.direction.x)
                buf.writeDouble(value.direction.y)
                buf.writeDouble(value.direction.z)
            },
            { buf: RegistryByteBuf ->
                PacketLaser(Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
            },
        )
    }
}
