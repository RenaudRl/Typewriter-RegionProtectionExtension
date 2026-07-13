package btc.renaud.protection.settings

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

class ProtectionMessageTemplates(
    private val miniMessage: MiniMessage,
    private val logger: Logger,
    private val values: ProtectionMessageTemplateValues,
) {
    private val invalidTemplates = ConcurrentHashMap<String, String>()
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    companion object {
        fun default(): ProtectionMessageTemplates = ProtectionMessageTemplates(
            miniMessage = MiniMessage.miniMessage(),
            logger = org.slf4j.LoggerFactory.getLogger("ProtectionMessageTemplates"),
            values = ProtectionMessageTemplateValues(
                deniedAction = "<red>Action denied{reason_line}</red>",
                deniedEntry = "<red>Entry denied ({flag}) in {region}.</red>",
                inspectionToggleOn = "<green>Flag inspection enabled</green>",
                inspectionToggleOff = "<yellow>Flag inspection disabled</yellow>",
                inspectionInitializingBossBar = "<gold>Loading inspection data…</gold>",
                inspectionNoRegionActionBar = "<gray>No active regions</gray>",
                inspectionNoRegionBossBar = "<dark_gray>Inspection: no regions</dark_gray>",
                inspectionActionBarPrefix = "<gold>[P{priority}]</gold> <aqua>[{region}]</aqua> ",
                inspectionActionBarNoFlags = "<gray>No flags configured</gray>",
                inspectionActionBarDetail = "<gold>{flag}</gold><dark_gray>: </dark_gray>{value}{inherited}{overrides}",
                inspectionActionBarSeparator = "<dark_gray>  </dark_gray>",
                inspectionActionBarMoreFlags = "<dark_gray> …</dark_gray>",
                inspectionActionBarAdditionalRegions = "<blue> • +{count} region(s)</blue>",
                inspectionActionBarInheritedIndicator = "<blue>↑</blue>",
                inspectionActionBarOverrideIndicator = "<light_purple>↻</light_purple>",
                inspectionBossBarTitle = "<green>#{index}</green><dark_gray>/</dark_gray><green>{total}</green><dark_gray> • </dark_gray><gold>P{priority}</gold><dark_gray> • </dark_gray><aqua>{region}</aqua>{flags}{summary}",
                inspectionBossBarNoFlags = "<gray>No active flags</gray>",
                inspectionBossBarSummaryBase = "<gold>{active} active</gold><dark_gray> • </dark_gray><aqua>{local} local</aqua>",
                inspectionBossBarSummaryInherited = "<dark_gray> • </dark_gray><blue>{count} inherited</blue>",
                inspectionBossBarSummaryOverrides = "<dark_gray> • </dark_gray><light_purple>{count} overrides</light_purple>",
                inspectionBossBarDetailPrefix = "<dark_gray> • </dark_gray>",
                inspectionBossBarDetail = "<gold>{flag}</gold><dark_gray>=</dark_gray>{value}{inherited}{overrides}",
                inspectionBossBarDetailSeparator = "<dark_gray>, </dark_gray>",
                inspectionBossBarDetailMore = "<dark_gray> …</dark_gray>",
                inspectionBossBarDetailInheritedIndicator = "<blue>↑</blue>",
                inspectionBossBarDetailOverrideIndicator = "<light_purple>↻</light_purple>",
            ),
        )
    }

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
        value: net.kyori.adventure.text.ComponentLike,
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
        summary: net.kyori.adventure.text.ComponentLike,
        flags: net.kyori.adventure.text.ComponentLike,
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
        value: net.kyori.adventure.text.ComponentLike,
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

    private fun indicatorComponent(active: Boolean, key: String, raw: String): Component {
        return if (active) render(key, raw) else Component.empty()
    }

    private fun render(key: String, template: String, placeholders: Map<String, Any?> = emptyMap()): Component {
        if (template.isBlank()) {
            if (invalidTemplates.putIfAbsent(key, key) == null) {
                logger.warn("Template '{}' is blank", key)
            }
            return Component.empty()
        }
        return try {
            val resolver = TagResolver.resolver(
                placeholders.entries.map { (name, value) ->
                    val component = when (value) {
                        is Component -> value
                        is net.kyori.adventure.text.ComponentLike -> value.asComponent()
                        else -> Component.text(value.toString())
                    }
                    Placeholder.component(name, component)
                }
            )
            miniMessage.deserialize(template, resolver)
        } catch (e: Exception) {
            if (invalidTemplates.putIfAbsent(key, key) == null) {
                logger.warn("Failed to render template '{}': {}", key, e.message)
            }
            Component.text(template)
        }
    }
}
