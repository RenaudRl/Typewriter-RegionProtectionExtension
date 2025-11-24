package com.typewritermc.protection.listener

import com.typewritermc.protection.settings.MessageChannelPreferences
import com.typewritermc.protection.settings.ProtectionSettingsSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

object ProtectionMessageDispatcher {
    private val bossBars = ConcurrentHashMap<UUID, BossBarSession>()

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
        previous?.cancel(plugin, player)

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
    ) {
        fun cancel(plugin: Plugin, player: Player) {
            task.cancel()
            SchedulerCompat.run(plugin, player.location) {
                player.hideBossBar(bar)
            }
        }
    }
}

