package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Blocks WorldEdit commands and API usage when WORLD_EDIT flag is deny.
 */
@Singleton
class WorldEditProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("WorldEditProtectionListener")

    private val worldEditCommands = setOf(
        "worldedit", "we", "//", "/copy", "/paste", "/cut", "/set", "/replace",
        "/walls", "/sphere", "/cyl", "/pyramid", "/forest", "/undo", "/redo",
        "/pos1", "/pos2", "/hpos1", "/hpos2", "/wand", "/toggleeditmat",
        "/limit", "/timeout", "/reorder", "/mask", "/gmask", "/toggleplace",
        "/searchitem", "/sel", "/desel", "/pos", "/hpos", "/chunk", "/listchunks",
        "/delchunks", "/restore", "/snapshot", "/snap", "/place", "/center",
        "/smooth", "/regen", "/naturalize", "/flora", "/forestgen", "/pumpkins",
        "/hcyl", "/hsphere", "/hpyramid", "/cyl", "/sphere", "/pyramid",
        "/generate", "/gen", "/g", "/deform", "/expression", "/eval",
        "/calc", "/solve", "/fill", "/drain", "/fixwater", "/fixlava",
        "/removeabove", "/removebelow", "/removenear", "/butcher", "/kill",
        "/thaw", "/snow", "/thaw", "/green", "/ceil", "/up", "/down",
        "/size", "/mat", "/smoothbrush", "/brush", "/br", "/tool",
        "/mat", "/mask", "/transform", "/generatebiome", "/biomelist",
        "/biomeinfo", "/delchunks", "/listchunks", "/chunkinfo"
    )

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val location = player.location
        val message = event.message.substring(1)
        val commandName = message.split(" ")[0].lowercase(Locale.ROOT)

        // Check if it's a WorldEdit command
        if (!isWorldEditCommand(commandName)) return

        val (evaluation, context) = evaluateFlag(
            RegionFlagKey.WORLD_EDIT,
            event,
            location,
            player,
            source = player
        )
        if (evaluation is FlagEvaluation.Modify) {
            val blocked = evaluation.metadata["worldedit.block"] as? Boolean ?: false
            if (blocked) {
                event.isCancelled = true
                logger.debug("Blocked WorldEdit command '{}' for {} in region {}",
                    commandName, player.name, context?.region?.id ?: "unknown")
            }
        }
    }

    private fun isWorldEditCommand(command: String): Boolean {
        if (command in worldEditCommands) return true
        if (command.startsWith("//")) return true
        return false
    }
}
