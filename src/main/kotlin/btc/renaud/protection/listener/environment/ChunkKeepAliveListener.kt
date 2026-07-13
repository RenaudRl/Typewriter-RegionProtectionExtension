package btc.renaud.protection.listener.environment

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.plugin
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.listener.SchedulerCompat
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkUnloadEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps chunks loaded when CHUNK_UNLOAD flag is active.
 * Chunks are force-loaded permanently (survives player logout).
 */
@Singleton
class ChunkKeepAliveListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("ChunkKeepAliveListener")
    private val forcedChunks = ConcurrentHashMap<String, Boolean>()

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        val key = chunkKey(chunk.world.name, chunk.x, chunk.z)
        if (forcedChunks.containsKey(key)) {
            // ChunkUnloadEvent is not cancellable in modern Paper,
            // but we prevent it by force-loading the chunk before unload
            event.chunk.world.setChunkForceLoaded(chunk.x, chunk.z, true)
        }
    }

    fun updateForceLoadedChunks() {
        val allRegions = regionRepository.all()
        val newForced = mutableSetOf<String>()

        for (region in allRegions) {
            val binding = region.flags.firstOrNull { it.key == RegionFlagKey.CHUNK_UNLOAD } ?: continue
            val value = binding.value as? btc.renaud.protection.flags.FlagValue.Boolean ?: continue
            if (!value.enabled) continue

            val shape = region.shape
            val min = shape.min()
            val max = shape.max()
            val world = Bukkit.getWorld(min.world.identifier) ?: continue

            val minCX = (min.x.toInt() shr 4)
            val maxCX = (max.x.toInt() shr 4)
            val minCZ = (min.z.toInt() shr 4)
            val maxCZ = (max.z.toInt() shr 4)

            for (cx in minCX..maxCX) {
                for (cz in minCZ..maxCZ) {
                    val key = chunkKey(world.name, cx, cz)
                    newForced.add(key)
                    if (!forcedChunks.containsKey(key)) {
                        world.setChunkForceLoaded(cx, cz, true)
                        logger.debug("Force-loaded chunk {},{} in world {}", cx, cz, world.name)
                    }
                }
            }
        }

        // Unload chunks that are no longer in any region
        val toRemove = forcedChunks.keys - newForced
        for (key in toRemove) {
            val parts = key.split(":")
            if (parts.size == 3) {
                val world = Bukkit.getWorld(parts[0]) ?: continue
                val cx = parts[1].toIntOrNull() ?: continue
                val cz = parts[2].toIntOrNull() ?: continue
                world.setChunkForceLoaded(cx, cz, false)
                logger.debug("Unloaded forced chunk {},{} in world {}", cx, cz, world.name)
            }
            forcedChunks.remove(key)
        }

        forcedChunks.putAll(newForced.associateWith { true })
    }

    fun clearAll() {
        for (key in forcedChunks.keys) {
            val parts = key.split(":")
            if (parts.size == 3) {
                val world = Bukkit.getWorld(parts[0]) ?: continue
                val cx = parts[1].toIntOrNull() ?: continue
                val cz = parts[2].toIntOrNull() ?: continue
                world.setChunkForceLoaded(cx, cz, false)
            }
        }
        forcedChunks.clear()
    }

    private fun chunkKey(world: String, cx: Int, cz: Int) = "$world:$cx:$cz"
}
