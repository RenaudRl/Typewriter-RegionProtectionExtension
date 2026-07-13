package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.slf4j.LoggerFactory
import java.util.Locale

@Singleton
class ChatProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("ChatProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val location = player.location
        
        // Check for SEND_CHAT
        val (sendEval, context) = evaluateFlag(RegionFlagKey.SEND_CHAT, event, location, player, source = player)
        if (sendEval is FlagEvaluation.Denied) {
            event.isCancelled = true
            if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.SEND_CHAT, sendEval)
            return
        }

        // Check for RECEIVE_CHAT (filter recipients)
        val viewers = event.viewers()
        viewers.removeIf { viewer ->
            if (viewer is org.bukkit.entity.Player) {
                val (receiveEval, _) = evaluateFlag(RegionFlagKey.RECEIVE_CHAT, event, viewer.location, viewer, source = viewer)
                receiveEval is FlagEvaluation.Denied
            } else {
                false
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val location = player.location
        val fullCommand = event.message.substring(1)
        val commandName = fullCommand.split(" ")[0].lowercase(Locale.ROOT)

        // Check allowed-cmds (override)
        val (allowedEval, _) = evaluateFlag(RegionFlagKey.ALLOWED_CMDS, event, location, player, source = player)
        if (allowedEval is FlagEvaluation.Modify) {
            val allowedList = allowedEval.metadata[RegionFlagKey.ALLOWED_CMDS.id] as? List<*>
            if (allowedList?.any { it.toString().lowercase(Locale.ROOT) == commandName } == true) {
                return // Allowed, skip blocked-cmds check
            }
        }

        // Check blocked-cmds
        val (blockedEval, context) = evaluateFlag(RegionFlagKey.BLOCKED_CMDS, event, location, player, source = player)
        if (blockedEval is FlagEvaluation.Modify) {
            val blockedList = blockedEval.metadata[RegionFlagKey.BLOCKED_CMDS.id] as? List<*>
            if (blockedList?.any { it.toString().lowercase(Locale.ROOT) == commandName || it == "*" } == true) {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.BLOCKED_CMDS, FlagEvaluation.Denied("command.blocked"))
            }
        }
    }
}
