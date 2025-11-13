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
        val previousRegion = from?.let { repository.regionsAt(it).firstOrNull() }
        val nextRegion = to?.let { repository.regionsAt(it).firstOrNull() }
        if (previousRegion?.id == nextRegion?.id) return EntryDecision.Allowed

        if (nextRegion != null) {
            val context = FlagContext(
                region = nextRegion,
                event = event,
                location = to?.toBukkitLocation(),
                player = player,
                source = player,
            )
            val entryResult = actionExecutor.evaluate(context, RegionFlagKey.ENTRY)
            when (entryResult) {
                is FlagEvaluation.Denied -> {
                    logger.debug("Entry blocked for {} into region {}", player.name, nextRegion.id)
                    return EntryDecision.Blocked(nextRegion, RegionFlagKey.ENTRY, entryResult)
                }
                is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, entryResult)
                else -> Unit
            }
            if (entryResult !is FlagEvaluation.Denied) {
                when (val entryActions = actionExecutor.evaluate(context, RegionFlagKey.ENTRY_ACTION)) {
                    is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, entryActions)
                    else -> Unit
                }
            }
        }

        if (previousRegion != null) {
            val context = FlagContext(
                region = previousRegion,
                event = event,
                location = from?.toBukkitLocation(),
                player = player,
                source = player,
            )
            val exitResult = actionExecutor.evaluate(context, RegionFlagKey.EXIT)
            when (exitResult) {
                is FlagEvaluation.Denied -> {
                    logger.debug("Exit blocked for {} from region {}", player.name, previousRegion.id)
                    return EntryDecision.Blocked(previousRegion, RegionFlagKey.EXIT, exitResult)
                }
                is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, exitResult)
                else -> Unit
            }
            if (exitResult !is FlagEvaluation.Denied) {
                when (val exitActions = actionExecutor.evaluate(context, RegionFlagKey.EXIT_ACTION)) {
                    is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, exitActions)
                    else -> Unit
                }
            }
        }

        return EntryDecision.Allowed
}
}
