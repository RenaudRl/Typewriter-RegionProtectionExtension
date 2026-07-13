package btc.renaud.protection.listener.environment

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.slf4j.LoggerFactory

@Singleton
class PistonProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("PistonProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        // Check blocks being pushed
        event.blocks.forEach { block ->
            val (evaluation, _) = evaluateFlag(RegionFlagKey.PISTONS, event, block.location)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                return
            }
        }
        
        // Also check the destination of the push
        val direction = event.direction
        event.blocks.forEach { block ->
            val destination = block.getRelative(direction).location
            val (evaluation, _) = evaluateFlag(RegionFlagKey.PISTONS, event, destination)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        event.blocks.forEach { block ->
            val (evaluation, _) = evaluateFlag(RegionFlagKey.PISTONS, event, block.location)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                return
            }
        }

        val direction = event.direction
        event.blocks.forEach { block ->
            val destination = block.getRelative(direction).location
            val (evaluation, _) = evaluateFlag(RegionFlagKey.PISTONS, event, destination)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                return
            }
        }
    }
}
