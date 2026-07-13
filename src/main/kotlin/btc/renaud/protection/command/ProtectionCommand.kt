package btc.renaud.protection.command

import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.content.ContentContext
import com.typewritermc.engine.paper.content.ContentModeTrigger
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.firstWalkableLocationBelow
import com.typewritermc.engine.paper.utils.toBukkitLocation
import btc.renaud.protection.entry.artifact.GlobalRegionArtifactEntry
import btc.renaud.protection.entry.artifact.RegionArtifactEntry
import btc.renaud.protection.entry.region.RegionDefinitionEntry
import btc.renaud.protection.selection.SelectionService
import btc.renaud.protection.selection.content.ProtectionRegionContentMode
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.flags.RegionFlagRegistry
import btc.renaud.protection.flags.displayName
import btc.renaud.protection.flags.formatFlagValue
import btc.renaud.protection.flags.parseFlagValue
import btc.renaud.protection.flags.resolveFlagResolutions
import btc.renaud.protection.service.runtime.RegionInfoFormatter
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import btc.renaud.protection.service.runtime.FlagInspectionService
import btc.renaud.protection.command.WorldGuardMigrationService
import btc.renaud.protection.settings.ProtectionMessageRenderer
import btc.renaud.protection.settings.ProtectionSettingsRepository
import btc.renaud.protection.settings.ProtectionMessages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.koin.java.KoinJavaComponent
import com.typewritermc.core.utils.point.Position
import btc.renaud.protection.selection.CuboidShape
import btc.renaud.protection.selection.CylinderShape
import btc.renaud.protection.selection.FlatPolygonShape
import btc.renaud.protection.selection.GlobalRegionShape
import btc.renaud.protection.selection.PolygonPrismShape
import btc.renaud.protection.selection.RegionShape
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

private val legacy = LegacyComponentSerializer.legacySection()

/**
 * Lazily-resolved dependencies for command handlers.
 * Encapsulates Koin lookups in a single place.
 */
private object CommandDeps {
    val selectionService: SelectionService by lazy { KoinJavaComponent.get(SelectionService::class.java) }
    val repository: RegionRepository by lazy { KoinJavaComponent.get(RegionRepository::class.java) }
    val flagRegistry: RegionFlagRegistry by lazy { KoinJavaComponent.get(RegionFlagRegistry::class.java) }
    val inspectionService: FlagInspectionService by lazy { KoinJavaComponent.get(FlagInspectionService::class.java) }
    val regionInfoFormatter: RegionInfoFormatter by lazy { KoinJavaComponent.get(RegionInfoFormatter::class.java) }
    val settingsRepository: ProtectionSettingsRepository by lazy { KoinJavaComponent.get(ProtectionSettingsRepository::class.java) }
    val worldGuardMigration: btc.renaud.protection.command.WorldGuardMigrationService by lazy { KoinJavaComponent.get(btc.renaud.protection.command.WorldGuardMigrationService::class.java) }
}

@TypewriterCommand
fun CommandTree.protectionCommand() = literal("protection") {
    withPermission("typewriter.protection")

    literal("list") {
        executePlayer { player ->
            val listMessages = CommandDeps.settingsRepository.snapshot(player).messages.commands.list
            val regions = CommandDeps.repository.all()
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
                        CommandDeps.settingsRepository.snapshot(player).messages.selection.missingArtifact,
                        mapOf("definition" to definition.id)
                    )?.let(sender::sendMessage)
                    return@executePlayer
                }
                if (artifact is GlobalRegionArtifactEntry) {
                    CommandDeps.selectionService.startSession(player, definition)
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
                CommandDeps.selectionService.removePoint(player, idx() - 1)
            }
        }
    }

    literal("import-we") {
        compatEntry<RegionDefinitionEntry>("manifest") { manifest ->
            executePlayer { player -> CommandDeps.selectionService.importWorldEdit(player, manifest()) }
        }
    }

    literal("apply") {
        executePlayer { player -> CommandDeps.selectionService.complete(player) }
    }

    literal("cancel") {
        executePlayer { player -> CommandDeps.selectionService.cancel(player) }
    }

    literal("info") {
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            executePlayer { player ->
                val messages = CommandDeps.settingsRepository.snapshot(player).messages
                val region = CommandDeps.repository.findById(regionRef().id)
                if (region == null) {
                    ProtectionMessageRenderer.render(messages.commands.general.regionNotFound)?.let(sender::sendMessage)
                    return@executePlayer
                }
                CommandDeps.regionInfoFormatter.buildRegionInfoLines(region, messages).forEach { sender.sendMessage(it) }
            }
        }
    }

    literal("flags") {
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            executePlayer { player ->
                val repo = CommandDeps.repository
                val registry = CommandDeps.flagRegistry
                val messages = CommandDeps.settingsRepository.snapshot(player).messages
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

    literal("setflag") {
        withPermission("typewriter.protection.admin")
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            string("flag") { flagArg ->
                greedyString("value") { valueArg ->
                    executePlayer { player ->
                        val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands
                        val regionId = regionRef().id
                        val flagName = flagArg()
                        val rawValue = valueArg()

                        val flagKey = btc.renaud.protection.flags.RegionFlagKey.entries
                            .firstOrNull { it.id.equals(flagName, ignoreCase = true) }
                        if (flagKey == null) {
                            ProtectionMessageRenderer.render(
                                messages.general.flagNotFound,
                                mapOf("flag" to flagName)
                            )?.let(sender::sendMessage)
                            return@executePlayer
                        }

                        val parsed = parseFlagValue(flagKey, rawValue)
                        CommandDeps.repository.setFlag(regionId, flagKey, rawValue)
                        ProtectionMessageRenderer.render(
                            messages.general.flagSet,
                            mapOf(
                                "flag" to flagKey.id,
                                "value" to legacy.serialize(formatFlagValue(parsed)),
                                "region" to regionId
                            )
                        )?.let(sender::sendMessage)
                    }
                }
            }
        }
    }

    literal("clearflag") {
        withPermission("typewriter.protection.admin")
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            string("flag") { flagArg ->
                executePlayer { player ->
                    val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands
                    val regionId = regionRef().id
                    val flagName = flagArg()

                    val flagKey = btc.renaud.protection.flags.RegionFlagKey.entries
                        .firstOrNull { it.id.equals(flagName, ignoreCase = true) }
                    if (flagKey == null) {
                        ProtectionMessageRenderer.render(
                            messages.general.flagNotFound,
                            mapOf("flag" to flagName)
                        )?.let(sender::sendMessage)
                        return@executePlayer
                    }

                    val definition = regionRef()
                    val defaultFlags = definition.flags.filter { it.key != flagKey }
                    // Rebuild flags without the cleared one — we use setFlag with default
                    val default = btc.renaud.protection.flags.parseFlagValue(flagKey, "")
                    CommandDeps.repository.setFlag(regionId, flagKey, "")
                    ProtectionMessageRenderer.render(
                        messages.general.flagCleared,
                        mapOf("flag" to flagKey.id, "region" to regionId)
                    )?.let(sender::sendMessage)
                }
            }
        }
    }

    literal("addowner") {
        withPermission("typewriter.protection.admin")
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            string("player") { playerArg ->
                executePlayer { player ->
                    val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands
                    val regionId = regionRef().id
                    val targetName = playerArg()

                    // Use plugin.server.offlinePlayers for Folia compatibility
                    // (Bukkit.getOfflinePlayer(String) is deprecated and blocks the main thread)
                    val target = plugin.server.offlinePlayers.firstOrNull {
                        it.name.equals(targetName, ignoreCase = true)
                    }
                    val uuid = target?.uniqueId?.toString() ?: targetName
                    CommandDeps.repository.addOwner(regionId, uuid)
                    ProtectionMessageRenderer.render(
                        messages.general.ownerAdded,
                        mapOf("player" to targetName, "region" to regionId)
                    )?.let(sender::sendMessage)
                }
            }
        }
    }

    literal("removeowner") {
        withPermission("typewriter.protection.admin")
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            string("player") { playerArg ->
                executePlayer { player ->
                    val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands
                    val regionId = regionRef().id
                    val targetName = playerArg()

                    val target = plugin.server.offlinePlayers.firstOrNull {
                        it.name.equals(targetName, ignoreCase = true)
                    }
                    val uuid = target?.uniqueId?.toString() ?: targetName
                    CommandDeps.repository.removeOwner(regionId, uuid)
                    ProtectionMessageRenderer.render(
                        messages.general.ownerRemoved,
                        mapOf("player" to targetName, "region" to regionId)
                    )?.let(sender::sendMessage)
                }
            }
        }
    }

    literal("addmember") {
        withPermission("typewriter.protection.admin")
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            string("player") { playerArg ->
                executePlayer { player ->
                    val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands
                    val regionId = regionRef().id
                    val targetName = playerArg()

                    val target = plugin.server.offlinePlayers.firstOrNull {
                        it.name.equals(targetName, ignoreCase = true)
                    }
                    val uuid = target?.uniqueId?.toString() ?: targetName
                    CommandDeps.repository.addMember(regionId, uuid)
                    ProtectionMessageRenderer.render(
                        messages.general.memberAdded,
                        mapOf("player" to targetName, "region" to regionId)
                    )?.let(sender::sendMessage)
                }
            }
        }
    }

    literal("removemember") {
        withPermission("typewriter.protection.admin")
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            string("player") { playerArg ->
                executePlayer { player ->
                    val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands
                    val regionId = regionRef().id
                    val targetName = playerArg()

                    val target = plugin.server.offlinePlayers.firstOrNull {
                        it.name.equals(targetName, ignoreCase = true)
                    }
                    val uuid = target?.uniqueId?.toString() ?: targetName
                    CommandDeps.repository.removeMember(regionId, uuid)
                    ProtectionMessageRenderer.render(
                        messages.general.memberRemoved,
                        mapOf("player" to targetName, "region" to regionId)
                    )?.let(sender::sendMessage)
                }
            }
        }
    }

    literal("teleport") {
        compatEntry<RegionDefinitionEntry>("region") { regionRef ->
            executePlayer { player ->
                val commands = CommandDeps.settingsRepository.snapshot(player).messages.commands
                val region = CommandDeps.repository.findById(regionRef().id)
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
                        player.teleportAsync(destination).thenAccept { teleported ->
                            if (!teleported) {
                                ProtectionMessageRenderer.render(
                                    commands.teleport.unavailable,
                                    mapOf("region" to regionName)
                                )?.let(player::sendMessage)
                            } else {
                                ProtectionMessageRenderer.render(
                                    commands.teleport.success,
                                    mapOf("region" to regionName)
                                )?.let(player::sendMessage)
                            }
                        }
                        return@executePlayer
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
            val messages = CommandDeps.settingsRepository.snapshot(player).messages.commands.inspect
            val enabled = CommandDeps.inspectionService.toggle(player)
            val template = if (enabled) messages.enabled else messages.disabled
            ProtectionMessageRenderer.render(template)?.let(sender::sendMessage)
        }
    }

    literal("wand") {
        executePlayer { player ->
            val wandKey = NamespacedKey("typewriter", "protection_wand")
            val wand = ItemStack(Material.STICK).apply {
                itemMeta = itemMeta.apply {
                    displayName(Component.text("Protection Wand"))
                    persistentDataContainer.set(wandKey, PersistentDataType.BOOLEAN, true)
                }
            }
            player.inventory.addItem(wand).forEach { (_, excess) ->
                player.world.dropItem(player.location, excess)
            }
            sender.sendMessage(Component.text("You received the protection selection wand."))
        }
    }

    literal("reload") {
        withPermission("typewriter.protection.admin")
        executePlayer { player ->
            CommandDeps.repository.reload()
            sender.sendMessage(Component.text("Protection regions reloaded."))
        }
    }

    literal("migrate-from-worldguard") {
        withPermission("typewriter.protection.admin")
        executePlayer { player ->
            val migrationService = CommandDeps.worldGuardMigration
            sender.sendMessage(Component.text("Starting WorldGuard migration..."))
            val result = migrationService.migrateFromWorldGuard()
            if (result.errors.isEmpty()) {
                sender.sendMessage(Component.text("Migration complete: ${result.regionsMigrated}/${result.regionsFound} regions migrated."))
            } else {
                sender.sendMessage(Component.text("Migration complete with ${result.errors.size} errors: ${result.regionsMigrated}/${result.regionsFound} regions migrated."))
                result.errors.take(5).forEach { error: String ->
                    sender.sendMessage(Component.text("  - $error"))
                }
            }
            CommandDeps.repository.reload()
        }
    }

    literal("delete") {
        withPermission("typewriter.protection.admin")
        string("regionId") { regionIdArg ->
            executePlayer { player ->
                val regionId = regionIdArg()
                val repo = CommandDeps.repository
                val region = repo.findById(regionId)
                if (region == null) {
                    sender.sendMessage(Component.text("<red>Region not found.</red>"))
                    return@executePlayer
                }
                val uuid = player.uniqueId.toString()
                if (!region.owners.contains(uuid) && !player.hasPermission("typewriter.protection.admin")) {
                    sender.sendMessage(Component.text("<red>You are not an owner of this region.</red>"))
                    return@executePlayer
                }
                repo.removeRegion(regionId)
                sender.sendMessage(Component.text("<green>Region '$regionId' deleted.</green>"))
            }
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
