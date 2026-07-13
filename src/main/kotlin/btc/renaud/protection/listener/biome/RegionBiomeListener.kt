package btc.renaud.protection.listener.biome

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.plugin
import btc.renaud.protection.events.ProtectionRegionsEnterEvent
import btc.renaud.protection.events.ProtectionRegionsExitEvent
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.block.Biome
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener that applies biome overrides when players enter/exit protected regions
 * with the BIOME flag set.
 */
@Singleton
class RegionBiomeListener(
    private val repository: RegionRepository,
) : Listener {
    
    private val logger = LoggerFactory.getLogger(RegionBiomeListener::class.java)
    
    // Track player's last active biome region to avoid duplicate updates
    private val playerBiomeRegions = ConcurrentHashMap<UUID, String?>()
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onRegionEnter(event: ProtectionRegionsEnterEvent) {
        val player = event.player
        val regions = event.regions
        
        // Find the highest priority region with a biome flag
        val biomeRegion = findBiomeRegion(regions) ?: return
        val biomeId = extractBiomeId(biomeRegion) ?: return
        
        val previousRegion = playerBiomeRegions[player.uniqueId]
        if (previousRegion == biomeRegion.id) {
            // Already in this biome region
            return
        }
        
        playerBiomeRegions[player.uniqueId] = biomeRegion.id
        
        // Resolve and apply biome
        val biome = resolveBiome(biomeId)
        if (biome == null) {
            logger.warn("Unable to resolve biome '{}' in region '{}'", biomeId, biomeRegion.id)
            return
        }
        
        logger.debug("Applying biome {} for player {} in region {}", biome.key, player.name, biomeRegion.id)
        
        // Apply biome to player's view (client-side visual effect using packets)
        btc.renaud.protection.listener.SchedulerCompat.runLater(plugin, player.location, 2L) {
            refreshBiomeForPlayer(player)
        } // Small delay to ensure player position is updated
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onRegionExit(event: ProtectionRegionsExitEvent) {
        val player = event.player
        val exitedRegions = event.regions.map { it.id }.toSet()
        
        val currentBiomeRegion = playerBiomeRegions[player.uniqueId]
        
        if (currentBiomeRegion != null && currentBiomeRegion in exitedRegions) {
            // Player is exiting their biome region
            playerBiomeRegions.remove(player.uniqueId)
            
            logger.debug("Player {} exited biome region {}, refreshing natural biomes", player.name, currentBiomeRegion)
            
            // Refresh to show natural biomes
            btc.renaud.protection.listener.SchedulerCompat.runLater(plugin, player.location, 2L) {
                refreshBiomeForPlayer(player)
            }
        }
    }
    
    private fun findBiomeRegion(regions: Set<RegionModel>): RegionModel? {
        return regions
            .filter { region ->
                region.flags.any { it.key == RegionFlagKey.BIOME }
            }
            .maxByOrNull { it.priority }
    }
    
    private fun extractBiomeId(region: RegionModel): String? {
        val biomeFlag = region.flags.firstOrNull { it.key == RegionFlagKey.BIOME } ?: return null
        return when (val value = biomeFlag.value) {
            is FlagValue.Text -> value.content.takeIf { it.isNotBlank() }
            else -> null
        }
    }
    
    private fun resolveBiome(identifier: String): Biome? {
        val trimmed = identifier.trim().lowercase(Locale.ENGLISH)
        if (trimmed.isEmpty()) return null
        
        // Try as namespaced key first
        val key = NamespacedKey.fromString(trimmed)
            ?: NamespacedKey.minecraft(trimmed.removePrefix("minecraft:"))
        
        return RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.BIOME)
            .get(key)
    }
    
    /**
     * Force chunk refresh for a player using PacketEvents.
     * Sends unload packets which forces the client to request fresh chunk data.
     */
    private fun refreshBiomeForPlayer(player: Player) {
        runCatching {
            val loc = player.location
            val radius = 3 // Chunk radius to refresh
            val centerX = loc.blockX shr 4
            val centerZ = loc.blockZ shr 4
            
            val manager = PacketEvents.getAPI().playerManager
            for (cx in (centerX - radius)..(centerX + radius)) {
                for (cz in (centerZ - radius)..(centerZ + radius)) {
                    val unloadPacket = WrapperPlayServerUnloadChunk(cx, cz)
                    manager.sendPacket(player, unloadPacket)
                }
            }
        }.onFailure { error ->
            logger.warn("Failed to refresh biome for ${player.name}: ${error.message}")
        }
    }
    
    fun clearPlayer(playerId: UUID) {
        playerBiomeRegions.remove(playerId)
    }
}
