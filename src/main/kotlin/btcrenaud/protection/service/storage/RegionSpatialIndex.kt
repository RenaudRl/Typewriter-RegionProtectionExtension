package btcrenaud.protection.service.storage

import com.typewritermc.core.utils.point.Position
import btcrenaud.protection.selection.GlobalRegionShape
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A spatial index for protection regions, optimizing lookup from linear O(N) to O(1) in average case.
 * Uses a simple 2D chunk-based grid (ignoring Y for primary partitioning, as regions are typically tall).
 */
class RegionSpatialIndex {
    private val chunks = ConcurrentHashMap<Long, CopyOnWriteArrayList<RegionModel>>()
    private val globalRegions = CopyOnWriteArrayList<RegionModel>()

    /**
     * Rebuilds the index with the provided regions.
     */
    fun rebuild(allRegions: Collection<RegionModel>) {
        chunks.clear()
        globalRegions.clear()
        for (region in allRegions) {
            add(region)
        }
    }

    /**
     * Adds a region to the spatial index.
     */
    fun add(region: RegionModel) {
        if (region.shape is GlobalRegionShape) {
            globalRegions.add(region)
            return
        }

        val min = region.shape.min()
        val max = region.shape.max()

        val minChunkX = min.x.toInt() shr 4
        val minChunkZ = min.z.toInt() shr 4
        val maxChunkX = max.x.toInt() shr 4
        val maxChunkZ = max.z.toInt() shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val key = chunkKey(cx, cz)
                chunks.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(region)
            }
        }
    }

    /**
     * Removes a region from the spatial index.
     */
    fun remove(region: RegionModel) {
        if (region.shape is GlobalRegionShape) {
            globalRegions.remove(region)
            return
        }

        val min = region.shape.min()
        val max = region.shape.max()

        val minChunkX = min.x.toInt() shr 4
        val minChunkZ = min.z.toInt() shr 4
        val maxChunkX = max.x.toInt() shr 4
        val maxChunkZ = max.z.toInt() shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val key = chunkKey(cx, cz)
                chunks[key]?.remove(region)
            }
        }
    }

    /**
     * Queries regions at the given position.
     */
    fun query(position: Position): List<RegionModel> {
        val cx = position.x.toInt() shr 4
        val cz = position.z.toInt() shr 4
        val key = chunkKey(cx, cz)
        
        val candidates = chunks[key] ?: return globalRegions.filter { it.shape.contains(position) }
        
        return (candidates + globalRegions)
            .filter { it.shape.contains(position) }
            .sortedByDescending { it.priority }
    }

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
