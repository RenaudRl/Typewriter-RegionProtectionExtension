package com.typewritermc.protection.selection

import com.github.retrooper.packetevents.util.Vector3f
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import com.typewritermc.engine.paper.content.components.NodeDisplayBuilder
import com.typewritermc.engine.paper.content.components.NodesComponent
import com.typewritermc.protection.entry.artifact.GlobalRegionArtifactEntry
import com.typewritermc.protection.entry.artifact.RegionArtifactEntry
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.service.storage.RegionArtifactStorage
import com.typewritermc.protection.selection.SelectionGeometry.verticalRange
import com.typewritermc.protection.service.storage.RegionRepository
import com.typewritermc.protection.selection.GlobalRegionShape
import com.typewritermc.protection.settings.ProtectionMessageRenderer
import com.typewritermc.protection.settings.ProtectionMessages
import com.typewritermc.protection.settings.ProtectionSettingsRepository
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class SelectionService {
    private val plugin: Plugin = Bukkit.getPluginManager().getPlugin("TypeWriter")
        ?: error("TypeWriter plugin is required")
    private val repository: RegionRepository = KoinJavaComponent.get(RegionRepository::class.java)
    private val worldEditAdapter = WorldEditAdapter()
    private val sessions = ConcurrentHashMap<UUID, SelectionSession>()

    fun startSession(player: Player, definition: RegionDefinitionEntry) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        val artifact = definition.artifact.get()
        if (artifact == null) {
            sendMessage(player, messages.selection.missingArtifact, mapOf("definition" to definition.id))
            return
        }
        RegionArtifactStorage.initialize(artifact)
        if (artifact is GlobalRegionArtifactEntry) {
            val shape = (RegionArtifactStorage.load(artifact)?.shape as? GlobalRegionShape)
                ?: artifact.toRegionShape()
            repository.updateRegion(definition, shape, emptyList(), SelectionMode.CUBOID, player)
            val label = definition.name.ifBlank { artifact.name.ifBlank { definition.id } }
            sendMessage(player, messages.selection.globalApplied, mapOf("label" to label))
            return
        }

        val label = definition.name.ifBlank { artifact.name.ifBlank { definition.id } }
        reuseOrCreateSession(player, artifact, definition, label, notify = true, messages = messages)
    }

    fun startArtifactSession(player: Player, artifact: RegionArtifactEntry, label: String = artifactLabel(artifact)) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        if (artifact is GlobalRegionArtifactEntry) {
            sendMessage(player, messages.selection.globalCaptureDenied, mapOf("artifact" to artifact.id))
            return
        }
        RegionArtifactStorage.initialize(artifact)
        reuseOrCreateSession(player, artifact, definition = null, label = label, notify = true, messages = messages)
    }

    fun setPoint(player: Player, location: Location, index: Int) {
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, ProtectionSettingsRepository.snapshot(player).messages.selection.noSession)
            return
        }
        session.setPoint(index, location.toTWPosition())
    }

    fun appendPoint(player: Player, location: Location = player.location) {
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, ProtectionSettingsRepository.snapshot(player).messages.selection.noSession)
            return
        }
        session.appendPoint(location.toTWPosition())
    }

    fun appendPointFromTarget(player: Player): Boolean {
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, ProtectionSettingsRepository.snapshot(player).messages.selection.noSession)
            return false
        }
        val target = player.getTargetBlockExact(64)?.location ?: player.location
        session.appendPoint(target.toTWPosition())
        return true
    }

    fun cycleMode(player: Player) {
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, ProtectionSettingsRepository.snapshot(player).messages.selection.noSession)
            return
        }
        session.cycleMode()
    }

    fun snapshot(player: Player): SelectionSnapshot? = sessions[player.uniqueId]?.snapshot()

    fun importWorldEdit(player: Player, definition: RegionDefinitionEntry) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        val artifact = definition.artifact.get()
        if (artifact == null) {
            sendMessage(player, messages.selection.missingArtifact, mapOf("definition" to definition.id))
            return
        }
        if (artifact is GlobalRegionArtifactEntry) {
            sendMessage(player, messages.selection.worldEditUnavailableForGlobal)
            return
        }
        importWorldEdit(player, artifact) {
            reuseOrCreateSession(
                player,
                artifact,
                definition,
                definition.name.ifBlank { artifact.name.ifBlank { definition.id } },
                notify = false,
                messages = messages
            )
        }
    }

    fun importWorldEdit(player: Player, artifact: RegionArtifactEntry) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        if (artifact is GlobalRegionArtifactEntry) {
            sendMessage(player, messages.selection.worldEditUnavailableForGlobal)
            return
        }
        importWorldEdit(player, artifact) {
            reuseOrCreateSession(
                player,
                artifact,
                definition = null,
                label = artifactLabel(artifact),
                notify = false,
                messages = messages
            )
        }
    }

    private fun importWorldEdit(player: Player, artifact: RegionArtifactEntry, sessionProvider: () -> SelectionSession?) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        if (!worldEditAdapter.isAvailable) {
            sendMessage(player, messages.selection.worldEditMissingAdapter)
            return
        }
        val geometry = worldEditAdapter.captureSelection(player)
        if (geometry == null) {
            sendMessage(player, messages.selection.worldEditNotFound)
            return
        }
        val session = sessionProvider() ?: run {
            sendMessage(player, messages.selection.worldEditSessionUnavailable)
            return
        }
        session.applyGeometry(geometry)
        val suffix = if (session.pointCount > 1) "s" else ""
        sendMessage(
            player,
            messages.selection.worldEditImported,
            mapOf(
                "points" to session.pointCount.toString(),
                "plural_suffix" to suffix
            )
        )
    }

    fun complete(player: Player) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, messages.selection.noSession)
            return
        }
        when (val result = session.commit()) {
            is SelectionCommitResult.Success -> {
                val definition = session.definition
                if (definition != null) {
                    repository.updateRegion(definition, result.shape, result.nodes, result.mode, player)
                } else {
                    repository.updateArtifact(session.artifact, result.shape, result.nodes, result.mode, player)
                }
                sendMessage(player, messages.selection.selectionApplied)
                sessions.remove(player.uniqueId)?.close()
            }
            SelectionCommitResult.NotEnoughPoints -> sendMessage(
                player,
                messages.selection.notEnoughPoints,
                mapOf("required" to session.minimumPoints())
            )
            is SelectionCommitResult.MixedWorlds -> sendMessage(
                player,
                messages.selection.mixedWorlds,
                mapOf("worlds" to result.worlds.joinToString())
            )
            SelectionCommitResult.InvalidGeometry -> sendMessage(player, messages.selection.invalidGeometry)
        }
    }

    fun removePoint(player: Player, index: Int) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, messages.selection.noSession)
            return
        }
        if (!session.removePoint(index)) {
            sendMessage(
                player,
                messages.selection.pointMissing,
                mapOf("index" to (index + 1).toString())
            )
        }
    }

    fun removeLastPoint(player: Player) {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        val session = sessions[player.uniqueId]
        if (session == null) {
            sendMessage(player, messages.selection.noSession)
            return
        }
        if (!session.removeLastPoint()) {
            sendMessage(player, messages.selection.noPointToRemove)
        }
    }

    fun cancel(player: Player) {
        cancelInternal(player, sessionId = null, notify = true)
    }

    fun cancel(player: Player, sessionId: UUID, notify: Boolean = false) {
        cancelInternal(player, sessionId, notify)
    }

    fun currentSessionId(player: Player): UUID? = sessions[player.uniqueId]?.id

    fun shutdown() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    private fun cancelInternal(player: Player, sessionId: UUID?, notify: Boolean) {
        val existing = sessions[player.uniqueId] ?: return
        if (sessionId != null && existing.id != sessionId) return
        sessions.remove(player.uniqueId)
        existing.close()
        if (notify) {
            val messages = ProtectionSettingsRepository.snapshot(player).messages
            sendMessage(player, messages.selection.sessionCancelled)
        }
    }

    private fun reuseOrCreateSession(
        player: Player,
        artifact: RegionArtifactEntry,
        definition: RegionDefinitionEntry?,
        label: String,
        notify: Boolean,
        messages: ProtectionMessages,
    ): SelectionSession? {
        val key = artifactKey(artifact)
        val existing = sessions.remove(player.uniqueId)
        val restored = existing?.takeIf { it.matches(artifact) }?.captureState()
        existing?.close()
        val session = SelectionSession(plugin, player, repository, artifact, definition, label, messages)
        restored?.let(session::restore)
        sessions[player.uniqueId] = session
        if (notify) session.sendActivationMessage()
        return session
    }

    private fun artifactLabel(artifact: RegionArtifactEntry): String = artifact.name.ifBlank { artifact.id }

    private fun sendMessage(player: Player, template: String?, placeholders: Map<String, Any?> = emptyMap()) {
        ProtectionMessageRenderer.render(template, placeholders).let { component ->
            component?.let(player::sendMessage)
        }
    }

    private fun sendMessage(player: Player, template: String?) {
        sendMessage(player, template, emptyMap())
    }
}

data class SelectionSnapshot(
    val mode: SelectionMode,
    val pointCount: Int,
    val canApply: Boolean,
)

private class SelectionSession(
    private val plugin: Plugin,
    private val player: Player,
    private val repository: RegionRepository,
    val artifact: RegionArtifactEntry,
    val definition: RegionDefinitionEntry?,
    private val label: String,
    private val messages: ProtectionMessages,
) {
    val id: UUID = UUID.randomUUID()
    private val logger = LoggerFactory.getLogger("SelectionSession")
    private val points = mutableListOf<Position>()
    private var verticalRange: Pair<Double, Double>? = null
    private var mode: SelectionMode = SelectionMode.CUBOID
    private val component = NodesComponent(
        nodeFetcher = { currentNodes() },
        nodePosition = { it.position },
        builder = { node -> decorateNode(node) }
    )
    private val visualizer = SelectionVisualizer(plugin, player)
    private val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
        runBlocking { component.tick(player) }
    }, 10L, 10L)

    val pointCount: Int
        get() = points.size

    private val artifactKey: String = artifactKey(artifact)

    init {
        runBlocking { component.initialize(player) }
        preloadExistingShape()
        refreshVisualization()
    }

    fun sendActivationMessage() {
        send(messages.selection.activation, mapOf("label" to label))
    }

    fun matches(candidate: RegionArtifactEntry): Boolean = artifactKey == artifactKey(candidate)

    fun snapshot(): SelectionSnapshot = SelectionSnapshot(mode, points.size, canCommit())

    fun appendPoint(position: Position) {
        val index = points.size
        setPoint(index, position)
    }

    fun setPoint(index: Int, position: Position) {
        val safeIndex = index.coerceAtLeast(0)
        if (!ensureSameWorld(safeIndex, position)) return
        when {
            safeIndex < points.size -> {
                points[safeIndex] = position
                recomputeRange()
                send(messages.selection.pointUpdated, coordinatePlaceholders(safeIndex + 1, position))
            }
            safeIndex == points.size -> {
                points += position
                includeY(position.y)
                send(messages.selection.pointAdded, coordinatePlaceholders(safeIndex + 1, position))
            }
            else -> send(messages.selection.pointAddFailed, mapOf("index" to (safeIndex + 1).toString()))
        }
        refreshVisualization()
    }

    fun removePoint(index: Int): Boolean {
        if (index !in points.indices) return false
        val removed = points.removeAt(index)
        recomputeRange()
        send(messages.selection.pointRemoved, coordinatePlaceholders(index + 1, removed))
        refreshVisualization()
        return true
    }

    fun removeLastPoint(): Boolean {
        if (points.isEmpty()) return false
        val lastIndex = points.lastIndex
        val removed = points.removeAt(lastIndex)
        recomputeRange()
        send(messages.selection.lastPointRemoved, coordinatePlaceholders(lastIndex + 1, removed))
        refreshVisualization()
        return true
    }

    fun cycleMode() {
        mode = mode.next()
        player.sendMessage(mode.displayName(messages.modes))
        player.sendMessage(mode.description(messages.modes))
        refreshVisualization()
    }

    fun applyGeometry(geometry: WorldEditGeometry) {
        applyShape(geometry.shape, geometry.nodes, geometry.mode, announce = true)
    }

    fun applyShape(
        shape: RegionShape,
        storedNodes: List<Position>? = null,
        storedMode: SelectionMode? = null,
        announce: Boolean = false,
    ) {
        val targetMode = storedMode ?: SelectionMode.fromShape(shape)
        val nodes = when {
            !storedNodes.isNullOrEmpty() -> storedNodes
            else -> convertShape(shape)
        }
        mode = targetMode
        points.clear()
        points.addAll(nodes)
        verticalRange = nodes.takeIf { it.isNotEmpty() }?.verticalRange()
        if (announce) {
            val suffix = if (points.size > 1) "s" else ""
            send(
                messages.selection.selectionSynced,
                mapOf(
                    "points" to points.size.toString(),
                    "plural_suffix" to suffix
                )
            )
        }
        refreshVisualization()
    }

    fun commit(): SelectionCommitResult {
        if (points.size < mode.minimumPoints) return SelectionCommitResult.NotEnoughPoints
        val worldIds = points.map { it.world.identifier }.toSet()
        if (worldIds.size > 1) return SelectionCommitResult.MixedWorlds(worldIds)
        val shape = mode.computeShape(points, verticalRange) ?: return SelectionCommitResult.InvalidGeometry
        return SelectionCommitResult.Success(shape, points.toList(), mode)
    }

    fun close() {
        task.cancel()
        runBlocking { component.dispose(player) }
        visualizer.clear()
    }

    fun minimumPoints(): Int = mode.minimumPoints

    fun captureState(): SessionState? {
        if (points.isEmpty()) return null
        return SessionState(mode, points.toList(), verticalRange)
    }

    fun restore(state: SessionState) {
        mode = state.mode
        points.clear()
        points.addAll(state.points)
        verticalRange = state.range ?: points.verticalRange()
        refreshVisualization()
    }

    private fun ensureSameWorld(index: Int, position: Position): Boolean {
        val first = points.firstOrNull() ?: return true
        val currentId = first.world.identifier
        val candidateId = position.world.identifier
        if (currentId.isBlank()) {
            rewriteWorld(position.world)
            return true
        }
        if (candidateId.isBlank()) {
            return true
        }
        if (currentId.equals(candidateId, ignoreCase = true)) {
            if (first.world != position.world) {
                rewriteWorld(position.world)
            }
            return true
        }
        if (index == 0) {
            rewriteWorld(position.world)
            return true
        }
        send(messages.selection.ensureSameWorld, mapOf("world" to first.world.identifier))
        return false
    }

    private fun includeY(value: Double) {
        verticalRange = verticalRange?.let { it.first.coerceAtMost(value) to it.second.coerceAtLeast(value) }
            ?: (value to value)
    }

    private fun recomputeRange() {
        verticalRange = if (points.isEmpty()) {
            null
        } else {
            points.verticalRange()
        }
    }

    private fun refreshVisualization() {
        if (points.size < mode.minimumPoints) {
            visualizer.clear()
            return
        }
        visualizer.update(mode, points.toList(), verticalRange)
    }

    private fun canCommit(): Boolean {
        if (points.size < mode.minimumPoints) return false
        val worldIds = points.map { it.world.identifier }.toSet()
        if (worldIds.size > 1) return false
        return mode.computeShape(points, verticalRange) != null
    }

    private fun rewriteWorld(world: World) {
        if (points.isEmpty()) return
        for (index in points.indices) {
            points[index] = points[index].withWorld(world)
        }
    }

    private fun preloadExistingShape() {
        val stored = RegionArtifactStorage.load(artifact)
        if (stored != null) {
            applyShape(stored.shape, stored.nodes, stored.mode, announce = false)
            return
        }
        val current = repository.findById(artifactKey)?.shape ?: definition?.let { repository.findById(it.id)?.shape }
        if (current != null) {
            applyShape(current, announce = false)
        }
    }

    private fun currentNodes(): Collection<SelectionNode> =
        points.mapIndexed { index, position -> SelectionNode(index, position) }

    private fun NodeDisplayBuilder.decorateNode(node: SelectionNode) {
        item = ItemStack(
            when (node.index) {
                0 -> Material.EMERALD_BLOCK
                1 -> Material.AMETHYST_BLOCK
                else -> Material.DIAMOND_BLOCK
            }
        )
        glow = when (node.index) {
            0 -> TextColor.color(0x1fd3a9)
            1 -> TextColor.color(0x21b5d3)
            else -> TextColor.color(0xf9c74f)
        }
        scale = Vector3f(0.8f, 0.8f, 0.8f)
        onInteract {
            if (player.isSneaking) {
                removePoint(node.index)
            } else {
                send(messages.selection.pointInfo, coordinatePlaceholders(node.index + 1, node.position))
            }
        }
    }

    private fun convertShape(shape: RegionShape): List<Position> {
        return when (shape) {
            is CuboidShape -> {
                val min = shape.min()
                val max = shape.max()
                listOf(min, max)
            }
            is PolygonPrismShape -> shape.vertices.map { Position(it.world, it.x, shape.minY, it.z) }
            is CylinderShape -> {
                val steps = 16
                (0 until steps).map { step ->
                    val angle = 2 * PI * step / steps
                    val x = shape.center.x + shape.radius * cos(angle)
                    val z = shape.center.z + shape.radius * sin(angle)
                    Position(shape.center.world, x, shape.minY, z)
                }
            }
            is FlatPolygonShape -> shape.vertices.map { Position(it.world, it.x, shape.y, it.z) }
            is GlobalRegionShape -> emptyList()
        }
    }

    private fun send(template: String?, placeholders: Map<String, Any?> = emptyMap()) {
        ProtectionMessageRenderer.render(template, placeholders)?.let(player::sendMessage)
    }

    private fun coordinatePlaceholders(index: Int, position: Position): Map<String, Any?> = mapOf(
        "index" to index.toString(),
        "x" to position.x.format(),
        "y" to position.y.format(),
        "z" to position.z.format()
    )
}

private data class SelectionNode(
    val index: Int,
    val position: Position,
)

private data class SessionState(
    val mode: SelectionMode,
    val points: List<Position>,
    val range: Pair<Double, Double>?,
)

private sealed class SelectionCommitResult {
    data class Success(val shape: RegionShape, val nodes: List<Position>, val mode: SelectionMode) : SelectionCommitResult()
    data object NotEnoughPoints : SelectionCommitResult()
    data class MixedWorlds(val worlds: Set<String>) : SelectionCommitResult()
    data object InvalidGeometry : SelectionCommitResult()
}

private fun artifactKey(artifact: RegionArtifactEntry): String =
    artifact.artifactId.takeIf { it.isNotBlank() } ?: artifact.id

private fun Double.format(): String = String.format("%.2f", this)

