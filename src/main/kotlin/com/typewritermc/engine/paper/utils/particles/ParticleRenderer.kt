package com.typewritermc.engine.paper.utils.particles

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * Compatibility shim — replaces the engine's ParticleRenderer (not present in engine-paper 0.9.0)
 * using standard Bukkit/Paper particle API.
 */
object ParticleRenderer {

    @JvmStatic
    fun render(
        player: Player,
        location: Location,
        type: String,
        count: Int,
        offset: Vector,
        speed: Double,
        data: Any? = null,
    ) {
        val particle = try {
            Particle.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            return
        }
        player.spawnParticle(particle, location, count, offset.x, offset.y, offset.z, speed, data)
    }

    @JvmStatic
    fun render(
        world: World,
        location: Location,
        type: String,
        count: Int,
        offset: Vector,
        speed: Double,
        data: Any? = null,
    ) {
        val particle = try {
            Particle.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            return
        }
        world.spawnParticle(particle, location, count, offset.x, offset.y, offset.z, speed, data)
    }
}
