package dev.firefly.simpletweaks.particle

import dev.firefly.simpletweaks.SimpleTweaks
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.particle.SimpleParticleType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/**
 * The mod's particle types.
 *
 * <h2>Why only a marker type, and not a parameterised `ParticleEffect`</h2>
 * 1.12.2 spawned the celestial ring by **constructing the particle instance directly**
 * (`mc.effectRenderer.addEffect(CelestialRingParticle(...))`) and passing the owner UUID, the angle
 * offset, the radius, the Y offset and the rotation speed as constructor arguments. None of that
 * travelled through the particle system — the parameters came from
 * [dev.firefly.simpletweaks.network.packets.PacketCelestialRing].
 *
 * <p>1.21 can do the same: `ParticleManager#addParticle(Particle)` accepts a ready-made particle.
 * The only thing a particle needs from the registry is a **sprite**, which comes from the
 * `SpriteProvider` handed to the client-side factory. So the type registered here is a plain
 * [SimpleParticleType] used purely to obtain that provider — see
 * [dev.firefly.simpletweaks.client.particle.CelestialRingParticles].
 *
 * <p>Instead of that, a custom `ParticleEffect` record could carry the five parameters and be spawned
 * through `world.addParticle(...)`. That was rejected deliberately: it would mean hand-writing a
 * `MapCodec` **and** a `PacketCodec` (roughly fifty lines of boilerplate) for data that this mod already
 * sends through its own packet, and the packet is the faithful path since it is what 1.12.2 used.
 *
 * <p>Registration is common (not client-only) because `Registries.PARTICLE_TYPE` is a common registry
 * and a dedicated server must be able to resolve the id if an effect is ever spawned server-side.
 */
object ModParticles {

    /** Gold sparkle that orbits a player — 1.12.2 `CelestialRingParticle`. */
    val CELESTIAL_RING: SimpleParticleType = FabricParticleTypes.simple()

    fun register() {
        Registry.register(
            Registries.PARTICLE_TYPE,
            Identifier.of(SimpleTweaks.MOD_ID, "celestial_ring"),
            CELESTIAL_RING,
        )
    }
}
