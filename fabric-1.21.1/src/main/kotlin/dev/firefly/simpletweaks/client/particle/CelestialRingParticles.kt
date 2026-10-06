package dev.firefly.simpletweaks.client.particle

import dev.firefly.simpletweaks.particle.ModParticles
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.particle.ParticleFactory
import net.minecraft.client.particle.SpriteProvider
import net.minecraft.particle.SimpleParticleType
import java.util.UUID

/**
 * Client-side registration for [CelestialRingParticle], and the spawn helper the
 * [dev.firefly.simpletweaks.network.packets.PacketCelestialRing] receiver calls.
 *
 * <h2>Why the `SpriteProvider` is captured instead of being used through the factory</h2>
 * The ring particle needs five parameters (owner UUID, angle offset, radius, Y offset, rotation speed)
 * and 1.12.2 passed them straight to the constructor. 1.21's `ParticleFactory#createParticle` only
 * receives the six vanilla spawn values, so a factory alone cannot carry them — which is why the
 * obvious alternative is a custom `ParticleEffect` record with its own codecs.
 *
 * <p>Capturing the provider sidesteps that: the type is registered so that a `SpriteProvider` exists,
 * the provider is stored, and the receiver then builds the particle **directly** and hands it to
 * `ParticleManager#addParticle(Particle)`. That is the same shape as 1.12.2's
 * `effectRenderer.addEffect(new CelestialRingParticle(...))`, and it avoids ~50 lines of codec
 * boilerplate for parameters that already travel in this mod's own packet.
 *
 * <p>The factory returned to the registry is therefore never actually used for the ring; it exists
 * because the registry requires one. It returns a particle with harmless placeholder parameters rather
 * than throwing, so that a future caller who does go through the normal particle path gets a visible
 * (if un-orbiting) sparkle instead of a crash.
 */
object CelestialRingParticles {

    /** 1.12.2 spawned exactly 8 particles, one every 45 degrees. */
    private const val COUNT = 8

    private var sprites: SpriteProvider? = null

    fun register() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.CELESTIAL_RING) { provider ->
            sprites = provider
            ParticleFactory<SimpleParticleType> { _, world, _, _, _, _, _, _ ->
                CelestialRingParticle(world, provider, UUID(0L, 0L), 0.0, 1.0, 1.0, 0.05)
            }
        }
    }

    /** Spawns the ring around [ownerUuid]; called from the packet receiver, on the client thread. */
    fun spawnRing(client: MinecraftClient, ownerUuid: UUID) {
        val world = client.world ?: return
        val provider = sprites ?: return
        for (i in 0 until COUNT) {
            val angleOffset = i * (2 * Math.PI / COUNT)
            client.particleManager.addParticle(
                CelestialRingParticle(world, provider, ownerUuid, angleOffset, 1.0, 1.0, 0.05)
            )
        }
    }
}
