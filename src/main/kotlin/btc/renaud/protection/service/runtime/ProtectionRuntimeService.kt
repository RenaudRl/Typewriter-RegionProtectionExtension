package btc.renaud.protection.service.runtime

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.listener.ProtectionListenerFlagContext
import btc.renaud.protection.listener.movement.EntryDecision
import btc.renaud.protection.selection.toBukkitLocation
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
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

    fun evaluateFlag(context: ProtectionListenerFlagContext, key: RegionFlagKey): FlagEvaluation = actionExecutor.evaluate(context, key)

    fun enforceEntry(player: Player, from: Position?, to: Position?, event: Event): EntryDecision {
        val previousRegions = from?.let { repository.regionsAt(it) } ?: emptyList()
        val nextRegions = to?.let { repository.regionsAt(it) } ?: emptyList()

        val previousIds = previousRegions.map { it.id }.toSet()
        val nextIds = nextRegions.map { it.id }.toSet()

        if (previousIds == nextIds) return EntryDecision.Allowed

        // EXIT Logic: Check regions we are leaving
        val leaving = previousRegions.filter { it.id !in nextIds }

        for (region in leaving) {
            val context = ProtectionListenerFlagContext(
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
                    processActions(listOf(region), from?.toBukkitLocation(), player, event, RegionFlagKey.EXIT_ACTION)
                    return EntryDecision.Blocked(region, RegionFlagKey.EXIT, result)
                }
                is FlagEvaluation.Allow -> break
                is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
                else -> {}
            }
        }

        // ENTRY Logic: Check regions we are entering
        val entering = nextRegions.filter { it.id !in previousIds }

        for (region in entering) {
            val context = ProtectionListenerFlagContext(
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
                    processActions(listOf(region), to?.toBukkitLocation(), player, event, RegionFlagKey.ENTRY_ACTION)
                    return EntryDecision.Blocked(region, RegionFlagKey.ENTRY, result)
                }
                is FlagEvaluation.Allow -> break
                is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
                else -> {}
            }
        }

        processActions(entering, to?.toBukkitLocation(), player, event, RegionFlagKey.ENTRY_ACTION)
        processActions(leaving, from?.toBukkitLocation(), player, event, RegionFlagKey.EXIT_ACTION)

        return EntryDecision.Allowed
    }

    private fun processActions(
        regions: List<RegionModel>,
        location: Location?,
        player: Player,
        event: Event,
        flag: RegionFlagKey,
    ) {
        if (regions.isEmpty()) return
        for (region in regions) {
            val context = ProtectionListenerFlagContext(
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
