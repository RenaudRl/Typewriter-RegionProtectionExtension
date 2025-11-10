package com.typewritermc.protection.selection

import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val EDGE_STEP = 0.5
private val DUST_PARTICLE: Particle = resolveDustParticle()

private fun resolveDustParticle(): Particle {
    fun has(name: String) = Particle.entries.any { it.name.equals(name, ignoreCase = true) }
    return when {
        has("DUST") -> Particle.valueOf("DUST")
        has("REDSTONE") -> Particle.valueOf("REDSTONE")
        has("REDSTONE_DUST") -> Particle.valueOf("REDSTONE_DUST")
        else -> Particle.END_ROD
    }
}

class SelectionVisualizer(
    private val plugin: Plugin,
    private val player: Player,
) {
    private var task: BukkitTask? = null
    private var cachedMode: SelectionMode? = null
    private var cachedNodes: List<Position> = emptyList()
    private var cachedRange: Pair<Double, Double>? = null

    fun update(mode: SelectionMode, nodes: List<Position>, range: Pair<Double, Double>?) {
        cachedMode = mode
        cachedNodes = nodes
        cachedRange = range
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { render() }, 10L, 10L)
        }
    }

    fun clear() {
        task?.cancel()
        task = null
        cachedMode = null
        cachedNodes = emptyList()
        cachedRange = null
    }

    private fun render() {
        if (!player.isOnline) return
        val mode = cachedMode ?: return
        val nodes = cachedNodes
        val shape = mode.computeShape(nodes, cachedRange)
        if (shape == null) {
            if (nodes.isEmpty()) clear() else return
            return
        }
        when (shape) {
            is CuboidShape -> renderCuboid(shape)
            is PolygonPrismShape -> renderPolygon(shape.minY, shape.maxY, shape.vertices, shape.vertices.firstOrNull()?.world)
            is FlatPolygonShape -> renderPolygon(shape.y, shape.y, shape.vertices, shape.vertices.firstOrNull()?.world)
            is CylinderShape -> renderCylinder(shape)
            else -> {}
        }
    }

    private fun renderCuboid(shape: CuboidShape) {
        val dust = prepare(shape.min().world, Color.fromRGB(46, 204, 113)) ?: return
        val particle = DUST_PARTICLE
        val min = shape.min()
        val max = shape.max()
        val minX = min.x
        val maxX = max.x
        val minY = min.y
        val maxY = max.y
        val minZ = min.z
        val maxZ = max.z

        fun spawn(x: Double, y: Double, z: Double) {
            player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }

        var x = minX
        while (x <= maxX) {
            spawn(x, minY, minZ)
            spawn(x, minY, maxZ)
            spawn(x, maxY, minZ)
            spawn(x, maxY, maxZ)
            x += EDGE_STEP
        }
        var z = minZ
        while (z <= maxZ) {
            spawn(minX, minY, z)
            spawn(maxX, minY, z)
            spawn(minX, maxY, z)
            spawn(maxX, maxY, z)
            z += EDGE_STEP
        }
        var y = minY
        while (y <= maxY) {
            spawn(minX, y, minZ)
            spawn(minX, y, maxZ)
            spawn(maxX, y, minZ)
            spawn(maxX, y, maxZ)
            y += EDGE_STEP
        }
    }

    private fun renderPolygon(minY: Double, maxY: Double, vertices: List<Position>, world: World?) {
        if (vertices.size < 2) return
        val dust = prepare(world, Color.fromRGB(52, 152, 219)) ?: return
        val particle = DUST_PARTICLE

        fun spawn(x: Double, y: Double, z: Double) {
            player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }

        val heightSteps = max(1, ((maxY - minY) / EDGE_STEP).roundToInt())
        vertices.forEachIndexed { index, vertex ->
            val next = vertices[(index + 1) % vertices.size]
            drawEdge(vertex.x, vertex.z, next.x, next.z) { x, z ->
                spawn(x, minY, z)
                spawn(x, maxY, z)
            }
            val startY = minY
            val step = (maxY - minY) / heightSteps
            var current = startY
            while (current <= maxY) {
                spawn(vertex.x, current, vertex.z)
                current += step
            }
        }
    }

    private fun renderCylinder(shape: CylinderShape) {
        val dust = prepare(shape.center.world, Color.fromRGB(155, 89, 182)) ?: return
        val particle = DUST_PARTICLE
        val radiusX = shape.radius
        val radiusZ = shape.radius
        val minY = shape.minY
        val maxY = shape.maxY
        val center = shape.center

        fun spawn(x: Double, y: Double, z: Double) {
            player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }

        val steps = 40
        for (i in 0 until steps) {
            val angle = 2 * Math.PI * i / steps
            val x = center.x + radiusX * kotlin.math.cos(angle)
            val z = center.z + radiusZ * kotlin.math.sin(angle)
            spawn(x, minY, z)
            spawn(x, maxY, z)
        }
        var y = minY
        while (y <= maxY) {
            spawn(center.x + radiusX, y, center.z)
            spawn(center.x - radiusX, y, center.z)
            spawn(center.x, y, center.z + radiusZ)
            spawn(center.x, y, center.z - radiusZ)
            y += EDGE_STEP
        }
    }

    private fun drawEdge(x1: Double, z1: Double, x2: Double, z2: Double, consumer: (Double, Double) -> Unit) {
        val dx = x2 - x1
        val dz = z2 - z1
        val length = max(abs(dx), abs(dz))
        val steps = max(1, (length / EDGE_STEP).roundToInt())
        for (i in 0..steps) {
            val t = i / steps.toDouble()
            consumer(x1 + dx * t, z1 + dz * t)
        }
    }

    private fun prepare(world: World?, color: Color): Particle.DustOptions? {
        val worldName = world?.identifier ?: player.world.name
        val bukkitWorld = Bukkit.getWorld(worldName) ?: Bukkit.getWorld(player.world.name) ?: return null
        if (player.world != bukkitWorld) return null
        return Particle.DustOptions(color, 1.1f)
    }
}
