package com.typewritermc.protection.listener.movement

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.events.ProtectionRegionsEnterEvent
import com.typewritermc.protection.events.ProtectionRegionsExitEvent
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.listener.FlagContext
import com.typewritermc.protection.listener.movement.EntryDecision.Blocked
import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.service.runtime.ProtectionRuntimeService
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import com.typewritermc.protection.settings.ProtectionSettingsRepository
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class MovementProtectionListener(
    private val repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
    private val runtimeService: ProtectionRuntimeService,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("MovementProtectionListener")
    private val lastRegions = ConcurrentHashMap<UUID, Set<String>>()

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val decision = runtimeService.enforceEntry(event.player, event.from?.toTWPosition(), event.to?.toTWPosition(), event)
        if (decision is Blocked) {
            event.isCancelled = true
            handleBlocked(event, decision, event.to, "Movement")
        } else {
            updateRegionMembership(event.player, event.to)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val decision = runtimeService.enforceEntry(event.player, event.from?.toTWPosition(), event.to?.toTWPosition(), event)
        if (decision is Blocked) {
            event.isCancelled = true
            handleBlocked(event, decision, event.to, "Teleport")
        } else {
            updateRegionMembership(event.player, event.to)
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        lastRegions.remove(event.player.uniqueId)
    }

    private fun <T> handleBlocked(
        event: T,
        blocked: Blocked,
        attemptedLocation: Location?,
        actionDescription: String,
    ) where T : PlayerEvent, T : Cancellable {
        val player = event.player
        val context = createContext(blocked.region, event, attemptedLocation, player)
        val snapshot = ProtectionSettingsRepository.snapshot(player)
        val suppressDefault = triggerDeniedActions(context, blocked.flag)
        val regionName = resolveRegionName(blocked.region)
        val defaultMessage = snapshot.deniedEntry(regionName, blocked.flag.id)
        val snapshotOverride = if (suppressDefault) snapshot.copy(showDeniedMessages = false) else snapshot
        val customMessage = if (suppressDefault) null else defaultMessage
        logger.debug(
            "{} for {} cancelled by flag {} in region {}",
            actionDescription,
            player.name,
            blocked.flag.id,
            blocked.region.id
        )
        actionExecutor.handleDenied(context, blocked.flag, blocked.evaluation, customMessage, snapshotOverride)
    }

    private fun updateRegionMembership(player: Player, location: Location?) {
        val currentModels = location?.let { repository.regionsAt(it.toTWPosition()) } ?: emptyList()
        val currentIds = currentModels.map { it.id }.toSet()
        val previousIds = lastRegions.put(player.uniqueId, currentIds).orEmpty()

        if (currentIds.isEmpty()) {
            lastRegions.remove(player.uniqueId)
        }

        val entered = currentIds - previousIds
        val exited = previousIds - currentIds

        if (entered.isNotEmpty()) {
            publishEnterEvent(player, currentModels, entered)
        }

        if (exited.isNotEmpty()) {
            publishExitEvent(player, exited)
        }

        if (entered.isNotEmpty() || exited.isNotEmpty()) {
            logger.debug(
                "Player {} region membership changed: entered={}, exited={}",
                player.uniqueId,
                entered,
                exited
            )
        }
    }

    private fun publishEnterEvent(player: Player, models: List<RegionModel>, entered: Set<String>) {
        val regions = entered.mapNotNull { id ->
            models.firstOrNull { it.id == id } ?: repository.findById(id)
        }.toSet()
        if (regions.isEmpty()) return
        try {
            Bukkit.getPluginManager().callEvent(ProtectionRegionsEnterEvent(player, regions))
        } catch (ignored: Exception) {
            logger.trace("Skipping ProtectionRegionsEnterEvent for {}: {}", player.uniqueId, ignored.message)
        }
    }

    private fun publishExitEvent(player: Player, exited: Set<String>) {
        val regions = exited.mapNotNull { id -> repository.findById(id) }.toSet()
        if (regions.isEmpty()) return
        try {
            Bukkit.getPluginManager().callEvent(ProtectionRegionsExitEvent(player, regions))
        } catch (ignored: Exception) {
            logger.trace("Skipping ProtectionRegionsExitEvent for {}: {}", player.uniqueId, ignored.message)
        }
    }

    private fun resolveRegionName(region: RegionModel): String {
        return region.definition.name.ifBlank { region.artifact?.name ?: region.id }
    }

    private fun triggerDeniedActions(context: FlagContext, flag: RegionFlagKey): Boolean {
        val actionFlag = when (flag) {
            RegionFlagKey.ENTRY -> RegionFlagKey.ENTRY_ACTION
            RegionFlagKey.EXIT -> RegionFlagKey.EXIT_ACTION
            else -> return false
        }
        val evaluation = actionExecutor.evaluate(context, actionFlag)
        val modify = evaluation as? FlagEvaluation.Modify ?: return false
        val messageHandled = modify.metadata["message.component"] != null || modify.metadata["message"] != null
        actionExecutor.applyModifications(context, modify)
        return messageHandled
    }
}
