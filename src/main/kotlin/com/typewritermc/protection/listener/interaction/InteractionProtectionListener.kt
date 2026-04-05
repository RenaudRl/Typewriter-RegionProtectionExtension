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
        val player = event.player
        
        // Check for VEHICLE_PLACE
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.item != null) {
            val type = event.item!!.type
            if (type.name.contains("MINECART") || type.name.contains("BOAT") || type.name.contains("RAFT")) {
                 val (evaluation, context) = evaluateFlag(RegionFlagKey.VEHICLE_PLACE, event, location, player, source = player)
                 if (evaluation is FlagEvaluation.Denied) {
                    event.isCancelled = true
                    if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.VEHICLE_PLACE, evaluation)
                    return
                 }
            }
        }

        // Check for USE
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null) {
            val type = event.clickedBlock!!.type
            
            // Check for CHEST_ACCESS
            if (isContainer(type)) {
                val (evaluation, context) = evaluateFlag(RegionFlagKey.CHEST_ACCESS, event, location, player, source = player)
                if (evaluation is FlagEvaluation.Denied) {
                    event.isCancelled = true
                    if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.CHEST_ACCESS, evaluation)
                    return
                }
            }

            // Check for SLEEP
            if (type.name.contains("BED")) {
                val (evaluation, context) = evaluateFlag(RegionFlagKey.SLEEP, event, location, player, source = player)
                if (evaluation is FlagEvaluation.Denied) {
                    event.isCancelled = true
                    if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.SLEEP, evaluation)
                    return
                }
            }

            // Check for general USE
            if (isUseInteraction(type)) {
                val (evaluation, context) = evaluateFlag(RegionFlagKey.USE, event, location, player, source = player)
                if (evaluation is FlagEvaluation.Denied) {
                    event.isCancelled = true
                    if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.USE, evaluation)
                    return
                }
            }
        }

        // Check for LIGHTER
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.item != null) {
            val type = event.item!!.type
            if (type == Material.FLINT_AND_STEEL || type == Material.FIRE_CHARGE) {
                val (evaluation, context) = evaluateFlag(RegionFlagKey.LIGHTER, event, location, player, source = player)
                if (evaluation is FlagEvaluation.Denied) {
                    event.isCancelled = true
                    if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.LIGHTER, evaluation)
                    return
                }
            }
        }
        
        // Fallback to generic INTERACT
        val (evaluation, context) = evaluateFlag(RegionFlagKey.INTERACT, event, location, player, source = player)
        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.INTERACT, evaluation)
            }
            is FlagEvaluation.Modify -> {
                if (context != null) actionExecutor.applyModifications(context, evaluation)
            }
            else -> Unit
        }
    }
    
    private fun isUseInteraction(type: Material): Boolean {
        // Explicit list of interactable blocks (deprecated isInteractable removed)
        return type.name.contains("DOOR") || 
               type.name.contains("BUTTON") || 
               type.name.contains("LEVER") ||
               type.name.contains("GATE") ||
               type.name.contains("TRAPDOOR") || 
               type.name.contains("CHEST") ||
               type.name.contains("BARREL") || 
               type.name.contains("SHULKER") ||
               type.name.contains("BED") ||
               type.name.contains("SIGN") ||
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
               type == Material.DAYLIGHT_DETECTOR ||
               type == Material.LECTERN ||
               type == Material.BELL ||
               type == Material.LOOM ||
               type == Material.GRINDSTONE ||
               type == Material.STONECUTTER ||
               type == Material.CARTOGRAPHY_TABLE ||
               type == Material.SMITHING_TABLE ||
               type == Material.CRAFTING_TABLE ||
               type == Material.RESPAWN_ANCHOR ||
               type == Material.LODESTONE
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val (evaluation, context) = evaluateFlag(RegionFlagKey.ITEM_DROP, event, event.player.location, event.player, source = event.player)
        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.ITEM_DROP, evaluation)
            }
            is FlagEvaluation.Modify -> {
                if (context != null) actionExecutor.applyModifications(context, evaluation)
            }
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        val (evaluation, context) = evaluateFlag(RegionFlagKey.ITEM_PICKUP, event, event.player.location, event.player, source = event.player)
        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.ITEM_PICKUP, evaluation)
            }
            is FlagEvaluation.Modify -> {
                if (context != null) actionExecutor.applyModifications(context, evaluation)
            }
            else -> Unit
        }
    }

    private fun isContainer(type: Material): Boolean {
        val name = type.name
        return name.contains("CHEST") || 
               name.contains("BARREL") || 
               name.contains("SHULKER") || 
               name.contains("HOPPER") || 
               name.contains("DISPENSER") || 
               name.contains("DROPPER") || 
               name.contains("FURNACE") || 
               name.contains("SMOKER") || 
               name.contains("BLAST_FURNACE") || 
               name.contains("BREWING_STAND")
    }
}

