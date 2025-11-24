package com.typewritermc.protection.selection

import com.typewritermc.core.utils.point.Position
import com.typewritermc.protection.selection.SelectionGeometry.verticalRange
import com.typewritermc.protection.settings.ProtectionMessageRenderer
import com.typewritermc.protection.settings.ProtectionMessages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

@Suppress("TooManyFunctions")
enum class SelectionMode {
    CUBOID,
    POLYGON;

    val minimumPoints: Int
        get() = when (this) {
            CUBOID -> 2
            POLYGON -> 3
        }

    fun next(): SelectionMode {
        val values = values()
        val index = values.indexOf(this)
        val nextIndex = (index + 1) % values.size
        return values[nextIndex]
    }

    fun displayName(messages: ProtectionMessages.ModeMessages): Component {
        val (template, fallback) = when (this) {
            CUBOID -> messages.cuboid.name to Component.text("Cuboid mode", NamedTextColor.GOLD)
            POLYGON -> messages.polygon.name to Component.text("Polygon mode", NamedTextColor.AQUA)
        }
        return ProtectionMessageRenderer.render(template) ?: fallback
    }

    fun description(messages: ProtectionMessages.ModeMessages): Component {
        val (template, fallback) = when (this) {
            CUBOID -> messages.cuboid.description to Component.text(
                "Two points define an axis-aligned volume",
                NamedTextColor.GRAY
            )
            POLYGON -> messages.polygon.description to Component.text(
                "Three or more points define a polygonal prism",
                NamedTextColor.GRAY
            )
        }
        return ProtectionMessageRenderer.render(template) ?: fallback
    }

    fun computeShape(nodes: List<Position>, range: Pair<Double, Double>? = null): RegionShape? {
        if (nodes.size < minimumPoints) return null
        val world = nodes.firstOrNull()?.world ?: return null
        if (nodes.any { it.world != world }) return null
        val (minY, maxY) = range ?: nodes.verticalRange()
        return when (this) {
            CUBOID -> {
                val minX = nodes.minOf { it.x }
                val minZ = nodes.minOf { it.z }
                val maxX = nodes.maxOf { it.x }
                val maxZ = nodes.maxOf { it.z }
                val min = Position(world, minX, minY, minZ)
                val max = Position(world, maxX, maxY, maxZ)
                CuboidShape(min, max)
            }
            POLYGON -> {
                if (nodes.size < 3) return null
                val base = nodes.map { Position(world, it.x, minY, it.z) }
                PolygonPrismShape(minY, maxY, base)
            }
        }
    }

    companion object {
        fun fromShape(shape: RegionShape): SelectionMode = when (shape) {
            is CuboidShape -> CUBOID
            is PolygonPrismShape, is FlatPolygonShape, is CylinderShape, is GlobalRegionShape -> POLYGON
        }
    }
}

object SelectionGeometry {
    fun Collection<Position>.verticalRange(): Pair<Double, Double> {
        if (isEmpty()) return 0.0 to 0.0
        val minY = minOf { it.y }
        val maxY = maxOf { it.y }
        return minY to maxY
    }
}

