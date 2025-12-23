package com.typewritermc.protection.listener.environment

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.weather.LightningStrikeEvent
import org.slf4j.LoggerFactory

@Singleton
class EnvironmentPropertiesProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("EnvironmentPropertiesProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location)
        
        when (actionExecutor.evaluate(context, RegionFlagKey.LEAF_DECAY)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        val type = event.block.type
        val flag = when {
            type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE || type == Material.FROSTED_ICE -> RegionFlagKey.ICE_MELT
            type == Material.SNOW || type == Material.SNOW_BLOCK -> RegionFlagKey.SNOW_MELT
            else -> null
        } ?: return

        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location)

        when (actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        val type = event.block.type
        val flag = when (type) {
            Material.LAVA -> RegionFlagKey.LAVA_FLOW
            Material.WATER -> RegionFlagKey.WATER_FLOW
            else -> null
        } ?: return

        // Check if the flow destination is in a protected region
        // Usually, we want to prevent flowing INTO or WITHIN a protected region if flow is disabled.
        // Or preventing flowing OUT of a source block. 
        // Standard WorldGuard behavior: check at the TO location.
        val region = dominantRegion(event.toBlock.location) ?: return
        val context = createContext(region, event, event.toBlock.location)


        when (actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockIgnite(event: BlockIgniteEvent) {
        val flag = when (event.cause) {
            BlockIgniteEvent.IgniteCause.LAVA -> RegionFlagKey.LAVA_FIRE
            BlockIgniteEvent.IgniteCause.LIGHTNING -> RegionFlagKey.LIGHTNING
            else -> RegionFlagKey.FIRE_SPREAD
        }

        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location)

        when (actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location)

        when (actionExecutor.evaluate(context, RegionFlagKey.FIRE_SPREAD)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onLightningStrike(event: LightningStrikeEvent) {
        val region = dominantRegion(event.lightning.location) ?: return
        val context = createContext(region, event, event.lightning.location)

        when (actionExecutor.evaluate(context, RegionFlagKey.LIGHTNING)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        val type = event.newState.type
        val flag = when (type) {
            Material.VINE, Material.WEEPING_VINES, Material.TWISTING_VINES, Material.CAVE_VINES -> RegionFlagKey.VINE_GROWTH
            else -> null // Other crops? usually allowed unless specific crop flags exist
        } ?: return

        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location)

        when (actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        val type = event.newState.type
        val flag = when (type) {
            Material.GRASS_BLOCK, Material.MYCELIUM, Material.DIRT_PATH -> RegionFlagKey.GRASS_GROWTH
            Material.VINE -> RegionFlagKey.VINE_GROWTH
            Material.FIRE -> RegionFlagKey.FIRE_SPREAD
            Material.MUSHROOM_STEM, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM -> RegionFlagKey.GRASS_GROWTH // Approximation
            else -> null
        } ?: return

        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location)

        when (actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }
}
