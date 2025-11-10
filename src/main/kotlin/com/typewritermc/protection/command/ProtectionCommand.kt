package com.typewritermc.protection.command

import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.core.interaction.context
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.content.ContentContext
import com.typewritermc.engine.paper.content.ContentModeTrigger
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.utils.firstWalkableLocationBelow
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.protection.entry.artifact.GlobalRegionArtifactEntry
import com.typewritermc.protection.entry.artifact.RegionArtifactEntry
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.selection.SelectionService
import com.typewritermc.protection.selection.content.ProtectionRegionContentMode
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.selection.CylinderShape
import com.typewritermc.protection.selection.FlatPolygonShape
import com.typewritermc.protection.selection.GlobalRegionShape
import com.typewritermc.protection.selection.PolygonPrismShape
import com.typewritermc.protection.selection.RegionShape
import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.flags.RegionFlagRegistry
import com.typewritermc.protection.flags.displayName
import com.typewritermc.protection.flags.formatFlagValue
import com.typewritermc.protection.flags.resolveFlagResolutions
import com.typewritermc.protection.service.runtime.RegionInfoFormatter
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import com.typewritermc.protection.service.runtime.FlagInspectionService
import com.typewritermc.protection.settings.ProtectionMessageRenderer
import com.typewritermc.protection.settings.ProtectionMessages
import com.typewritermc.protection.settings.ProtectionSettingsRepository
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.koin.java.KoinJavaComponent
import java.util.UUID

private fun selectionService(): SelectionService = KoinJavaComponent.get(SelectionService::class.java)
private fun repository(): RegionRepository = KoinJavaComponent.get(RegionRepository::class.java)
private fun flagRegistry(): RegionFlagRegistry = KoinJavaComponent.get(RegionFlagRegistry::class.java)
private fun inspectionService(): FlagInspectionService = KoinJavaComponent.get(FlagInspectionService::class.java)

@TypewriterCommand
fun CommandTree.protectionCommand() = literal("protection") {
    withPermission("typewriter.protection")

    literal("list") {
        executePlayer { player ->
            val listMessages = ProtectionSettingsRepository.snapshot(player).messages.commands.list
            val regions = repository().all()
            if (regions.isEmpty()) {
                ProtectionMessageRenderer.render(listMessages.empty)?.let(sender::sendMessage)
                return@executePlayer
            }

            ProtectionMessageRenderer.render(
                listMessages.header,
                mapOf("count" to regions.size)
            )?.let(sender::sendMessage)

            regions.sortedByDescending { it.priority }
                .forEach { region ->
                    val name = region.definition.name.ifBlank { region.artifact?.name ?: region.id }
                    ProtectionMessageRenderer.render(
                        listMessages.entry,
                        mapOf(
                            "name" to name,
                            "priority" to region.priority
                        )
                    )?.let(sender::sendMessage)
                }
        }
    }

    literal("edit") {
        compatEntry<RegionDefinitionEntry>("manifest") { manifest ->
            executePlayer { player ->
                val definition = manifest()
                val artifact = definition.artifact.get()
                if (artifact == null) {
                    ProtectionMessageRenderer.render(
                        ProtectionSettingsRepository.snapshot(player).messages.selection.missingArtifact,
                        mapOf("definition" to definition.id)
                    )?.let(sender::sendMessage)
                    return@executePlayer
                }
                if (artifact is GlobalRegionArtifactEntry) {
                    selectionService().startSession(player, definition)
                    return@executePlayer
                }

                val contextData = ContentContext(mapOf("definitionId" to definition.id))
                ContentModeTrigger(
                    contextData,
                    ProtectionRegionContentMode(contextData, player)
                ).triggerFor(player, context())
            }
        }
    }

    literal("remove") {
        int("index", 1, 16) { idx ->
            executePlayer { player ->
                selectionService().removePoint(player, idx() - 1)
            }
        }
    }

    literal("import-we") {
        compatEntry<RegionDefinitionEntry>("manifest") { manifest ->
            executePlayer { player -> selectionService().importWorldEdit(player, manifest()) }
        }
    }

    literal("apply") {
        executePlayer { player -> selectionService().complete(player) }
    }

    literal("cancel") {
        executePlayer { player -> selectionService().cancel(player) }
    }

    literal("info") {
        compatEntry<RegionArtifactEntry>("region") { regionRef ->
            executePlayer { player ->
                val messages = ProtectionSettingsRepository.snapshot(player).messages
                val region = repository().findById(regionRef().id)
                if (region == null) {
                    ProtectionMessageRenderer.render(messages.commands.general.regionNotFound)?.let(sender::sendMessage)
                    return@executePlayer
                }
                RegionInfoFormatter.buildRegionInfoLines(region, messages).forEach { sender.sendMessage(it) }
            }
        }
    }

    literal("flags") {
        compatEntry<RegionArtifactEntry>("region") { regionRef ->
            executePlayer { player ->
                val repo = repository()
                val registry = flagRegistry()
                val messages = ProtectionSettingsRepository.snapshot(player).messages
                val region = repo.findById(regionRef().id)
                if (region == null) {
                    ProtectionMessageRenderer.render(messages.commands.general.regionNotFound)?.let(sender::sendMessage)
                    return@executePlayer
                }
                val lines = buildFlagAnalysis(region, repo, registry, messages)
                lines.forEach { sender.sendMessage(it) }
            }
        }
    }

    literal("teleport") {
        compatEntry<RegionArtifactEntry>("region") { regionRef ->
            executePlayer { player ->
                val commands = ProtectionSettingsRepository.snapshot(player).messages.commands
                val region = repository().findById(regionRef().id)
                if (region == null) {
                    ProtectionMessageRenderer.render(commands.general.regionNotFound)?.let(sender::sendMessage)
                    return@executePlayer
                }

                val regionName = displayName(region)
                when (val target = resolveTeleportTarget(region)) {
                    is TeleportTargetResult.Success -> {
                        val destination = target.location.clone().apply {
                            yaw = player.location.yaw
                            pitch = player.location.pitch
                        }
                        val teleported = player.teleport(destination)
                        if (!teleported) {
                            ProtectionMessageRenderer.render(
                                commands.teleport.unavailable,
                                mapOf("region" to regionName)
                            )?.let(sender::sendMessage)
                            return@executePlayer
                        }

                        ProtectionMessageRenderer.render(
                            commands.teleport.success,
                            mapOf("region" to regionName)
                        )?.let(sender::sendMessage)
                    }

                    is TeleportTargetResult.WorldMissing -> {
                        ProtectionMessageRenderer.render(
                            commands.teleport.worldMissing,
                            mapOf("region" to regionName, "world" to target.world)
                        )?.let(sender::sendMessage)
                    }

                    TeleportTargetResult.Unavailable -> {
                        ProtectionMessageRenderer.render(
                            commands.teleport.unavailable,
                            mapOf("region" to regionName)
                        )?.let(sender::sendMessage)
                    }
                }
            }
        }
    }

    literal("inspect") {
        executePlayer { player ->
            val messages = ProtectionSettingsRepository.snapshot(player).messages.commands.inspect
            val enabled = inspectionService().toggle(player)
            val template = if (enabled) messages.enabled else messages.disabled
            ProtectionMessageRenderer.render(template)?.let(sender::sendMessage)
        }
    }
}

private fun buildFlagAnalysis(
    region: RegionModel,
    repository: RegionRepository,
    registry: RegionFlagRegistry,
    messages: ProtectionMessages,
): List<Component> {
    val flags = messages.commands.flags
    val lines = mutableListOf<Component>()
    ProtectionMessageRenderer.render(
        flags.header,
        mapOf("region" to displayName(region))
    )?.let(lines::add)

    val resolutions = resolveFlagResolutions(region, repository, registry)
    if (resolutions.isEmpty()) {
        ProtectionMessageRenderer.render(flags.empty)?.let(lines::add)
        return lines
    }

    val effective = resolutions.filter { it.isEffective }
    ProtectionMessageRenderer.render(flags.resolvedHeader)?.let(lines::add)
    if (effective.isEmpty()) {
        ProtectionMessageRenderer.render(flags.empty)?.let(lines::add)
    } else {
        val inheritedMarker = ProtectionMessageRenderer.render(flags.resolvedInheritedMarker)
        val overrideMarker = ProtectionMessageRenderer.render(flags.resolvedOverrideMarker)
        effective.forEach { resolution ->
            val effectiveSource = resolution.effective ?: return@forEach
            val markers = mutableMapOf<String, Any?>(
                "flag" to resolution.key.id,
                "value" to formatFlagValue(effectiveSource.binding.value),
                "source" to displayName(effectiveSource.region)
            )
            markers["inherited_marker"] = if (resolution.isInherited) inheritedMarker else ""
            markers["override_marker"] = if (resolution.overridesParent) overrideMarker else ""
            ProtectionMessageRenderer.render(flags.resolvedEntry, markers)?.let(lines::add)
        }
    }

    val inherited = effective.filter { it.isInherited }
    if (inherited.isNotEmpty()) {
        ProtectionMessageRenderer.render(flags.inheritedHeader)?.let(lines::add)
        inherited.forEach { resolution ->
            val effectiveSource = resolution.effective ?: return@forEach
            ProtectionMessageRenderer.render(
                flags.inheritedEntry,
                mapOf(
                    "flag" to resolution.key.id,
                    "source" to displayName(effectiveSource.region)
                )
            )?.let(lines::add)
        }
    }

    val overrides = effective.filter { it.overridesParent }
    if (overrides.isNotEmpty()) {
        ProtectionMessageRenderer.render(flags.overridesHeader)?.let(lines::add)
        overrides.forEach { resolution ->
            val effectiveSource = resolution.effective ?: return@forEach
            val parent = resolution.parentSource
            val template = if (parent != null) flags.overridesEntryWithParent else flags.overridesEntryWithoutParent
            val placeholders = mutableMapOf<String, Any?>(
                "flag" to resolution.key.id,
                "value" to formatFlagValue(effectiveSource.binding.value)
            )
            if (parent != null) {
                placeholders["parent_value"] = formatFlagValue(parent.binding.value)
                placeholders["parent_region"] = displayName(parent.region)
            }
            ProtectionMessageRenderer.render(template, placeholders)?.let(lines::add)
        }
    }

    val blocked = resolutions.filter { it.blockedByInheritance }
    if (blocked.isNotEmpty()) {
        ProtectionMessageRenderer.render(flags.blockedHeader)?.let(lines::add)
        blocked.forEach { resolution ->
            val sourceRegion = resolution.history.lastOrNull()?.region ?: region
            val inheritance = resolution.definition?.inheritance?.name?.lowercase() ?: "always"
            ProtectionMessageRenderer.render(
                flags.blockedEntry,
                mapOf(
                    "flag" to resolution.key.id,
                    "source" to displayName(sourceRegion),
                    "inheritance" to inheritance
                )
            )?.let(lines::add)
        }
    }

    ProtectionMessageRenderer.render(flags.legend)?.let(lines::add)
    return lines
}

private fun resolveTeleportTarget(region: RegionModel): TeleportTargetResult {
    val shape = region.shape
    if (shape is GlobalRegionShape) {
        val worldId = shape.worlds.firstOrNull() ?: return TeleportTargetResult.Unavailable
        val world = Bukkit.getWorld(worldId) ?: run {
            val uuid = runCatching { UUID.fromString(worldId) }.getOrNull()
            uuid?.let(Bukkit::getWorld)
        }
        return if (world != null) {
            TeleportTargetResult.Success(world.spawnLocation)
        } else {
            TeleportTargetResult.WorldMissing(worldId)
        }
    }

    val center = computeCenterPosition(shape) ?: return TeleportTargetResult.Unavailable
    val safe = center.firstWalkableLocationBelow() ?: center
    val location = try {
        safe.toBukkitLocation()
    } catch (error: IllegalArgumentException) {
        return TeleportTargetResult.WorldMissing(safe.world.identifier)
    }
    return TeleportTargetResult.Success(location)
}

private fun computeCenterPosition(shape: RegionShape): Position? = when (shape) {
    is CuboidShape -> midpoint(shape.min(), shape.max())
    is PolygonPrismShape -> if (shape.vertices.isEmpty()) null else midpoint(shape.min(), shape.max())
    is FlatPolygonShape -> if (shape.vertices.isEmpty()) null else midpoint(shape.min(), shape.max())
    is CylinderShape -> {
        val center = shape.center
        Position(center.world, center.x, (shape.minY + shape.maxY) / 2.0, center.z)
    }
    is GlobalRegionShape -> null
}

private fun midpoint(min: Position, max: Position): Position {
    return Position(
        min.world,
        (min.x + max.x) / 2.0,
        (min.y + max.y) / 2.0,
        (min.z + max.z) / 2.0,
    )
}

private sealed interface TeleportTargetResult {
    data class Success(val location: Location) : TeleportTargetResult
    data class WorldMissing(val world: String) : TeleportTargetResult
    object Unavailable : TeleportTargetResult
}
