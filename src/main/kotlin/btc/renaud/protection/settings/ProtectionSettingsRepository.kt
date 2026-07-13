package btc.renaud.protection.settings

import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.entry.entries.get
import btc.renaud.protection.entry.settings.ProtectionMessageSettings
import btc.renaud.protection.entry.settings.ProtectionSettingsEntry
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Singleton
class ProtectionSettingsRepository {
    companion object {
        private const val DEFAULT_DENIED_ACTION_TEMPLATE = "<red>Denied Action{reason_line}</red>"
    }

    private val logger = LoggerFactory.getLogger("ProtectionSettingsRepository")
    @Volatile
    private var lastInvalidColor: String? = null
    @Volatile
    private var lastInvalidOverlay: String? = null
    private val blankTemplateWarnings = ConcurrentHashMap.newKeySet<String>()
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val defaultMessageSettings = ProtectionMessageSettings(deniedAction = DEFAULT_DENIED_ACTION_TEMPLATE)
    private val defaultMessages = ProtectionMessages()
    private val defaultTemplates = createMessageTemplates(defaultMessageSettings, DEFAULT_DENIED_ACTION_TEMPLATE, warn = false)
    private val defaultSnapshot = ProtectionSettingsSnapshot.default(
        messages = defaultMessages,
        templates = defaultTemplates,
    )

    // ─── Cache: UUID → (snapshot, timestamp) ───
    private val snapshotCache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTtlMs = TimeUnit.SECONDS.toMillis(5)

    fun snapshot(player: Player?): ProtectionSettingsSnapshot {
        if (player == null) return defaultSnapshot
        val uuid = player.uniqueId.toString()
        val now = System.currentTimeMillis()
        val cached = snapshotCache[uuid]
        if (cached != null && (now - cached.timestamp) < cacheTtlMs) {
            return cached.snapshot
        }
        val fresh = buildSnapshot(player)
        snapshotCache[uuid] = CacheEntry(fresh, now)
        // Periodic cleanup
        if (snapshotCache.size > 1000) {
            val cutoff = now - cacheTtlMs
            snapshotCache.values.removeIf { it.timestamp < cutoff }
        }
        return fresh
    }

    /** Invalidate cache for a specific player (e.g. on settings change). */
    fun invalidateCache(playerId: String) {
        snapshotCache.remove(playerId)
    }

    /** Invalidate entire cache (e.g. on reload). */
    fun invalidateAllCache() {
        snapshotCache.clear()
    }

    private fun buildSnapshot(player: Player?): ProtectionSettingsSnapshot {
        val entry = runCatching { Query.firstWhere<ProtectionSettingsEntry> { true } }
            .onFailure { error ->
                logger.debug("Falling back to default protection settings: {}", error.message)
            }
            .getOrNull()
        if (entry == null) {
            return defaultSnapshot
        }
        val refreshTicks = max(5, entry.inspectRefreshTicks.get(player) ?: defaultSnapshot.refreshTicks.toInt())
        val color = parseColor(entry.inspectBossBarColor)
        val overlay = parseOverlay(entry.inspectBossBarOverlay)
        val showMessages = entry.showDeniedMessages.get(player) ?: defaultSnapshot.showDeniedMessages
        val deniedChannels = MessageChannelPreferences(
            chat = entry.deniedMessageChat.get(player) ?: defaultSnapshot.deniedMessageChannels.chat,
            actionBar = entry.deniedMessageActionBar.get(player)
                ?: defaultSnapshot.deniedMessageChannels.actionBar,
            bossBar = entry.deniedMessageBossBar.get(player)
                ?: defaultSnapshot.deniedMessageChannels.bossBar,
        )
        val customChannels = MessageChannelPreferences(
            chat = entry.customMessageChat.get(player) ?: defaultSnapshot.customMessageChannels.chat,
            actionBar = entry.customMessageActionBar.get(player)
                ?: defaultSnapshot.customMessageChannels.actionBar,
            bossBar = entry.customMessageBossBar.get(player)
                ?: defaultSnapshot.customMessageChannels.bossBar,
        )
        val bossBarDuration = max(20, entry.messageBossBarDurationTicks).toLong()
        val messageTemplates = createMessageTemplates(entry.messageTemplates, entry.deniedMessageTemplate, warn = true)
        return ProtectionSettingsSnapshot(
            refreshTicks = refreshTicks.toLong(),
            bossBarColor = color,
            bossBarOverlay = overlay,
            showDeniedMessages = showMessages,
            deniedMessageChannels = deniedChannels,
            customMessageChannels = customChannels,
            messageBossBarDurationTicks = bossBarDuration,
            messages = entry.messages,
            templates = messageTemplates,
        )
    }

    private fun parseColor(raw: String): BossBar.Color {
        return BossBar.Color.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: run {
                if (!raw.equals(lastInvalidColor, ignoreCase = true)) {
                    lastInvalidColor = raw
                    logger.warn("Unknown bossbar color '{}' in protection_settings entry, defaulting to BLUE", raw)
                }
                BossBar.Color.BLUE
            }
    }

    private fun parseOverlay(raw: String): BossBar.Overlay {
        return BossBar.Overlay.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: run {
                if (!raw.equals(lastInvalidOverlay, ignoreCase = true)) {
                    lastInvalidOverlay = raw
                    logger.warn("Unknown bossbar overlay '{}' in protection_settings entry, defaulting to PROGRESS", raw)
                }
                BossBar.Overlay.PROGRESS
            }
    }

    private fun createMessageTemplates(
        settings: ProtectionMessageSettings?,
        deniedTemplate: String?,
        warn: Boolean,
    ): ProtectionMessageTemplates {
        val base = settings ?: defaultMessageSettings
        val effective = if (!deniedTemplate.isNullOrBlank()) {
            base.copy(deniedAction = deniedTemplate)
        } else {
            base
        }
        val values = ProtectionMessageTemplateValues(
            deniedAction = sanitizeTemplate(effective.deniedAction, defaultMessageSettings.deniedAction, "messages.deniedAction", warn),
            deniedEntry = sanitizeTemplate(effective.deniedEntry, defaultMessageSettings.deniedEntry, "messages.deniedEntry", warn),
            inspectionToggleOn = sanitizeTemplate(effective.inspectionToggleOn, defaultMessageSettings.inspectionToggleOn, "messages.inspectionToggleOn", warn),
            inspectionToggleOff = sanitizeTemplate(effective.inspectionToggleOff, defaultMessageSettings.inspectionToggleOff, "messages.inspectionToggleOff", warn),
            inspectionInitializingBossBar = sanitizeTemplate(
                effective.inspectionInitializingBossBar,
                defaultMessageSettings.inspectionInitializingBossBar,
                "messages.inspectionInitializingBossBar",
                warn
            ),
            inspectionNoRegionActionBar = sanitizeTemplate(
                effective.inspectionNoRegionActionBar,
                defaultMessageSettings.inspectionNoRegionActionBar,
                "messages.inspectionNoRegionActionBar",
                warn
            ),
            inspectionNoRegionBossBar = sanitizeTemplate(
                effective.inspectionNoRegionBossBar,
                defaultMessageSettings.inspectionNoRegionBossBar,
                "messages.inspectionNoRegionBossBar",
                warn
            ),
            inspectionActionBarPrefix = sanitizeTemplate(
                effective.inspectionActionBarPrefix,
                defaultMessageSettings.inspectionActionBarPrefix,
                "messages.inspectionActionBarPrefix",
                warn
            ),
            inspectionActionBarNoFlags = sanitizeTemplate(
                effective.inspectionActionBarNoFlags,
                defaultMessageSettings.inspectionActionBarNoFlags,
                "messages.inspectionActionBarNoFlags",
                warn
            ),
            inspectionActionBarDetail = sanitizeTemplate(
                effective.inspectionActionBarDetail,
                defaultMessageSettings.inspectionActionBarDetail,
                "messages.inspectionActionBarDetail",
                warn
            ),
            inspectionActionBarSeparator = sanitizeTemplate(
                effective.inspectionActionBarSeparator,
                defaultMessageSettings.inspectionActionBarSeparator,
                "messages.inspectionActionBarSeparator",
                warn
            ),
            inspectionActionBarMoreFlags = sanitizeTemplate(
                effective.inspectionActionBarMoreFlags,
                defaultMessageSettings.inspectionActionBarMoreFlags,
                "messages.inspectionActionBarMoreFlags",
                warn
            ),
            inspectionActionBarAdditionalRegions = sanitizeTemplate(
                effective.inspectionActionBarAdditionalRegions,
                defaultMessageSettings.inspectionActionBarAdditionalRegions,
                "messages.inspectionActionBarAdditionalRegions",
                warn
            ),
            inspectionActionBarInheritedIndicator = sanitizeTemplate(
                effective.inspectionActionBarInheritedIndicator,
                defaultMessageSettings.inspectionActionBarInheritedIndicator,
                "messages.inspectionActionBarInheritedIndicator",
                warn
            ),
            inspectionActionBarOverrideIndicator = sanitizeTemplate(
                effective.inspectionActionBarOverrideIndicator,
                defaultMessageSettings.inspectionActionBarOverrideIndicator,
                "messages.inspectionActionBarOverrideIndicator",
                warn
            ),
            inspectionBossBarTitle = sanitizeTemplate(
                effective.inspectionBossBarTitle,
                defaultMessageSettings.inspectionBossBarTitle,
                "messages.inspectionBossBarTitle",
                warn
            ),
            inspectionBossBarNoFlags = sanitizeTemplate(
                effective.inspectionBossBarNoFlags,
                defaultMessageSettings.inspectionBossBarNoFlags,
                "messages.inspectionBossBarNoFlags",
                warn
            ),
            inspectionBossBarSummaryBase = sanitizeTemplate(
                effective.inspectionBossBarSummaryBase,
                defaultMessageSettings.inspectionBossBarSummaryBase,
                "messages.inspectionBossBarSummaryBase",
                warn
            ),
            inspectionBossBarSummaryInherited = sanitizeTemplate(
                effective.inspectionBossBarSummaryInherited,
                defaultMessageSettings.inspectionBossBarSummaryInherited,
                "messages.inspectionBossBarSummaryInherited",
                warn
            ),
            inspectionBossBarSummaryOverrides = sanitizeTemplate(
                effective.inspectionBossBarSummaryOverrides,
                defaultMessageSettings.inspectionBossBarSummaryOverrides,
                "messages.inspectionBossBarSummaryOverrides",
                warn
            ),
            inspectionBossBarDetailPrefix = sanitizeTemplate(
                effective.inspectionBossBarDetailPrefix,
                defaultMessageSettings.inspectionBossBarDetailPrefix,
                "messages.inspectionBossBarDetailPrefix",
                warn
            ),
            inspectionBossBarDetail = sanitizeTemplate(
                effective.inspectionBossBarDetail,
                defaultMessageSettings.inspectionBossBarDetail,
                "messages.inspectionBossBarDetail",
                warn
            ),
            inspectionBossBarDetailSeparator = sanitizeTemplate(
                effective.inspectionBossBarDetailSeparator,
                defaultMessageSettings.inspectionBossBarDetailSeparator,
                "messages.inspectionBossBarDetailSeparator",
                warn
            ),
            inspectionBossBarDetailMore = sanitizeTemplate(
                effective.inspectionBossBarDetailMore,
                defaultMessageSettings.inspectionBossBarDetailMore,
                "messages.inspectionBossBarDetailMore",
                warn
            ),
            inspectionBossBarDetailInheritedIndicator = sanitizeTemplate(
                effective.inspectionBossBarDetailInheritedIndicator,
                defaultMessageSettings.inspectionBossBarDetailInheritedIndicator,
                "messages.inspectionBossBarDetailInheritedIndicator",
                warn
            ),
            inspectionBossBarDetailOverrideIndicator = sanitizeTemplate(
                effective.inspectionBossBarDetailOverrideIndicator,
                defaultMessageSettings.inspectionBossBarDetailOverrideIndicator,
                "messages.inspectionBossBarDetailOverrideIndicator",
                warn
            ),
        )
        return ProtectionMessageTemplates(miniMessage, logger, values)
    }

    private fun sanitizeTemplate(raw: String, fallback: String, key: String, warn: Boolean): String {
        if (raw.isBlank()) {
            if (warn && blankTemplateWarnings.add(key)) {
                logger.warn("Template '{}' is blank in protection_settings entry, falling back to default", key)
            }
            return fallback
        }
        return raw
    }
}

private data class CacheEntry(
    val snapshot: ProtectionSettingsSnapshot,
    val timestamp: Long,
)
