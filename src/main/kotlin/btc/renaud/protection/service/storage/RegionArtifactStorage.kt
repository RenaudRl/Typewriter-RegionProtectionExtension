package btc.renaud.protection.service.storage

import com.google.gson.Gson
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entries.stringData
import btc.renaud.protection.entry.artifact.GlobalRegionArtifactEntry
import btc.renaud.protection.entry.artifact.RegionArtifactEntry
import btc.renaud.protection.selection.CuboidShape
import btc.renaud.protection.selection.CylinderShape
import btc.renaud.protection.selection.FlatPolygonShape
import btc.renaud.protection.selection.GlobalRegionShape
import btc.renaud.protection.selection.PolygonPrismShape
import btc.renaud.protection.selection.RegionShape
import btc.renaud.protection.selection.SelectionMode
import btc.renaud.protection.selection.SelectionMode.Companion.fromShape
import btc.renaud.protection.selection.SerializedShape
import btc.renaud.protection.selection.serialize
import btc.renaud.protection.selection.toRegionShape
import kotlinx.coroutines.runBlocking
import java.util.Locale
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

/**
 * Default [RegionStorage] implementation using Typewriter artifact stringData.
 */
@Singleton
class RegionArtifactStorage(
    private val gson: Gson,
) : RegionStorage {
    private val logger = LoggerFactory.getLogger("RegionArtifactStorage")

    override suspend fun load(artifact: RegionArtifactEntry): RegionShapeData? {
        return runCatching {
            val data = artifact.stringData() ?: return null
            if (data.isBlank()) return null
            parsePayload(data)
        }.onFailure { error ->
            logger.warn("Failed to load region shape for artifact {}", artifact.artifactId, error)
        }.getOrNull()
    }

    override suspend fun save(artifact: RegionArtifactEntry, data: RegionShapeData) {
        runCatching {
            val payload = RegionShapePayloadV2(data.mode, data.nodes)
            val json = gson.toJson(payload)
            artifact.stringData(json)
        }.onFailure { error ->
            logger.error("Failed to persist region shape for artifact {}", artifact.artifactId, error)
        }
    }

    override suspend fun initialize(artifact: RegionArtifactEntry) {
        val current = runCatching { artifact.stringData() }.getOrNull()
        if (current.isNullOrBlank()) {
            save(artifact, RegionShapeData())
        }
    }

    override suspend fun loadGlobal(artifact: RegionArtifactEntry): GlobalRegionShapeData? {
        val globalArtifact = artifact as? GlobalRegionArtifactEntry ?: return null
        return runCatching {
            val data = artifact.stringData() ?: return null
            if (data.isBlank()) return null
            parseGlobalPayload(data, globalArtifact)
        }.onFailure { error ->
            logger.warn("Failed to load global region data for artifact {}", artifact.artifactId, error)
        }.getOrNull()
    }

    override suspend fun saveGlobal(artifact: RegionArtifactEntry, data: GlobalRegionShapeData) {
        runCatching {
            val payload = GlobalRegionPayload(
                worlds = data.worlds,
                minY = data.minY,
                maxY = data.maxY,
            )
            val json = gson.toJson(payload)
            artifact.stringData(json)
        }.onFailure { error ->
            logger.error("Failed to persist global region artifact {}", artifact.artifactId, error)
        }
    }

    override suspend fun initializeGlobal(artifact: RegionArtifactEntry) {
        val globalArtifact = artifact as? GlobalRegionArtifactEntry ?: return
        val current = runCatching { artifact.stringData() }.getOrNull()
        if (current.isNullOrBlank()) {
            saveGlobal(
                artifact,
                GlobalRegionShapeData(
                    worlds = globalArtifact.resolvedWorlds(),
                    minY = globalArtifact.minY,
                    maxY = globalArtifact.maxY,
                )
            )
            return
        }
        val stored = parseGlobalPayload(current, globalArtifact)
        val desiredWorlds = globalArtifact.resolvedWorlds()
        if (
            stored == null ||
            !stored.worlds.normalizedEquals(desiredWorlds) ||
            stored.minY != globalArtifact.minY ||
            stored.maxY != globalArtifact.maxY
        ) {
            saveGlobal(
                artifact,
                GlobalRegionShapeData(
                    worlds = desiredWorlds,
                    minY = globalArtifact.minY,
                    maxY = globalArtifact.maxY,
                )
            )
        }
    }

    private fun parsePayload(data: String): RegionShapeData? {
        gson.fromJson(data, RegionShapePayloadV2::class.java)?.let { payload ->
            val mode = SelectionMode.entries.firstOrNull { it.name == payload.mode } ?: SelectionMode.CUBOID
            val nodes = payload.nodes ?: emptyList()
            return RegionShapeData(
                version = 2,
                mode = mode.name,
                nodes = nodes,
                shapeType = mode.name,
            )
        }

        val legacy = gson.fromJson(data, RegionShapePayloadLegacy::class.java) ?: return null
        val shape = legacy.shape?.toRegionShape(CuboidShape()) ?: return null
        val nodes = legacy.nodes ?: deriveNodes(shape)
        val mode = SelectionMode.fromShape(shape)
        return RegionShapeData(
            version = 1,
            mode = mode.name,
            nodes = nodes,
            shapeType = mode.name,
        )
    }

    private fun parseGlobalPayload(data: String, fallback: GlobalRegionArtifactEntry): GlobalRegionShapeData? {
        val payload = runCatching { gson.fromJson(data, GlobalRegionPayload::class.java) }.getOrNull()
        val worlds = payload?.resolvedWorlds()?.takeIf { it.isNotEmpty() } ?: fallback.resolvedWorlds()
        val minY = payload?.minY ?: fallback.minY
        val maxY = payload?.maxY ?: fallback.maxY
        return GlobalRegionShapeData(worlds = worlds, minY = minY, maxY = maxY)
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

    private data class RegionShapePayloadLegacy(
        val shape: SerializedShape?,
        val nodes: List<Position>?,
    )

    private data class RegionShapePayloadV2(
        val mode: String,
        val nodes: List<Position>?,
    )

    private data class GlobalRegionPayload(
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
