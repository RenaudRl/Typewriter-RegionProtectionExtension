package com.typewritermc.protection.service.storage

import com.google.gson.Gson
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.protection.entry.artifact.GlobalRegionArtifactEntry
import com.typewritermc.protection.entry.artifact.RegionArtifactEntry
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.selection.CylinderShape
import com.typewritermc.protection.selection.FlatPolygonShape
import com.typewritermc.protection.selection.GlobalRegionShape
import com.typewritermc.protection.selection.PolygonPrismShape
import com.typewritermc.protection.selection.RegionShape
import com.typewritermc.protection.selection.SelectionMode
import com.typewritermc.protection.selection.SelectionMode.Companion.fromShape
import com.typewritermc.protection.selection.SerializedShape
import com.typewritermc.protection.selection.serialize
import com.typewritermc.protection.selection.toRegionShape
import kotlinx.coroutines.runBlocking
import java.util.Locale
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

data class StoredRegionData(
    val mode: SelectionMode,
    val nodes: List<Position>,
    val shape: RegionShape,
)

internal object RegionArtifactStorage {
    private val logger = LoggerFactory.getLogger("RegionArtifactStorage")

    private fun gson(): Gson = KoinJavaComponent.get(Gson::class.java, named("dataSerializer"))

    fun load(artifact: RegionArtifactEntry): StoredRegionData? {
        return runCatching {
            val data = runBlocking { artifact.stringData() } ?: return null
            if (data.isBlank()) return null
            when (artifact) {
                is GlobalRegionArtifactEntry -> parseGlobalPayload(data, artifact)
                else -> parsePayload(data)
            }
        }.onFailure { error ->
            logger.warn("Failed to load region shape for artifact {}", artifact.artifactId, error)
        }.getOrNull()
    }

    fun save(artifact: RegionArtifactEntry, mode: SelectionMode, nodes: List<Position>) {
        if (artifact is GlobalRegionArtifactEntry) {
            saveGlobal(artifact)
            return
        }
        runCatching {
            val payload = PayloadV2(mode.name, nodes)
            val json = gson().toJson(payload)
            runBlocking { artifact.stringData(json) }
        }.onFailure { error ->
            logger.error("Failed to persist region shape for artifact {}", artifact.artifactId, error)
        }
    }

    fun initialize(artifact: RegionArtifactEntry) {
        when (artifact) {
            is GlobalRegionArtifactEntry -> initializeGlobal(artifact)
            else -> initializeRegion(artifact)
        }
    }

    private fun initializeRegion(artifact: RegionArtifactEntry) {
        val current = runCatching { runBlocking { artifact.stringData() } }.getOrNull()
        if (current.isNullOrBlank()) {
            save(artifact, SelectionMode.CUBOID, emptyList())
        }
    }

    private fun initializeGlobal(artifact: GlobalRegionArtifactEntry) {
        val current = runCatching { runBlocking { artifact.stringData() } }.getOrNull()
        if (current.isNullOrBlank()) {
            saveGlobal(artifact)
            return
        }
        val stored = parseGlobalPayload(current, artifact)
        val shape = stored?.shape as? GlobalRegionShape
        val desiredWorlds = artifact.resolvedWorlds()
        if (
            shape == null ||
            !shape.worlds.normalizedEquals(desiredWorlds) ||
            shape.minY != artifact.minY ||
            shape.maxY != artifact.maxY
        ) {
            saveGlobal(artifact)
        }
    }

    private fun saveGlobal(artifact: GlobalRegionArtifactEntry) {
        val payload = GlobalPayload(
            worlds = artifact.resolvedWorlds(),
            minY = artifact.minY,
            maxY = artifact.maxY,
        )
        runCatching {
            val json = gson().toJson(payload)
            runBlocking { artifact.stringData(json) }
        }.onFailure { error ->
            logger.error("Failed to persist global region artifact {}", artifact.artifactId, error)
        }
    }

    private fun parsePayload(data: String): StoredRegionData? {
        gson().fromJson(data, PayloadV2::class.java)?.let { payload ->
            val mode = SelectionMode.values().firstOrNull { it.name == payload.mode } ?: SelectionMode.CUBOID
            val nodes = payload.nodes ?: emptyList()
            val shape = mode.computeShape(nodes) ?: DEFAULT_SHAPE
            return StoredRegionData(mode, nodes, shape)
        }

        val legacy = gson().fromJson(data, PayloadLegacy::class.java) ?: return null
        val shape = legacy.shape?.toRegionShape(DEFAULT_SHAPE) ?: return null
        val nodes = legacy.nodes ?: deriveNodes(shape)
        val mode = SelectionMode.fromShape(shape)
        return StoredRegionData(mode, nodes, shape)
    }

    private fun parseGlobalPayload(data: String, fallback: GlobalRegionArtifactEntry): StoredRegionData? {
        val payload = runCatching { gson().fromJson(data, GlobalPayload::class.java) }.getOrNull()
        val worlds = payload?.resolvedWorlds()?.takeIf { it.isNotEmpty() } ?: fallback.resolvedWorlds()
        val minY = payload?.minY ?: fallback.minY
        val maxY = payload?.maxY ?: fallback.maxY
        val shape = GlobalRegionShape(worlds = worlds, minY = minY, maxY = maxY)
        return StoredRegionData(SelectionMode.POLYGON, emptyList(), shape)
    }

    private fun deriveNodes(shape: RegionShape): List<Position> {
        return when (shape) {
            is CuboidShape -> listOf(shape.min(), shape.max())
            is PolygonPrismShape -> shape.vertices.map { Position(it.world, it.x, shape.minY, it.z) }
            is FlatPolygonShape -> shape.vertices.map { Position(it.world, it.x, shape.y, it.z) }
            is CylinderShape -> {
                val world = shape.center.world
                val steps = 16
                (0 until steps).map { step ->
                    val angle = 2 * Math.PI * step / steps
                    val x = shape.center.x + shape.radius * kotlin.math.cos(angle)
                    val z = shape.center.z + shape.radius * kotlin.math.sin(angle)
                    Position(world, x, shape.minY, z)
                }
            }
            else -> emptyList()
        }
    }

    private val DEFAULT_SHAPE = CuboidShape()

    private data class PayloadLegacy(
        val shape: SerializedShape?,
        val nodes: List<Position>?,
    )

    private data class PayloadV2(
        val mode: String,
        val nodes: List<Position>?,
    )

    private data class GlobalPayload(
        val worlds: List<String>? = null,
        val minY: Double? = null,
        val maxY: Double? = null,
    ) {
        fun resolvedWorlds(): List<String> {
            val collected = linkedMapOf<String, String>()
            worlds.orEmpty().forEach { candidate ->
                val trimmed = candidate.trim()
                if (trimmed.isNotEmpty()) {
                    collected.putIfAbsent(trimmed.lowercase(Locale.ROOT), trimmed)
                }
            }
            return collected.values.toList()
        }
    }
}

private fun List<String>.normalizedEquals(other: List<String>): Boolean {
    if (isEmpty() && other.isEmpty()) return true
    val left = map { it.lowercase(Locale.ROOT) }.toSet()
    val right = other.map { it.lowercase(Locale.ROOT) }.toSet()
    return left == right
}



