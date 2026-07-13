package btc.renaud.protection.listener

import btc.renaud.protection.settings.MessageChannelPreferences
import btc.renaud.protection.settings.ProtectionSettingsSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

object ProtectionMessageDispatcher : Listener {
    private val bossBars = ConcurrentHashMap<UUID, BossBarSession>()

    /** Must be called during extension initialization to register the quit listener. */
    fun registerQuitListener(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        bossBars.remove(event.player.uniqueId)?.let { session ->
            session.task.cancel()
        }
    }

    fun send(
        plugin: Plugin,
        player: Player,
        message: Component,
        channels: MessageChannelPreferences,
        snapshot: ProtectionSettingsSnapshot,
    ) {
        if (channels.isEmpty) return
        SchedulerCompat.run(plugin, player.location) {
            if (channels.chat) {
                player.sendMessage(message)
            }
            if (channels.actionBar) {
                player.sendActionBar(message)
            }
            if (channels.bossBar) {
                showBossBar(plugin, player, message, snapshot)
            }
        }
    }

    private fun showBossBar(
        plugin: Plugin,
        player: Player,
        message: Component,
        snapshot: ProtectionSettingsSnapshot,
    ) {
        val previous = bossBars.remove(player.uniqueId)
        previous?.task?.cancel()
        previous?.let { player.hideBossBar(it.bar) }

        val bar = BossBar.bossBar(message, 1.0f, snapshot.bossBarColor, snapshot.bossBarOverlay)
        player.showBossBar(bar)
        val delay = snapshot.messageBossBarDurationTicks.coerceAtLeast(1L)
        val task = SchedulerCompat.runLater(plugin, player.location, delay) {
            player.hideBossBar(bar)
            bossBars.remove(player.uniqueId)
        }
        bossBars[player.uniqueId] = BossBarSession(bar, task)
    }

    private data class BossBarSession(
        val bar: BossBar,
        val task: SchedulerCompat.TaskHandle,
    )
}

