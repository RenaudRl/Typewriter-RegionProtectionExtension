package com.typewritermc.protection.listener.building

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.listener.FlagContext
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.slf4j.LoggerFactory

@Singleton
class BuildProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("BuildProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val location = event.blockPlaced.location
        val player = event.player
        
        // Check BUILD flag first
        var (evaluation, context) = evaluateFlag(RegionFlagKey.BUILD, event, location, player, source = player)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
            if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.BUILD, evaluation)
            return
        }

        // Check BLOCK_PLACE flag
        val pair = evaluateFlag(RegionFlagKey.BLOCK_PLACE, event, location, player, source = player)
        evaluation = pair.first
        context = pair.second

        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
            if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.BLOCK_PLACE, evaluation)
        } else if (evaluation is FlagEvaluation.Modify && context != null) {
            actionExecutor.applyModifications(context, evaluation)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val location = event.block.location
        val player = event.player

        // Check BUILD flag first
        var (evaluation, context) = evaluateFlag(RegionFlagKey.BUILD, event, location, player, source = player)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
            if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.BUILD, evaluation)
            return
        }

        // Check BLOCK_BREAK flag
        val pair = evaluateFlag(RegionFlagKey.BLOCK_BREAK, event, location, player, source = player)
        evaluation = pair.first
        context = pair.second

        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
            if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.BLOCK_BREAK, evaluation)
        } else if (evaluation is FlagEvaluation.Modify && context != null) {
            actionExecutor.applyModifications(context, evaluation)
        }
    }
}

