package com.typewritermc.protection.service.runtime

import com.typewritermc.core.utils.point.Position
import com.typewritermc.protection.flags.displayName
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.selection.CylinderShape
import com.typewritermc.protection.selection.FlatPolygonShape
import com.typewritermc.protection.selection.GlobalRegionShape
import com.typewritermc.protection.selection.PolygonPrismShape
import com.typewritermc.protection.selection.RegionShape
import com.typewritermc.protection.selection.SelectionMode
import com.typewritermc.protection.service.storage.RegionArtifactStorage
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.settings.ProtectionMessageRenderer
import com.typewritermc.protection.settings.ProtectionMessages
import net.kyori.adventure.text.Component
import java.text.DecimalFormat
import kotlin.math.abs

internal object RegionInfoFormatter {
    private val numberFormat = DecimalFormat("0.##")

    fun buildRegionInfoLines(region: RegionModel, messages: ProtectionMessages): List<Component> {
        val info = messages.commands.info
        val lines = mutableListOf<Component>()
        val name = displayName(region)

        ProtectionMessageRenderer.render(info.header, mapOf("name" to name))?.let(lines::add)
        ProtectionMessageRenderer.render(info.priority, mapOf("priority" to region.priority))?.let(lines::add)

        if (region.owners.isNotEmpty()) {
            ProtectionMessageRenderer.render(info.owners, mapOf("owners" to region.owners.joinToString()))?.let(lines::add)
        }
        if (region.members.isNotEmpty()) {
            ProtectionMessageRenderer.render(info.members, mapOf("members" to region.members.joinToString()))?.let(lines::add)
        }
        if (region.groups.isNotEmpty()) {
            ProtectionMessageRenderer.render(info.groups, mapOf("groups" to region.groups.joinToString()))?.let(lines::add)
        }

        val stored = region.artifact?.let { RegionArtifactStorage.load(it) }
        val effectiveShape = stored?.shape ?: region.shape
        val mode = stored?.mode ?: SelectionMode.fromShape(effectiveShape)

        ProtectionMessageRenderer.render(
            info.mode,
            mapOf("mode" to mode.displayName(messages.modes))
        )?.let(lines::add)

        ProtectionMessageRenderer.render(
            info.shape,
            mapOf("shape" to shapeDisplayName(effectiveShape, info))
        )?.let(lines::add)

        val bounds = effectiveShape.min() to effectiveShape.max()
        ProtectionMessageRenderer.render(
            info.bounds,
            mapOf(
                "min" to formatPosition(bounds.first),
                "max" to formatPosition(bounds.second)
            )
        )?.let(lines::add)

        lines += shapeDetails(effectiveShape, info)

        stored?.nodes?.takeIf { it.isNotEmpty() }?.let { nodes ->
            val suffix = if (nodes.size > 1) "s" else ""
            ProtectionMessageRenderer.render(
                info.nodes,
                mapOf(
                    "count" to nodes.size,
                    "plural_suffix" to suffix,
                    "points" to formatPositions(nodes)
                )
            )?.let(lines::add)
        }

        if (region.flags.isNotEmpty()) {
            ProtectionMessageRenderer.render(
                info.flags,
                mapOf("flags" to region.flags.joinToString { it.key.id })
            )?.let(lines::add)
        } else {
            ProtectionMessageRenderer.render(info.noFlags)?.let(lines::add)
        }

        return lines
    }

    private fun shapeDisplayName(
        shape: RegionShape,
        info: ProtectionMessages.CommandMessages.InfoMessages,
    ): String = when (shape) {
        is CuboidShape -> info.shapeNames.cuboid
        is PolygonPrismShape, is FlatPolygonShape -> info.shapeNames.polygonPrism
        is CylinderShape -> info.shapeNames.cylinder
        is GlobalRegionShape -> info.shapeNames.global
    }

    private fun shapeDetails(
        shape: RegionShape,
        info: ProtectionMessages.CommandMessages.InfoMessages,
    ): List<Component> {
        val details = mutableListOf<Component>()
        when (shape) {
            is CuboidShape -> {
                val bounds = shape.min() to shape.max()
                val dx = abs(bounds.second.x - bounds.first.x) + 1
                val dy = abs(bounds.second.y - bounds.first.y) + 1
                val dz = abs(bounds.second.z - bounds.first.z) + 1
                ProtectionMessageRenderer.render(
                    info.cuboidDimensions,
                    mapOf(
                        "x" to formatNumber(dx),
                        "y" to formatNumber(dy),
                        "z" to formatNumber(dz)
                    )
                )?.let(details::add)
            }
            is PolygonPrismShape -> {
                ProtectionMessageRenderer.render(
                    info.prismHeight,
                    mapOf(
                        "min_y" to formatNumber(shape.minY),
                        "max_y" to formatNumber(shape.maxY),
                        "height" to formatNumber(shape.maxY - shape.minY)
                    )
                )?.let(details::add)
                if (shape.vertices.isNotEmpty()) {
                    ProtectionMessageRenderer.render(
                        info.prismVertices,
                        mapOf(
                            "count" to shape.vertices.size,
                            "vertices" to formatVertices(shape.vertices)
                        )
                    )?.let(details::add)
                }
            }
            is FlatPolygonShape -> {
                ProtectionMessageRenderer.render(
                    info.flatAltitude,
                    mapOf("y" to formatNumber(shape.y))
                )?.let(details::add)
                if (shape.vertices.isNotEmpty()) {
                    ProtectionMessageRenderer.render(
                        info.prismVertices,
                        mapOf(
                            "count" to shape.vertices.size,
                            "vertices" to formatVertices(shape.vertices)
                        )
                    )?.let(details::add)
                }
            }
            is CylinderShape -> {
                ProtectionMessageRenderer.render(
                    info.cylinderCenter,
                    mapOf(
                        "center_x" to formatNumber(shape.center.x),
                        "center_z" to formatNumber(shape.center.z)
                    )
                )?.let(details::add)
                ProtectionMessageRenderer.render(
                    info.cylinderRadius,
                    mapOf(
                        "radius" to formatNumber(shape.radius),
                        "min_y" to formatNumber(shape.minY),
                        "max_y" to formatNumber(shape.maxY)
                    )
                )?.let(details::add)
            }
            is GlobalRegionShape -> {
                ProtectionMessageRenderer.render(
                    info.globalWorlds,
                    mapOf("worlds" to shape.worlds.joinToString())
                )?.let(details::add)
                ProtectionMessageRenderer.render(
                    info.globalAltitude,
                    mapOf(
                        "min_y" to formatNumber(shape.minY),
                        "max_y" to formatNumber(shape.maxY)
                    )
                )?.let(details::add)
            }
        }
        return details
    }

    private fun formatVertices(vertices: List<Position>, limit: Int = 6): String {
        if (vertices.isEmpty()) return "-"
        val preview = vertices.take(limit).joinToString { "${formatNumber(it.x)}, ${formatNumber(it.z)}" }
        val remaining = vertices.size - limit
        return if (remaining > 0) "$preview … (+$remaining)" else preview
    }

    private fun formatPositions(nodes: List<Position>, limit: Int = 6): String {
        if (nodes.isEmpty()) return "-"
        val preview = nodes.take(limit).joinToString { formatPosition(it) }
        val remaining = nodes.size - limit
        return if (remaining > 0) "$preview … (+$remaining)" else preview
    }

    private fun formatPosition(position: Position): String {
        return "${formatNumber(position.x)}, ${formatNumber(position.y)}, ${formatNumber(position.z)} (${position.world.identifier})"
    }

    private fun formatNumber(value: Double): String = numberFormat.format(value)
}
