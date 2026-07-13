package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/**
 * Applies chat prefix/suffix from region flags via AsyncChatEvent.
 * Performance: modifies the message directly on the async chat pipeline.
 */
@Singleton
class ChatPrefixSuffixListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("ChatPrefixSuffixListener")
    private val legacy = LegacyComponentSerializer.legacySection()

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val location = player.location

        val (prefixEval, _) = evaluateFlag(RegionFlagKey.CHAT_PREFIX, event, location, player, source = player)
        val (suffixEval, _) = evaluateFlag(RegionFlagKey.CHAT_SUFFIX, event, location, player, source = player)

        val prefix = if (prefixEval is FlagEvaluation.Modify) {
            prefixEval.metadata["chat.prefix"]?.let { legacy.serialize(Component.text(it.toString())) } ?: ""
        } else ""

        val suffix = if (suffixEval is FlagEvaluation.Modify) {
            suffixEval.metadata["chat.suffix"]?.let { legacy.serialize(Component.text(it.toString())) } ?: ""
        } else ""

        if (prefix.isNotEmpty() || suffix.isNotEmpty()) {
            val original = event.message()
            val modified = Component.text(prefix).append(original).append(Component.text(suffix))
            event.message(modified)
            logger.debug("Applied chat prefix/suffix for {}: [{}]", player.name, prefix)
        }
    }
}
