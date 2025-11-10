package com.typewritermc.protection.entry.settings

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.protection.settings.ProtectionMessages

@Entry(
    "protection_settings",
    "Configuration for protection tooling",
    Colors.BLUE,
    "mdi:shield-cog"
)
@Tags("protection", "settings")
class ProtectionSettingsEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Tick delay between inspection updates (minimum 5 ticks)")
    val inspectRefreshTicks: Var<Int> = ConstVar(40),
    @Help("BossBar color used during /tw protection inspect")
    val inspectBossBarColor: String = "BLUE",
    @Help("BossBar overlay used during /tw protection inspect")
    val inspectBossBarOverlay: String = "PROGRESS",
    @Help("Display chat messages when a player is denied an action")
    val showDeniedMessages: Var<Boolean> = ConstVar(true),
    @Help("Send denied messages to the player's chat")
    val deniedMessageChat: Var<Boolean> = ConstVar(true),
    @Help("Send denied messages to the action bar")
    val deniedMessageActionBar: Var<Boolean> = ConstVar(false),
    @Help("Send denied messages via a temporary boss bar")
    val deniedMessageBossBar: Var<Boolean> = ConstVar(false),
    @Help("Send custom flag messages to the player's chat")
    val customMessageChat: Var<Boolean> = ConstVar(true),
    @Help("Send custom flag messages to the action bar")
    val customMessageActionBar: Var<Boolean> = ConstVar(false),
    @Help("Send custom flag messages via a temporary boss bar")
    val customMessageBossBar: Var<Boolean> = ConstVar(false),
    @Help("Duration in ticks before boss bar messages disappear (minimum 20 ticks)")
    val messageBossBarDurationTicks: Int = 100,
    @Help("MiniMessage template for denied action messages. Use {reason} for the raw reason and {reason_line} for ': reason'.")
    val deniedMessageTemplate: String = "<red>Denied Action{reason_line}</red>",
    @Help(
        "Advanced MiniMessage templates used for denied feedback and inspection mode. " +
            "Supports placeholders documented in the protection guide."
    )
    val messageTemplates: ProtectionMessageSettings = ProtectionMessageSettings(),
    @Help(
        "Configure all in-game messages used by protection tools. " +
            "Supports MiniMessage formatting and placeholders documented in the Protection guide."
    )
    val messages: ProtectionMessages = ProtectionMessages(),
) : ManifestEntry

data class ProtectionMessageSettings(
    @Help("Denied action template. Placeholders: {reason}, {reason_line} (prefixed with ': ' when reason available).")
    val deniedAction: String = "<red>Action denied{reason_line}</red>",
    @Help("Denied entry template. Placeholders: {region}, {flag}.")
    val deniedEntry: String = "<red>Entry denied ({flag}) in {region}.</red>",
    @Help("Action bar feedback when inspection is enabled.")
    val inspectionToggleOn: String = "<green>Flag inspection enabled</green>",
    @Help("Action bar feedback when inspection is disabled.")
    val inspectionToggleOff: String = "<yellow>Flag inspection disabled</yellow>",
    @Help("BossBar title displayed while the inspection session initializes.")
    val inspectionInitializingBossBar: String = "<gold>Loading inspection data…</gold>",
    @Help("Action bar message displayed when no regions are active during inspection.")
    val inspectionNoRegionActionBar: String = "<gray>No active regions</gray>",
    @Help("BossBar title displayed when no regions are active during inspection.")
    val inspectionNoRegionBossBar: String = "<dark_gray>Inspection: no regions</dark_gray>",
    @Help("Prefix for the inspection action bar. Placeholders: {priority}, {region}.")
    val inspectionActionBarPrefix: String = "<gold>[P{priority}]</gold> <aqua>[{region}]</aqua> ",
    @Help("Action bar fallback when the dominant region exposes no active flags.")
    val inspectionActionBarNoFlags: String = "<gray>No flags configured</gray>",
    @Help("Action bar entry for each flag. Placeholders: {flag}, {value}, {inherited}, {overrides}.")
    val inspectionActionBarDetail: String = "<gold>{flag}</gold><dark_gray>: </dark_gray>{value}{inherited}{overrides}",
    @Help("Separator between flag entries in the inspection action bar.")
    val inspectionActionBarSeparator: String = "<dark_gray>  </dark_gray>",
    @Help("Suffix appended when additional flag previews are truncated.")
    val inspectionActionBarMoreFlags: String = "<dark_gray> …</dark_gray>",
    @Help("Suffix appended when additional regions overlap. Placeholder: {count}.")
    val inspectionActionBarAdditionalRegions: String = "<blue> • +{count} region(s)</blue>",
    @Help("Indicator appended to action bar details when a flag is inherited.")
    val inspectionActionBarInheritedIndicator: String = "<blue>↑</blue>",
    @Help("Indicator appended to action bar details when a flag overrides its parent.")
    val inspectionActionBarOverrideIndicator: String = "<light_purple>↻</light_purple>",
    @Help(
        "BossBar title template per region. Placeholders: {index}, {total}, {priority}, {region}, {flags}, {summary}."
    )
    val inspectionBossBarTitle: String =
        "<green>#{index}</green><dark_gray>/</dark_gray><green>{total}</green><dark_gray> • </dark_gray>" +
            "<gold>P{priority}</gold><dark_gray> • </dark_gray><aqua>{region}</aqua>{flags}{summary}",
    @Help("BossBar summary when a region exposes no effective flags.")
    val inspectionBossBarNoFlags: String = "<gray>No active flags</gray>",
    @Help("Base summary fragment. Placeholders: {active}, {local}.")
    val inspectionBossBarSummaryBase: String = "<gold>{active} active</gold><dark_gray> • </dark_gray><aqua>{local} local</aqua>",
    @Help("Summary fragment appended when inherited flags exist. Placeholder: {count}.")
    val inspectionBossBarSummaryInherited: String = "<dark_gray> • </dark_gray><blue>{count} inherited</blue>",
    @Help("Summary fragment appended when overrides exist. Placeholder: {count}.")
    val inspectionBossBarSummaryOverrides: String = "<dark_gray> • </dark_gray><light_purple>{count} overrides</light_purple>",
    @Help("Prefix inserted before the boss bar flag detail list.")
    val inspectionBossBarDetailPrefix: String = "<dark_gray> • </dark_gray>",
    @Help("BossBar detail entry per flag. Placeholders: {flag}, {value}, {inherited}, {overrides}.")
    val inspectionBossBarDetail: String = "<gold>{flag}</gold><dark_gray>=</dark_gray>{value}{inherited}{overrides}",
    @Help("Separator between boss bar flag details.")
    val inspectionBossBarDetailSeparator: String = "<dark_gray>, </dark_gray>",
    @Help("Suffix appended when boss bar flag details are truncated.")
    val inspectionBossBarDetailMore: String = "<dark_gray> …</dark_gray>",
    @Help("Indicator appended to boss bar flag details when inherited.")
    val inspectionBossBarDetailInheritedIndicator: String = "<blue>↑</blue>",
    @Help("Indicator appended to boss bar flag details when overriding a parent value.")
    val inspectionBossBarDetailOverrideIndicator: String = "<light_purple>↻</light_purple>",
)
