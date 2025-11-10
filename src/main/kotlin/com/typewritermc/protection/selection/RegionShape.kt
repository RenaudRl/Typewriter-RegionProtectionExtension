package com.typewritermc.protection.selection

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.AlgebraicTypeInfo
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import com.typewritermc.engine.paper.content.modes.custom.PositionContentMode
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Represents the geometric dimension used for region selections. */
enum class SelectionDimension { TWO_D, THREE_D }

sealed interface RegionShape {
    val dimension: SelectionDimension

    fun contains(position: Position): Boolean

    fun min(): Position

    fun max(): Position
}

/** Marks shapes that can be edited locally (non-global). */

/** Axis-aligned cuboid shape used for most regions. */
@AlgebraicTypeInfo("region_shape_cuboid", Colors.BLUE, "mdi:cube")
data class CuboidShape(
    @Help("First corner of the cuboid")
    @ContentEditor(PositionContentMode::class)
    val corner1: Position = Position.ORIGIN,
    @Help("Second corner of the cuboid")
    @ContentEditor(PositionContentMode::class)
    val corner2: Position = Position.ORIGIN,
) : RegionShape {
    override val dimension: SelectionDimension = SelectionDimension.THREE_D

    private val minX = min(corner1.x, corner2.x)
    private val minY = min(corner1.y, corner2.y)
    private val minZ = min(corner1.z, corner2.z)
    private val maxX = max(corner1.x, corner2.x)
    private val maxY = max(corner1.y, corner2.y)
    private val maxZ = max(corner1.z, corner2.z)

    override fun contains(position: Position): Boolean {
        return position.x in minX..maxX &&
            position.y in minY..maxY &&
            position.z in minZ..maxZ
    }

    override fun min(): Position = Position(corner1.world, minX, minY, minZ)

    override fun max(): Position = Position(corner1.world, maxX, maxY, maxZ)
}

/** Polygon selection extruded between two heights. */
@AlgebraicTypeInfo("region_shape_polygon", Colors.PURPLE, "mdi:draw-polygon")
data class PolygonPrismShape(
    @Help("Floor height for the extrusion")
    val minY: Double = 0.0,
    @Help("Ceiling height for the extrusion")
    val maxY: Double = 255.0,
    @Help("Ordered list of vertices making up the polygon")
    @ContentEditor(PositionContentMode::class)
    val vertices: List<Position> = emptyList(),
) : RegionShape {
    override val dimension: SelectionDimension = SelectionDimension.THREE_D

    override fun contains(position: Position): Boolean {
        if (position.y < minY || position.y > maxY) return false
        if (vertices.size < 3) return false
        return isInsidePolygon(position)
    }

    private fun isInsidePolygon(position: Position): Boolean {
        var inside = false
        var j = vertices.lastIndex
        for (i in vertices.indices) {
            val vi = vertices[i]
            val vj = vertices[j]
            val intersects = ((vi.z > position.z) != (vj.z > position.z)) &&
                (position.x < (vj.x - vi.x) * (position.z - vi.z) / (vj.z - vi.z + 1e-7) + vi.x)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    override fun min(): Position {
        if (vertices.isEmpty()) return Position.ORIGIN
        val minX = vertices.minOf { it.x }
        val minZ = vertices.minOf { it.z }
        return Position(vertices.first().world, minX, minY, minZ)
    }

    override fun max(): Position {
        if (vertices.isEmpty()) return Position.ORIGIN
        val maxX = vertices.maxOf { it.x }
        val maxZ = vertices.maxOf { it.z }
        return Position(vertices.first().world, maxX, maxY, maxZ)
    }
}

/** Represents an upright cylinder. */
@AlgebraicTypeInfo("region_shape_cylinder", Colors.CYAN, "mdi:cylinder")
data class CylinderShape(
    @Help("Center of the cylinder")
    @ContentEditor(PositionContentMode::class)
    val center: Position = Position.ORIGIN,
    @Help("Radius in blocks")
    val radius: Double = 5.0,
    @Help("Minimum height of the cylinder")
    val minY: Double = 0.0,
    @Help("Maximum height of the cylinder")
    val maxY: Double = 255.0,
) : RegionShape {
    override val dimension: SelectionDimension = SelectionDimension.THREE_D

    override fun contains(position: Position): Boolean {
        if (position.y < minY || position.y > maxY) return false
        val dx = position.x - center.x
        val dz = position.z - center.z
        return dx * dx + dz * dz <= radius * radius
    }

    override fun min(): Position = Position(center.world, center.x - radius, minY, center.z - radius)

    override fun max(): Position = Position(center.world, center.x + radius, maxY, center.z + radius)
}

/** Simplified 2D polygon used for world-edit imports. */
@AlgebraicTypeInfo("region_shape_flat_polygon", Colors.AMBER, "mdi:vector-polygon")
data class FlatPolygonShape(
    @Help("Height at which the polygon is evaluated")
    val y: Double = 64.0,
    @Help("Vertices forming the polygon")
    @ContentEditor(PositionContentMode::class)
    val vertices: List<Position> = emptyList(),
) : RegionShape {
    override val dimension: SelectionDimension = SelectionDimension.TWO_D

    override fun contains(position: Position): Boolean {
        if (vertices.size < 3) return false
        return position.y == y && PolygonPrismShape(y, y, vertices).contains(position)
    }

    override fun min(): Position {
        if (vertices.isEmpty()) return Position.ORIGIN
        val polygon = PolygonPrismShape(y, y, vertices)
        val min = polygon.min()
        return Position(vertices.first().world, min.x, y, min.z)
    }

    override fun max(): Position {
        if (vertices.isEmpty()) return Position.ORIGIN
        val polygon = PolygonPrismShape(y, y, vertices)
        val max = polygon.max()
        return Position(vertices.first().world, max.x, y, max.z)
    }
}

/** Shape covering an entire world, used for global regions. */
@AlgebraicTypeInfo("region_shape_global", Colors.ORANGE, "mdi:earth")
data class GlobalRegionShape(
    @Help("World identifiers governed by this region")
    val worlds: List<String> = emptyList(),
    @Help("Minimum height included in the region")
    val minY: Double = DEFAULT_MIN_Y,
    @Help("Maximum height included in the region")
    val maxY: Double = DEFAULT_MAX_Y,
) : RegionShape {
    override val dimension: SelectionDimension = SelectionDimension.THREE_D

    private val normalizedWorlds = worlds.map { it.lowercase(Locale.ROOT) }.toSet()

    override fun contains(position: Position): Boolean {
        if (normalizedWorlds.isEmpty()) return false
        val worldId = position.world.identifier.lowercase(Locale.ROOT)
        if (worldId !in normalizedWorlds) return false
        return position.y in minY..maxY
    }

    override fun min(): Position = boundary(minY, MIN_COORDINATE)

    override fun max(): Position = boundary(maxY, MAX_COORDINATE)

    private fun boundary(y: Double, coordinate: Double): Position {
        val worldName = worlds.firstOrNull() ?: return Position.ORIGIN
        return Position(World(worldName), coordinate, y, coordinate)
    }

    companion object {
        private const val MIN_COORDINATE = -30_000_000.0
        private const val MAX_COORDINATE = 30_000_000.0
        private const val DEFAULT_MIN_Y = -64.0
        private const val DEFAULT_MAX_Y = 320.0
    }
}

