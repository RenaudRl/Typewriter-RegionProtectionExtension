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
import org.bukkit.Material
import org.bukkit.event.block.Action
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
        val location = event.interactionPoint ?: event.clickedBlock?.location ?: event.player.location
        val region = dominantRegion(location) ?: return
        
        val context = createContext(region, event, location, event.player, source = event.player)
        
        // Check for VEHICLE_PLACE
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.item != null) {
            val type = event.item!!.type
            if (type.name.contains("MINECART") || type.name.contains("BOAT") || type.name.contains("RAFT")) {
                 when (val result = actionExecutor.evaluate(context, RegionFlagKey.VEHICLE_PLACE)) {
                    is FlagEvaluation.Denied -> {
                        event.isCancelled = true
                        actionExecutor.handleDenied(context, RegionFlagKey.VEHICLE_PLACE, result)
                        return
                    }
                    else -> {}
                 }
            }
        }

        // Check for USE
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && isUseInteraction(event.clickedBlock!!.type)) {
             when (val result = actionExecutor.evaluate(context, RegionFlagKey.USE)) {
                is FlagEvaluation.Denied -> {
                    event.isCancelled = true
                    actionExecutor.handleDenied(context, RegionFlagKey.USE, result)
                    return
                }
                else -> {}
             }
        }
        
        // Fallback to generic INTERACT
        when (val result = actionExecutor.evaluate(context, RegionFlagKey.INTERACT)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.INTERACT, result)
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
            else -> Unit
        }
    }
    
    private fun isUseInteraction(type: Material): Boolean {
        return type.isInteractable || 
               type.name.contains("DOOR") || 
               type.name.contains("BUTTON") || 
               type.name.contains("LEVER") ||
               type.name.contains("GATE") ||
               type.name.contains("TRAPDOOR") || 
               type.name.contains("CHEST") ||
               type.name.contains("BARREL") || 
               type.name.contains("SHULKER") ||
               type == Material.REPEATER || 
               type == Material.COMPARATOR ||
               type == Material.HOPPER ||
               type == Material.DISPENSER ||
               type == Material.DROPPER ||
               type == Material.FURNACE ||
               type == Material.BLAST_FURNACE ||
               type == Material.SMOKER ||
               type == Material.ENCHANTING_TABLE ||
               type == Material.ENDER_CHEST ||
               type == Material.ANVIL ||
               type == Material.CHIPPED_ANVIL ||
               type == Material.DAMAGED_ANVIL ||
               type == Material.BEACON ||
               type == Material.BREWING_STAND ||
               type == Material.NOTE_BLOCK ||
               type == Material.JUKEBOX ||
               type == Material.COMMAND_BLOCK || 
               type == Material.DAYLIGHT_DETECTOR
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

