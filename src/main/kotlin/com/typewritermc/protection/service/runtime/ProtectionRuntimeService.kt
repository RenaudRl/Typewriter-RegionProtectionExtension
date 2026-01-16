package com.typewritermc.protection.service.runtime

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.listener.FlagContext
import com.typewritermc.protection.listener.movement.EntryDecision
import com.typewritermc.protection.selection.toBukkitLocation
import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.slf4j.LoggerFactory

@Singleton
class ProtectionRuntimeService(
    private val repository: RegionRepository,
    private val actionExecutor: FlagActionExecutor,
) {
    private val logger = LoggerFactory.getLogger("ProtectionRuntimeService")

    fun regionAt(location: Location): RegionModel? = repository.regionsAt(location.toTWPosition()).firstOrNull()

    fun evaluateFlag(context: FlagContext, key: RegionFlagKey): FlagEvaluation = actionExecutor.evaluate(context, key)

    fun enforceEntry(player: Player, from: Position?, to: Position?, event: Event): EntryDecision {
        val previousRegions = from?.let { repository.regionsAt(it) } ?: emptyList()
        val nextRegions = to?.let { repository.regionsAt(it) } ?: emptyList()
        
        val previousIds = previousRegions.map { it.id }.toSet()
        val nextIds = nextRegions.map { it.id }.toSet()

        if (previousIds == nextIds) return EntryDecision.Allowed

        // EXIT Logic: Check regions we are leaving
        // Filter previousRegions to only those NOT in nextIds
        // Sorted by priority descending (already sorted by regionsAt)
        val leaving = previousRegions.filter { it.id !in nextIds }
        
        for (region in leaving) {
            val context = FlagContext(
                region = region,
                event = event,
                location = from?.toBukkitLocation(),
                player = player,
                source = player
            )
            val result = actionExecutor.evaluate(context, RegionFlagKey.EXIT)
            when (result) {
                is FlagEvaluation.Denied -> {
                    logger.debug("Exit blocked for {} from region {}", player.name, region.id)
                    return EntryDecision.Blocked(region, RegionFlagKey.EXIT, result)
                }
                is FlagEvaluation.Allow -> break // Allowed by higher priority, stop checking lower
                is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
                else -> {} // Pass, continue to next
            }
            // Actions only on success? Or always? 
            // Usually actions run if modifying or allowing, or passing.
            // If we are denied, we return.
        }

        // ENTRY Logic: Check regions we are entering
        val entering = nextRegions.filter { it.id !in previousIds }
        
        for (region in entering) {
            val context = FlagContext(
                region = region,
                event = event,
                location = to?.toBukkitLocation(),
                player = player,
                source = player
            )
            val result = actionExecutor.evaluate(context, RegionFlagKey.ENTRY)
            when (result) {
                is FlagEvaluation.Denied -> {
                    logger.debug("Entry blocked for {} into region {}", player.name, region.id)
                    return EntryDecision.Blocked(region, RegionFlagKey.ENTRY, result)
                }
                is FlagEvaluation.Allow -> break // Allowed by higher priority
                is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
                else -> {} 
            }
        }
        
        // Actions execution Step
        // We run actions for all valid entries/exits if we weren't blocked
        // Note: The logic above broke on Allow.
        // But actions should probably run for ALL regions we effectively entered/exited?
        // Or only the one that decided?
        // Usually you want "Welcome to Spawn" AND "Welcome to PvP Zone".
        // So actions should be decoupled from the Decision Loop if possible, OR run as we iterate.
        // But if we blocked, we shouldn't have run actions.
        // Correct approach: First decide, Then execute actions.
        
        // Optimization: Run actions after decision is final.
        processActions(entering, to?.toBukkitLocation(), player, event, RegionFlagKey.ENTRY_ACTION)
        processActions(leaving, from?.toBukkitLocation(), player, event, RegionFlagKey.EXIT_ACTION)

        return EntryDecision.Allowed
    }

    private fun processActions(
        regions: List<RegionModel>,
        location: Location?,
        player: Player,
        event: Event,
        flag: RegionFlagKey
    ) {
        if (regions.isEmpty()) return
        for (region in regions) {
             val context = FlagContext(
                region = region,
                event = event,
                location = location,
                player = player,
                source = player
            )
            val result = actionExecutor.evaluate(context, flag)
            if (result is FlagEvaluation.Modify) {
                actionExecutor.applyModifications(context, result)
            }
        }
    }
}

