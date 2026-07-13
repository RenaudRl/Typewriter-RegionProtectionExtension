package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.slf4j.LoggerFactory

/**
 * Controls elytra gliding via the GLIDE flag.
 * "allow" = force enable, "deny" = force disable, "default" = no change.
 */
@Singleton
class GlideProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("GlideProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onToggleFlight(event: PlayerToggleFlightEvent) {
        if (!event.isFlying) return
        val player = event.player
        val (evaluation, context) = evaluateFlag(
            RegionFlagKey.GLIDE, event, player.location, player, source = player
        )
        if (evaluation is FlagEvaluation.Modify) {
            val force = evaluation.metadata["glide.force"] as? Boolean
            if (force == false) {
                event.isCancelled = true
                player.isFlying = false
                player.allowFlight = false
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onToggleGlide(event: EntityToggleGlideEvent) {
        val player = event.entity as? Player ?: return
        if (!event.isGliding) return
        val (evaluation, context) = evaluateFlag(
            RegionFlagKey.GLIDE, event, player.location, player, source = player
        )
        if (evaluation is FlagEvaluation.Modify) {
            val force = evaluation.metadata["glide.force"] as? Boolean
            if (force == false) {
                event.isCancelled = true
            }
        }
    }
}
