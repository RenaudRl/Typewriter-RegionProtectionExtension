package btc.renaud.protection.service.storage

import com.typewritermc.core.utils.point.Position
import btc.renaud.protection.entry.artifact.RegionArtifactEntry
import btc.renaud.protection.selection.RegionShape
import btc.renaud.protection.selection.SelectionMode

/**
 * DTO for serializing region shape data to/from artifact storage.
 * Versioned for forward compatibility.
 */
data class RegionShapeData(
    val version: Int = CURRENT_VERSION,
    val mode: String = SelectionMode.CUBOID.name,
    val nodes: List<Position> = emptyList(),
    val shapeType: String = "CUBOID",
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

/**
 * DTO for global region data stored in artifacts.
 */
data class GlobalRegionShapeData(
    val version: Int = CURRENT_VERSION,
    val worlds: List<String> = emptyList(),
    val minY: Double = -64.0,
    val maxY: Double = 320.0,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Abstraction for region shape storage. Implementations can target
 * the Typewriter artifact system (default) or external databases.
 */
interface RegionStorage {
    /**
     * Load shape data for the given artifact, or null if no data exists.
     */
    suspend fun load(artifact: RegionArtifactEntry): RegionShapeData?

    /**
     * Save shape data for the given artifact.
     */
    suspend fun save(artifact: RegionArtifactEntry, data: RegionShapeData)

    /**
     * Initialize the artifact with default data if empty.
     */
    suspend fun initialize(artifact: RegionArtifactEntry)

    /**
     * Load global region data for the given artifact, or null if no data exists.
     */
    suspend fun loadGlobal(artifact: RegionArtifactEntry): GlobalRegionShapeData?

    /**
     * Save global region data for the given artifact.
     */
    suspend fun saveGlobal(artifact: RegionArtifactEntry, data: GlobalRegionShapeData)

    /**
     * Initialize the global artifact with default data if empty.
     */
    suspend fun initializeGlobal(artifact: RegionArtifactEntry)
}
