package com.typewritermc.protection.listener.interaction

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.slf4j.LoggerFactory

@Singleton
class InteractionProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("InteractionProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val location = event.interactionPoint ?: event.player.location
        val region = dominantRegion(location)
        if (region == null) {
            logger.debug("No region matched interaction at {}", location)
            return
        }
        val context = createContext(region, event, location, event.player, source = event.player)
        when (val result = actionExecutor.evaluate(context, RegionFlagKey.INTERACT)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.INTERACT, result)
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val region = dominantRegion(event.player.location)
        if (region == null) {
            logger.debug("No region matched item drop at {}", event.player.location)
            return
        }
        val context = createContext(region, event, event.player.location, event.player, source = event.player)
        when (val result = actionExecutor.evaluate(context, RegionFlagKey.ITEM_DROP)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.ITEM_DROP, result)
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        val region = dominantRegion(event.player.location)
        if (region == null) {
            logger.debug("No region matched item pickup at {}", event.player.location)
            return
        }
        val context = createContext(region, event, event.player.location, event.player, source = event.player)
        when (val result = actionExecutor.evaluate(context, RegionFlagKey.ITEM_PICKUP)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.ITEM_PICKUP, result)
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
            else -> Unit
        }
    }
}

