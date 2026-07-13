package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.selection.toBukkitLocation
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.slf4j.LoggerFactory

/**
 * Teleports player to JOIN_LOCATION when they are inside a region with the flag.
 * Only applies when the player is physically inside the region bounds.
 */
@Singleton
class JoinLocationListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("JoinLocationListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val location = player.location
        val regions = regionRepository.regionsAt(location.toTWPosition())

        for (region in regions) {
            val binding = region.flags.firstOrNull { it.key == RegionFlagKey.JOIN_LOCATION } ?: continue
            val target = (binding.value as? btc.renaud.protection.flags.FlagValue.LocationValue)?.position
                ?: continue
            val destination = target.toBukkitLocation()
            event.respawnLocation = destination
            logger.debug("Redirected {} join location to {} via region {}",
                player.name, destination, region.id)
            return
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val location = player.location
        val regions = regionRepository.regionsAt(location.toTWPosition())

        for (region in regions) {
            val binding = region.flags.firstOrNull { it.key == RegionFlagKey.JOIN_LOCATION } ?: continue
            val target = (binding.value as? btc.renaud.protection.flags.FlagValue.LocationValue)?.position
                ?: continue
            val destination = target.toBukkitLocation()
            player.teleportAsync(destination)
            logger.debug("Teleported {} to join location {} via region {}",
                player.name, destination, region.id)
            return
        }
    }
}
