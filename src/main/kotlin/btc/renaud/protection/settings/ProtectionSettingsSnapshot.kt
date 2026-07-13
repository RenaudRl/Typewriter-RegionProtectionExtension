package btc.renaud.protection.settings

import net.kyori.adventure.bossbar.BossBar

/**
 * Immutable snapshot of protection settings for a specific player.
 * Built from [ProtectionSettingsEntry] with per-player Var resolution.
 */
data class ProtectionSettingsSnapshot(
    val refreshTicks: Long = 40,
    val bossBarColor: BossBar.Color = BossBar.Color.BLUE,
    val bossBarOverlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
    val showDeniedMessages: Boolean = true,
    val deniedMessageChannels: MessageChannelPreferences = MessageChannelPreferences(),
    val customMessageChannels: MessageChannelPreferences = MessageChannelPreferences(),
    val messageBossBarDurationTicks: Long = 100,
    val messages: ProtectionMessages = ProtectionMessages(),
    val templates: ProtectionMessageTemplates = ProtectionMessageTemplates.default(),
) {
    companion object {
        fun default(
            messages: ProtectionMessages = ProtectionMessages(),
            templates: ProtectionMessageTemplates = ProtectionMessageTemplates.default(),
        ): ProtectionSettingsSnapshot = ProtectionSettingsSnapshot(
            messages = messages,
            templates = templates,
        )
    }
}

/**
 * Player preferences for which channels receive protection messages.
 */
data class MessageChannelPreferences(
    val chat: Boolean = true,
    val actionBar: Boolean = false,
    val bossBar: Boolean = false,
) {
    val isEmpty: Boolean get() = !chat && !actionBar && !bossBar
}
