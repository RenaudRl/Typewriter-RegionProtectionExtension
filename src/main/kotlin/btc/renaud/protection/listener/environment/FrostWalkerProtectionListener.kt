package btc.renaud.protection.listener.environment

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFormEvent
import org.slf4j.LoggerFactory

/**
 * Controls frost walker ice formation via the FROSTWALKER flag.
 */
@Singleton
class FrostWalkerProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("FrostWalkerProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        // Frost walker creates ice (FROSTED_ICE) from water
        if (event.newState.type != Material.FROSTED_ICE) return

        val (evaluation, _) = evaluateFlag(
            RegionFlagKey.FROSTWALKER,
            event,
            event.block.location
        )
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
            logger.debug("Blocked frost walker ice formation at {}", event.block.location)
        }
    }
}
