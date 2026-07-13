package btc.renaud.protection.listener.building

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.listener.ProtectionListenerFlagContext
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.Material
import org.bukkit.entity.Ravager
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        val entity = event.entity
        if (entity is Ravager) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.RAVAGER_GRIEF, event, event.block.location)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL && event.clickedBlock?.type == Material.FARMLAND) {
            val block = event.clickedBlock ?: return
            val (evaluation, context) = evaluateFlag(RegionFlagKey.CROP_TRAMPLING, event, block.location, event.player, source = event.player)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.CROP_TRAMPLING, evaluation)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityInteract(event: EntityInteractEvent) {
        if (event.block.type == Material.FARMLAND) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.CROP_TRAMPLING, event, event.block.location)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
            }
        }
    }
}

