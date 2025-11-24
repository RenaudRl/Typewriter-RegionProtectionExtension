package com.typewritermc.protection.settings

import com.typewritermc.core.entries.Query
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.protection.entry.settings.ProtectionMessageSettings
import com.typewritermc.protection.entry.settings.ProtectionSettingsEntry
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
import kotlin.math.max

object ProtectionSettingsRepository {
    private const val DEFAULT_DENIED_ACTION_TEMPLATE = "<red>Denied Action{reason_line}</red>"

    private val logger = LoggerFactory.getLogger("ProtectionSettingsRepository")
    private var lastInvalidColor: String? = null
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

    fun snapshot(player: Player?): ProtectionSettingsSnapshot {
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
                ?: defaultSnapshot.deniedMessageChannels.bossBar
        )
        val customChannels = MessageChannelPreferences(
            chat = entry.customMessageChat.get(player) ?: defaultSnapshot.customMessageChannels.chat,
            actionBar = entry.customMessageActionBar.get(player)
                ?: defaultSnapshot.customMessageChannels.actionBar,
            bossBar = entry.customMessageBossBar.get(player)
                ?: defaultSnapshot.customMessageChannels.bossBar
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

data class ProtectionSettingsSnapshot(
    val refreshTicks: Long,
    val bossBarColor: BossBar.Color,
    val bossBarOverlay: BossBar.Overlay,
    val showDeniedMessages: Boolean,
    val deniedMessageChannels: MessageChannelPreferences,
    val customMessageChannels: MessageChannelPreferences,
    val messageBossBarDurationTicks: Long,
    val messages: ProtectionMessages,
    val templates: ProtectionMessageTemplates,
) {
    fun deniedAction(reason: String?): Component = templates.deniedAction(reason)

    fun deniedEntry(region: String, flag: String): Component = templates.deniedEntry(region, flag)

    companion object {
        fun default(
            messages: ProtectionMessages,
            templates: ProtectionMessageTemplates,
        ): ProtectionSettingsSnapshot = ProtectionSettingsSnapshot(
            refreshTicks = 40,
            bossBarColor = BossBar.Color.BLUE,
            bossBarOverlay = BossBar.Overlay.PROGRESS,
            showDeniedMessages = true,
            deniedMessageChannels = MessageChannelPreferences(chat = true, actionBar = false, bossBar = false),
            customMessageChannels = MessageChannelPreferences(chat = true, actionBar = false, bossBar = false),
            messageBossBarDurationTicks = 100,
            messages = messages,
            templates = templates,
        )
    }
}

data class MessageChannelPreferences(
    val chat: Boolean,
    val actionBar: Boolean,
    val bossBar: Boolean,
) {
    val isEmpty: Boolean get() = !chat && !actionBar && !bossBar
}

data class ProtectionMessageTemplateValues(
    val deniedAction: String,
    val deniedEntry: String,
    val inspectionToggleOn: String,
    val inspectionToggleOff: String,
    val inspectionInitializingBossBar: String,
    val inspectionNoRegionActionBar: String,
    val inspectionNoRegionBossBar: String,
    val inspectionActionBarPrefix: String,
    val inspectionActionBarNoFlags: String,
    val inspectionActionBarDetail: String,
    val inspectionActionBarSeparator: String,
    val inspectionActionBarMoreFlags: String,
    val inspectionActionBarAdditionalRegions: String,
    val inspectionActionBarInheritedIndicator: String,
    val inspectionActionBarOverrideIndicator: String,
    val inspectionBossBarTitle: String,
    val inspectionBossBarNoFlags: String,
    val inspectionBossBarSummaryBase: String,
    val inspectionBossBarSummaryInherited: String,
    val inspectionBossBarSummaryOverrides: String,
    val inspectionBossBarDetailPrefix: String,
    val inspectionBossBarDetail: String,
    val inspectionBossBarDetailSeparator: String,
    val inspectionBossBarDetailMore: String,
    val inspectionBossBarDetailInheritedIndicator: String,
    val inspectionBossBarDetailOverrideIndicator: String,
)

class ProtectionMessageTemplates(
    private val miniMessage: MiniMessage,
    private val logger: Logger,
    private val values: ProtectionMessageTemplateValues,
) {
    private val invalidTemplates = ConcurrentHashMap<String, String>()
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    fun deniedAction(reason: String?): Component {
        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() }
        val reasonLine = trimmed?.let { ": $it" } ?: ""
        return render(
            "messages.deniedAction",
            values.deniedAction,
            mapOf(
                "reason" to Component.text(trimmed ?: ""),
                "reason_line" to Component.text(reasonLine)
            )
        )
    }

    fun deniedEntry(region: String, flag: String): Component {
        return render(
            "messages.deniedEntry",
            values.deniedEntry,
            mapOf(
                "region" to Component.text(region),
                "flag" to Component.text(flag)
            )
        )
    }

    fun inspectionToggleOn(): Component = render("messages.inspectionToggleOn", values.inspectionToggleOn)

    fun inspectionToggleOff(): Component = render("messages.inspectionToggleOff", values.inspectionToggleOff)

    fun inspectionInitializingBossBar(): Component = render("messages.inspectionInitializingBossBar", values.inspectionInitializingBossBar)

    fun inspectionNoRegionActionBar(): Component = render("messages.inspectionNoRegionActionBar", values.inspectionNoRegionActionBar)

    fun inspectionNoRegionBossBar(): Component = render("messages.inspectionNoRegionBossBar", values.inspectionNoRegionBossBar)

    fun inspectionActionBarPrefix(priority: Int, region: String): Component {
        return render(
            "messages.inspectionActionBarPrefix",
            values.inspectionActionBarPrefix,
            mapOf(
                "priority" to Component.text(priority.toString()),
                "region" to Component.text(region)
            )
        )
    }

    fun inspectionActionBarNoFlags(): Component = render("messages.inspectionActionBarNoFlags", values.inspectionActionBarNoFlags)

    fun inspectionActionBarSeparator(): Component = render("messages.inspectionActionBarSeparator", values.inspectionActionBarSeparator)

    fun inspectionActionBarDetail(
        flag: String,
        value: ComponentLike,
        inherited: Boolean,
        overrides: Boolean,
    ): Component {
        return render(
            "messages.inspectionActionBarDetail",
            values.inspectionActionBarDetail,
            mapOf(
                "flag" to Component.text(flag),
                "value" to value,
                "inherited" to indicatorComponent(
                    inherited,
                    "messages.inspectionActionBarInheritedIndicator",
                    values.inspectionActionBarInheritedIndicator
                ),
                "overrides" to indicatorComponent(
                    overrides,
                    "messages.inspectionActionBarOverrideIndicator",
                    values.inspectionActionBarOverrideIndicator
                )
            )
        )
    }

    fun inspectionActionBarMoreFlags(): Component = render("messages.inspectionActionBarMoreFlags", values.inspectionActionBarMoreFlags)

    fun inspectionActionBarAdditionalRegions(count: Int): Component {
        return render(
            "messages.inspectionActionBarAdditionalRegions",
            values.inspectionActionBarAdditionalRegions,
            mapOf("count" to Component.text(count.toString()))
        )
    }

    fun inspectionBossBarTitle(
        index: Int,
        total: Int,
        priority: Int,
        region: String,
        summary: ComponentLike,
        flags: ComponentLike,
    ): Component {
        return render(
            "messages.inspectionBossBarTitle",
            values.inspectionBossBarTitle,
            mapOf(
                "index" to Component.text(index.toString()),
                "total" to Component.text(total.toString()),
                "priority" to Component.text(priority.toString()),
                "region" to Component.text(region),
                "summary" to summary,
                "flags" to flags
            )
        )
    }

    fun inspectionBossBarNoFlags(): Component = render("messages.inspectionBossBarNoFlags", values.inspectionBossBarNoFlags)

    fun inspectionBossBarSummaryBase(active: Int, local: Int): Component {
        return render(
            "messages.inspectionBossBarSummaryBase",
            values.inspectionBossBarSummaryBase,
            mapOf(
                "active" to Component.text(active.toString()),
                "local" to Component.text(local.toString())
            )
        )
    }

    fun inspectionBossBarSummaryInherited(count: Int): Component {
        return render(
            "messages.inspectionBossBarSummaryInherited",
            values.inspectionBossBarSummaryInherited,
            mapOf("count" to Component.text(count.toString()))
        )
    }

    fun inspectionBossBarSummaryOverrides(count: Int): Component {
        return render(
            "messages.inspectionBossBarSummaryOverrides",
            values.inspectionBossBarSummaryOverrides,
            mapOf("count" to Component.text(count.toString()))
        )
    }

    fun inspectionBossBarDetailPrefix(): Component = render("messages.inspectionBossBarDetailPrefix", values.inspectionBossBarDetailPrefix)

    fun inspectionBossBarDetail(
        flag: String,
        value: ComponentLike,
        inherited: Boolean,
        overrides: Boolean,
    ): Component {
        return render(
            "messages.inspectionBossBarDetail",
            values.inspectionBossBarDetail,
            mapOf(
                "flag" to Component.text(flag),
                "value" to value,
                "inherited" to indicatorComponent(
                    inherited,
                    "messages.inspectionBossBarDetailInheritedIndicator",
                    values.inspectionBossBarDetailInheritedIndicator
                ),
                "overrides" to indicatorComponent(
                    overrides,
                    "messages.inspectionBossBarDetailOverrideIndicator",
                    values.inspectionBossBarDetailOverrideIndicator
                )
            )
        )
    }

    fun inspectionBossBarDetailSeparator(): Component = render("messages.inspectionBossBarDetailSeparator", values.inspectionBossBarDetailSeparator)

    fun inspectionBossBarDetailMore(): Component = render("messages.inspectionBossBarDetailMore", values.inspectionBossBarDetailMore)

    private fun indicatorComponent(active: Boolean, key: String, template: String): ComponentLike {
        return if (active) {
            render(key, template)
        } else {
            Component.empty()
        }
    }

    private fun render(
        key: String,
        template: String,
        placeholders: Map<String, ComponentLike> = emptyMap(),
    ): Component {
        val normalized = normalizeTemplate(template, placeholders.keys)
        val resolver = TagResolver.builder().apply {
            placeholders.forEach { (name, component) ->
                this.resolver(Placeholder.component(name, component.asComponent()))
            }
        }.build()
        return runCatching { miniMessage.deserialize(normalized, resolver) }
            .getOrElse { error ->
                val previous = invalidTemplates.put(key, template)
                if (previous != template) {
                    logger.warn("Failed to parse protection message template {}='{}': {}", key, template, error.message)
                }
                var fallback = template
                placeholders.forEach { (name, component) ->
                    val plain = plainSerializer.serialize(component.asComponent())
                    fallback = fallback.replace("{$name}", plain)
                }
                Component.text(fallback)
            }
    }

    private fun normalizeTemplate(template: String, names: Set<String>): String {
        var normalized = template
        names.forEach { name ->
            normalized = normalized.replace("{$name}", "<$name>")
        }
        return normalized
    }
}

