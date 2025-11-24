package com.typewritermc.protection.settings

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Configurable templates used across the protection tooling. All strings accept MiniMessage syntax.
 */
data class ProtectionMessages(
    val selection: SelectionMessages = SelectionMessages(),
    val content: ContentMessages = ContentMessages(),
    val modes: ModeMessages = ModeMessages(),
    val commands: CommandMessages = CommandMessages(),
) {
    data class SelectionMessages(
        val missingArtifact: String = "<red>Region definition <definition> is not linked to any artifact.</red>",
        val globalApplied: String = "<green>Applied global region for <label>.</green>",
        val globalCaptureDenied: String = "<red>Global artifacts cannot be captured.</red>",
        val activation: String = "<green>Selection mode enabled for <label>. Use the toolbar to add or remove points, then validate from the menu.</green>",
        val noSession: String = "<red>You do not have an active selection session.</red>",
        val pointAdded: String = "<aqua>Point <index> added: <x> <y> <z></aqua>",
        val pointUpdated: String = "<aqua>Point <index> updated: <x> <y> <z></aqua>",
        val pointAddFailed: String = "<red>Cannot add point <index>: index is too high.</red>",
        val pointRemoved: String = "<gray>Removed point <index>: <x> <y> <z></gray>",
        val lastPointRemoved: String = "<gray>Removed last point: <x> <y> <z></gray>",
        val noPointToRemove: String = "<yellow>There is no point to remove.</yellow>",
        val pointMissing: String = "<yellow>No point <index> recorded.</yellow>",
        val pointInfo: String = "<light_purple>Point <index>: <x>, <y>, <z></light_purple>",
        val worldMismatch: String = "<red>All points must belong to the same world (<world>).</red>",
        val sessionCancelled: String = "<gray>Selection cancelled.</gray>",
        val selectionApplied: String = "<green>Selection applied.</green>",
        val notEnoughPoints: String = "<yellow>Add at least <required> points before applying.</yellow>",
        val mixedWorlds: String = "<red>All points must be in the same world (<worlds>).</red>",
        val invalidGeometry: String = "<red>The current selection cannot be converted into a region.</red>",
        val selectionSynced: String = "<yellow>Selection synchronised (<points> point<plural_suffix>).</yellow>",
        val worldEditUnavailableForGlobal: String = "<red>Cannot import a WorldEdit selection for a global region.</red>",
        val worldEditMissingAdapter: String = "<red>No compatible WorldEdit adapter was detected.</red>",
        val worldEditNotFound: String = "<red>No WorldEdit selection detected.</red>",
        val worldEditSessionUnavailable: String = "<red>Unable to open the capture session for this artifact.</red>",
        val worldEditImported: String = "<yellow>Imported WorldEdit selection (<points> point<plural_suffix>).</yellow>",
        val ensureSameWorld: String = "<red>All points must belong to the same world (<world>).</red>",
    )

    data class ContentMessages(
        val addPointName: String = "<gold><bold>Add point</bold></gold>",
        val addPointLore: List<String> = listOf("<gray>Click to capture the targeted block.</gray>"),
        val removeLastName: String = "<red><bold>Remove last</bold></red>",
        val removeLastLore: List<String> = listOf("<gray>Click to remove the last point.</gray>"),
        val modeHint: String = "<gray>Click to change the selection mode.</gray>",
        val applyName: String = "<green><bold>Apply</bold></green>",
        val applyLore: List<String> = listOf("<gray>Click to save the current selection.</gray>"),
        val cancelName: String = "<red><bold>Cancel</bold></red>",
        val cancelLore: List<String> = listOf("<gray>Click to cancel the capture.</gray>"),
    )

    data class ModeMessages(
        val cuboid: ModeEntry = ModeEntry(
            name = "<gold>Cuboid mode</gold>",
            description = "<gray>Two points define an axis-aligned volume.</gray>",
        ),
        val polygon: ModeEntry = ModeEntry(
            name = "<aqua>Polygon mode</aqua>",
            description = "<gray>Three or more points define a polygonal prism.</gray>",
        ),
    ) {
        data class ModeEntry(
            val name: String = "",
            val description: String = "",
        )
    }

    data class CommandMessages(
        val general: GeneralMessages = GeneralMessages(),
        val list: ListMessages = ListMessages(),
        val info: InfoMessages = InfoMessages(),
        val flags: FlagsMessages = FlagsMessages(),
        val inspect: InspectMessages = InspectMessages(),
        val teleport: TeleportMessages = TeleportMessages(),
    ) {
        data class GeneralMessages(
            val regionNotFound: String = "<red>Region not found.</red>",
            val definitionNotFound: String = "Could not find region definition '<definition>'.",
            val artifactNotFound: String = "Could not find artifact '<artifact>'.",
        )

        data class ListMessages(
            val empty: String = "<yellow>No protected regions are currently configured.</yellow>",
            val header: String = "<aqua>Protected regions (<count>):</aqua>",
            val entry: String = "<gray>- <name></gray><dark_gray> (prio <priority>)</dark_gray>",
        )

        data class InfoMessages(
            val header: String = "<aqua><name></aqua>",
            val priority: String = "<gray>Priority: <priority></gray>",
            val owners: String = "<gray>Owners: <owners></gray>",
            val members: String = "<gray>Members: <members></gray>",
            val groups: String = "<gray>Groups: <groups></gray>",
            val mode: String = "<gray>Selection mode: <mode></gray>",
            val shape: String = "<gray>Shape: <shape></gray>",
            val bounds: String = "<dark_gray>Bounds: <min> -> <max></dark_gray>",
            val cuboidDimensions: String = "<gray>Dimensions: <x> x <y> x <z> blocks</gray>",
            val prismHeight: String = "<gray>Height: <min_y> -> <max_y> (<height> blocks)</gray>",
            val prismVertices: String = "<dark_aqua>Vertices (<count>): <vertices></dark_aqua>",
            val flatAltitude: String = "<gray>Altitude: <y></gray>",
            val cylinderCenter: String = "<gray>Center: <center_x>, <center_z></gray>",
            val cylinderRadius: String = "<gray>Radius: <radius> blocks | Height: <min_y> -> <max_y></gray>",
            val globalWorlds: String = "<gray>Worlds: <worlds></gray>",
            val globalAltitude: String = "<gray>Altitude: <min_y> -> <max_y></gray>",
            val nodes: String = "<dark_aqua>Points (<count> point<plural_suffix>): <points></dark_aqua>",
            val flags: String = "<gray>Flags: <flags></gray>",
            val noFlags: String = "<gray>Flags: none</gray>",
            val shapeNames: ShapeNames = ShapeNames(),
        ) {
            data class ShapeNames(
                val cuboid: String = "Cuboid",
                val polygonPrism: String = "Polygonal prism",
                val cylinder: String = "Cylinder",
                val flatPolygon: String = "Flat polygon",
                val global: String = "Entire world",
            )
        }

        data class FlagsMessages(
            val header: String = "<aqua>Flag analysis for <region></aqua>",
            val empty: String = "<gray>No flag defined in the hierarchy.</gray>",
            val resolvedHeader: String = "<gold>Resolved values:</gold>",
            val resolvedEntry: String = "<dark_gray>• </dark_gray><aqua><flag></aqua><dark_gray> = </dark_gray><value><dark_gray> (<source>)</dark_gray><inherited_marker><override_marker>",
            val resolvedInheritedMarker: String = "<blue> ↑</blue>",
            val resolvedOverrideMarker: String = "<light_purple> ↻</light_purple>",
            val inheritedHeader: String = "<blue>Inheritance:</blue>",
            val inheritedEntry: String = "<dark_gray>• </dark_gray><aqua><flag></aqua><gray> applied from </gray><blue><source></blue>",
            val overridesHeader: String = "<light_purple>Local overrides:</light_purple>",
            val overridesEntryWithParent: String = "<dark_gray>• </dark_gray><aqua><flag></aqua><gray> replaces </gray><parent_value><gray> from <parent_region></gray><dark_gray> with </dark_gray><value>",
            val overridesEntryWithoutParent: String = "<dark_gray>• </dark_gray><aqua><flag></aqua><gray> overrides an inherited value with </gray><value>",
            val blockedHeader: String = "<red>Ignored (inheritance disabled):</red>",
            val blockedEntry: String = "<dark_gray>• </dark_gray><red><flag></red><gray> defined in </gray><red><source></red><dark_gray> but ignored (inheritance <inheritance>).</dark_gray>",
            val legend: String = "<dark_gray>Legend: ↑ inherited, ↻ local override</dark_gray>",
        )

        data class InspectMessages(
            val enabled: String = "<green>Inspection mode enabled: ActionBar and BossBar now list active flags.</green>",
            val disabled: String = "<yellow>Inspection mode disabled.</yellow>",
        )

        data class TeleportMessages(
            val success: String = "<green>Teleported to <region>.</green>",
            val unavailable: String = "<red>Unable to determine a safe location for <region>.</red>",
            val worldMissing: String = "<red>Cannot access world <world> for <region>.</red>",
        )
    }
}

/**
 * Utility responsible for rendering configured MiniMessage templates safely.
 */
object ProtectionMessageRenderer {
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val plainSerializer = PlainTextComponentSerializer.plainText()
    private val logger = LoggerFactory.getLogger("ProtectionMessageRenderer")
    private val loggedFailures = ConcurrentHashMap.newKeySet<String>()

    fun render(template: String?, placeholders: Map<String, Any?> = emptyMap()): Component? {
        val raw = template?.takeUnless { it.isBlank() } ?: return null
        val resolvers = placeholders.entries
            .mapNotNull { (key, value) ->
                when (value) {
                    null -> null
                    is Component -> Placeholder.component(key, value)
                    else -> Placeholder.unparsed(key, value.toString())
                }
            }
        val resolver = if (resolvers.isEmpty()) TagResolver.empty() else TagResolver.resolver(resolvers)
        return runCatching { miniMessage.deserialize(raw, resolver) }
            .getOrElse { error ->
                if (loggedFailures.add(raw)) {
                    logger.warn("Failed to parse protection message '{}': {}", raw, error.message)
                }
                Component.text(raw)
            }
    }

    fun renderPlain(template: String?, placeholders: Map<String, Any?> = emptyMap()): String? {
        return render(template, placeholders)?.let { plainSerializer.serialize(it) }
    }
}

