package dev.firefly.simpletweaks.client.particle

import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.client.particle.SpriteBillboardParticle
import net.minecraft.client.particle.SpriteProvider
import net.minecraft.client.world.ClientWorld
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gold sparkle orbiting a player — port of 1.12.2 `client/particle/CelestialRingParticle.kt` (75 lines).
 *
 * <h2>1.12.2 → 1.21.1 mapping</h2>
 * | 1.12.2 (`ParticleSimpleAnimated`) | 1.21.1 ([SpriteBillboardParticle]) |
 * |---|---|
 * | `onUpdate()` | `tick()` |
 * | `particleAge` / `particleMaxAge` | `age` / `maxAge` |
 * | `posX/posY/posZ`, `prevPosX/…` | `x/y/z`, `prevPosX/…` |
 * | `setExpired()` | `markDead()` |
 * | `particleScale *= 0.7F` | `scale(0.7f)` |
 * | `canCollide = false` | `collidesWithWorld = false` |
 * | `setColorFade(0xFFD700)` | `setColor(1.0f, 0.8431f, 0.0f)` |
 * | `setAlphaF(a)` | `setAlpha(a)` (protected, hence usable from a subclass) |
 * | `setParticleTextureIndex(176 + frame)` | `setSpriteForAge(provider)` |
 * | `getFXLayer() = 0` | `getType()` = [ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT] |
 *
 * <h2>Deliberate differences</h2>
 * <ul>
 *   <li><b>The frame animation runs forwards, not backwards.</b> 1.12.2 computed the sprite index by
 *       hand as `8 - 1 - age * 8 / maxAge`, i.e. it walked its 8-frame strip in reverse. In 1.21 the
 *       frame is chosen by the `SpriteProvider`, which only walks forwards. Reversing it would mean
 *       calling `getSprite(int, int)` with an inverted age, which is provider-implementation-defined
 *       and fragile. The visible difference is the direction of the twinkle.</li>
 *   <li><b>The sprite is `minecraft:flash`</b> (see `assets/simple_tweaks/particles/celestial_ring.json`),
 *       an approximation of 1.12.2's `particles.png` index 176 strip. No new texture was added; if the
 *       look is wrong, that single JSON line is the only thing to change.</li>
 * </ul>
 *
 * <p>The orbit maths, the 40(+0..7) tick lifetime, the half-life alpha fade and the "first tick only
 * places the particle" quirk are all copied unchanged — including the quirk, because it is what keeps
 * the packet's own x/y/z (which are zero) from ever being visible.
 */
class CelestialRingParticle(
    world: ClientWorld,
    private val sprites: SpriteProvider,
    private val ownerUuid: UUID,
    private val angleOffset: Double,
    private val radius: Double,
    private val yOffset: Double,
    private val rotateSpeed: Double,
) : SpriteBillboardParticle(world, 0.0, 0.0, 0.0) {

    /** 1.12.2's `spawned` flag: the first tick only positions the particle. */
    private var placed = false

    init {
        this.setSprite(sprites)
        this.scale(0.7f)
        this.maxAge = 40 + this.random.nextInt(8)
        this.collidesWithWorld = false
        // 0xFFD700, split into components because 1.21 has no packed-colour setter.
        this.setColor(1.0f, 0.8431f, 0.0f)
    }

    override fun tick() {
        if (!placed) {
            placed = true
            snapToOwner()
            return
        }

        this.prevPosX = this.x
        this.prevPosY = this.y
        this.prevPosZ = this.z

        if (this.age++ >= this.maxAge) {
            markDead()
            return
        }

        if (this.age > this.maxAge / 2) {
            val fadeProgress = (this.age - this.maxAge / 2).toFloat() / (this.maxAge / 2).toFloat()
            this.setAlpha(1.0f - fadeProgress)
        }

        this.setSpriteForAge(this.sprites)

        if (!snapToOwner()) {
            markDead()
        }
    }

    /** Returns false when the owner is gone, which 1.12.2 treated as "expire". */
    private fun snapToOwner(): Boolean {
        val player = this.world.getPlayerByUuid(ownerUuid) ?: return false
        if (!player.isAlive) return false

        val time = player.age.toDouble()
        val angle = angleOffset + time * rotateSpeed
        this.x = player.x + radius * cos(angle)
        this.y = player.y + yOffset + sin(time * 0.1) * 0.15
        this.z = player.z + radius * sin(angle)

        if (!placed) {
            this.prevPosX = this.x
            this.prevPosY = this.y
            this.prevPosZ = this.z
        }
        return true
    }

    override fun getType(): ParticleTextureSheet = ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
}
