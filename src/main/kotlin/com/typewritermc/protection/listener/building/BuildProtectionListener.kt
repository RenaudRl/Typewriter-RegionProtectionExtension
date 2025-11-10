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
        val region = dominantRegion(location)
        if (region == null) {
            logger.debug("No region matched block place at {}", location)
            return
        }

        val context = createContext(region, event, location, event.player, source = event.player)
        if (!evaluateFlag(event, context, RegionFlagKey.BUILD)) return
        evaluateFlag(event, context, RegionFlagKey.BLOCK_PLACE)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val location = event.block.location
        val region = dominantRegion(location)
        if (region == null) {
            logger.debug("No region matched block break at {}", location)
            return
        }

        val context = createContext(region, event, location, event.player, source = event.player)
        if (!evaluateFlag(event, context, RegionFlagKey.BUILD)) return
        evaluateFlag(event, context, RegionFlagKey.BLOCK_BREAK)
    }

    private fun evaluateFlag(
        event: Cancellable,
        context: FlagContext,
        flag: RegionFlagKey,
    ): Boolean {
        return when (val result = actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, flag, result)
                false
            }
            is FlagEvaluation.Modify -> {
                actionExecutor.applyModifications(context, result)
                true
            }
            else -> true
        }
    }
}
