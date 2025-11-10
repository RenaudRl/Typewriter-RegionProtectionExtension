package com.typewritermc.protection.selection

import com.typewritermc.core.utils.point.Position

/** Serialized representation of a RegionShape stored inside snapshots. */
data class SerializedShape(
    val type: SerializedShapeType = SerializedShapeType.CUBOID,
    val corner1: Position? = null,
    val corner2: Position? = null,
    val minY: Double? = null,
    val maxY: Double? = null,
    val vertices: List<Position>? = null,
    val center: Position? = null,
    val radius: Double? = null,
    val flatY: Double? = null,
    val worlds: List<String>? = null,
)

enum class SerializedShapeType { CUBOID, POLYGON_PRISM, CYLINDER, FLAT_POLYGON, GLOBAL }

fun RegionShape.serialize(): SerializedShape = when (this) {
    is CuboidShape -> SerializedShape(
        type = SerializedShapeType.CUBOID,
        corner1 = corner1,
        corner2 = corner2,
    )
    is PolygonPrismShape -> SerializedShape(
        type = SerializedShapeType.POLYGON_PRISM,
        minY = minY,
        maxY = maxY,
        vertices = vertices
    )
    is CylinderShape -> SerializedShape(
        type = SerializedShapeType.CYLINDER,
        center = center,
        radius = radius,
        minY = minY,
        maxY = maxY,
    )
    is FlatPolygonShape -> SerializedShape(
        type = SerializedShapeType.FLAT_POLYGON,
        flatY = y,
        vertices = vertices
    )
    is GlobalRegionShape -> SerializedShape(
        type = SerializedShapeType.GLOBAL,
        minY = minY,
        maxY = maxY,
        worlds = worlds,
    )
}

fun SerializedShape?.toRegionShape(fallback: RegionShape): RegionShape {
    if (this == null) return fallback
    return when (type) {
        SerializedShapeType.CUBOID -> CuboidShape(corner1 ?: Position.ORIGIN, corner2 ?: Position.ORIGIN)
        SerializedShapeType.POLYGON_PRISM -> PolygonPrismShape(
            minY = minY ?: 0.0,
            maxY = maxY ?: 255.0,
            vertices = vertices ?: emptyList()
        )
        SerializedShapeType.CYLINDER -> CylinderShape(
            center = center ?: Position.ORIGIN,
            radius = radius ?: 5.0,
            minY = minY ?: 0.0,
            maxY = maxY ?: 255.0
        )
        SerializedShapeType.FLAT_POLYGON -> FlatPolygonShape(
            y = flatY ?: 64.0,
            vertices = vertices ?: emptyList()
        )
        SerializedShapeType.GLOBAL -> GlobalRegionShape(
            worlds = worlds ?: emptyList(),
            minY = minY ?: -64.0,
            maxY = maxY ?: 320.0,
        )
    }
}
