package com.typewritermc.protection.service.storage

import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.protection.entry.artifact.GlobalRegionArtifactEntry
import com.typewritermc.protection.entry.artifact.RegionArtifactEntry
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.entry.region.groupIds
import com.typewritermc.protection.entry.region.parentDefinition
import com.typewritermc.protection.flags.FlagBinding
import com.typewritermc.protection.flags.FlagEvaluationService
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.selection.RegionShape
import com.typewritermc.protection.selection.SelectionMode
import com.typewritermc.protection.selection.toRegionShape
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Singleton
class RegionRepository {
    private val logger = Companion.logger
    private val lock = ReentrantReadWriteLock()
    private val regions = ConcurrentHashMap<String, RegionModel>()

    init {
        reload()
    }

    fun reload() {
        var loaded = 0
        lock.write {
            val definitions = Query.find<RegionDefinitionEntry>().toList()
            val partial = mutableMapOf<String, MutableRegionModel>()
            definitions.forEach { definition ->
                resolve(definition, partial)
            }
            regions.clear()
            regions.putAll(partial.values.associateBy({ it.regionId }, { it.toRegionModel() }))
            loaded = regions.size
        }
        scheduleLoadLog(loaded)
        notifyFlagGraphReloaded()
    }

    fun all(): Collection<RegionModel> = lock.read { regions.values.toList() }

    fun findById(id: String): RegionModel? = lock.read { regions[id] }

    fun regionsAt(position: Position): List<RegionModel> = lock.read {
        regions.values.filter { it.shape.contains(position) }.sortedByDescending { it.priority }
    }

    fun updateRegion(
        definition: RegionDefinitionEntry,
        shape: RegionShape,
        nodes: List<Position>,
        mode: SelectionMode,
        actor: Player?,
    ) {
        val artifact = definition.artifact.get()
        if (artifact != null) {
            updateArtifact(artifact, shape, nodes, mode, actor)
            return
        }

        lock.write {
            val regionId = definition.id
            val current = regions[regionId]
            if (current != null) {
                regions[regionId] = current.copy(shape = shape)
                logger.info("Updated region {} shape via {}", regionId, actor?.name ?: "system")
            } else {
                val fallback = RegionModel(
                    regionId = regionId,
                    definition = definition,
                    artifact = null,
                    shape = shape,
                    owners = definition.owners.toSet(),
                    members = definition.members.toSet(),
                    groups = definition.groupIds.toSet(),
                    priority = definition.priority,
                    flags = definition.flags,
                    parentId = definition.parentDefinition?.id,
                    children = emptySet(),
                )
                regions[regionId] = fallback
                logger.warn(
                    "Created runtime region model for {} while applying a selection (missing during update)",
                    regionId
                )
            }
        }
    }

    fun updateArtifact(
        artifact: RegionArtifactEntry,
        shape: RegionShape,
        nodes: List<Position>,
        mode: SelectionMode?,
        actor: Player?,
    ) {
        val selectionMode = mode ?: RegionArtifactStorage.load(artifact)?.mode ?: SelectionMode.fromShape(shape)
        RegionArtifactStorage.save(artifact, selectionMode, nodes)
        lock.write {
            val regionIds = regions
                .filter { (_, model) -> model.artifact?.artifactId == artifact.artifactId || model.artifact?.id == artifact.id }
                .keys
            if (regionIds.isEmpty()) {
                logger.info(
                    "Persisted shape for artifact {} via {} (no active regions currently use it)",
                    artifact.artifactId,
                    actor?.name ?: "system"
                )
            } else {
                regionIds.forEach { id ->
                    regions[id]?.let { model ->
                        regions[id] = model.copy(shape = shape)
                    }
                }
                logger.info(
                    "Updated shape for {} region(s) linked to artifact {} via {}",
                    regionIds.size,
                    artifact.artifactId,
                    actor?.name ?: "system"
                )
            }
        }
    }

    private fun resolve(
        definition: RegionDefinitionEntry,
        resolved: MutableMap<String, MutableRegionModel>,
    ): MutableRegionModel {
        resolved[definition.id]?.let { return it }
        val artifact = definition.artifact.get()
        val parentDefinition = definition.parentDefinition
        val parentModel = parentDefinition?.let { parent ->
            resolve(parent, resolved)
        }
        val owners = buildSet {
            parentModel?.owners?.let { addAll(it) }
            addAll(definition.owners)
        }
        val members = buildSet {
            parentModel?.members?.let { addAll(it) }
            addAll(definition.members)
        }
        val groups = buildSet {
            parentModel?.groups?.let { addAll(it) }
            definition.groupIds.forEach { id -> if (id.isNotBlank()) add(id) }
        }
        val flags = mergeFlags(parentModel?.flags.orEmpty(), definition.flags)
        val priority = definition.priority.takeIf { it != 0 } ?: parentModel?.priority ?: 0
        val model = MutableRegionModel(
            definition = definition,
            artifact = artifact,
            shape = resolveShape(definition, artifact),
            owners = owners,
            members = members,
            groups = groups,
            priority = priority,
            flags = flags,
            parentId = parentModel?.regionId,
        )
        resolved[definition.id] = model
        parentModel?.children?.add(model.regionId)
        return model
    }

    private fun mergeFlags(parent: List<FlagBinding>, local: List<FlagBinding>): List<FlagBinding> {
        if (parent.isEmpty()) return local
        if (local.isEmpty()) return parent
        val merged = LinkedHashMap<RegionFlagKey, FlagBinding>()
        parent.forEach { merged[it.key] = it }
        local.forEach { merged[it.key] = it }
        return merged.values.toList()
    }

    private fun notifyFlagGraphReloaded() {
        val service: FlagEvaluationService? = try {
            KoinJavaComponent.get(FlagEvaluationService::class.java)
        } catch (ignored: Exception) {
            null
        }
        if (service == null) {
            logger.debug("FlagEvaluationService not available during repository reload")
        } else {
            service.invalidateAll()
        }
    }

    private fun scheduleLoadLog(size: Int) {
        enqueueLoadLog("Loaded $size protection regions")
    }

    private companion object {
        private val logger = LoggerFactory.getLogger("RegionRepository")
        private val logMutex = Any()
        private var pendingLoadLog: PendingLoadLog? = null
        private var lastImmediateMessage: String? = null
        private var lastImmediateTimestamp: Long = 0L

        private data class PendingLoadLog(
            val message: String,
            var occurrences: Int,
            var task: BukkitTask?,
        )

        private fun enqueueLoadLog(message: String) {
            val plugin = try {
                Bukkit.getPluginManager().getPlugin("TypeWriter")
            } catch (ex: Exception) {
                null
            }
            synchronized(logMutex) {
                if (plugin == null || !plugin.isEnabled) {
                    val now = System.currentTimeMillis()
                    if (lastImmediateMessage == message && now - lastImmediateTimestamp < 1_000L) {
                        lastImmediateTimestamp = now
                        return
                    }
                    lastImmediateMessage = message
                    lastImmediateTimestamp = now
                    logger.info(message)
                    return
                }

                lastImmediateMessage = null
                lastImmediateTimestamp = 0L

                val current = pendingLoadLog
                if (current == null || current.message != message) {
                    current?.task?.cancel()
                    pendingLoadLog = PendingLoadLog(
                        message = message,
                        occurrences = 1,
                        task = scheduleLog(plugin),
                    )
                } else {
                    current.occurrences += 1
                    current.task?.cancel()
                    current.task = scheduleLog(plugin)
                }
            }
        }

        private fun scheduleLog(plugin: Plugin): BukkitTask {
            return Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                flushPendingLog()
            }, 5L)
        }

        private fun flushPendingLog() {
            val snapshot: PendingLoadLog?
            synchronized(logMutex) {
                snapshot = pendingLoadLog
                pendingLoadLog = null
            }
            snapshot?.let { pending ->
                if (pending.occurrences <= 1) {
                    logger.info(pending.message)
                } else {
                    logger.info(
                        "{} (triggered {} times in quick succession)",
                        pending.message,
                        pending.occurrences,
                    )
                }
            }
        }
    }
}

private data class MutableRegionModel(
    val definition: RegionDefinitionEntry,
    val artifact: RegionArtifactEntry?,
    val shape: RegionShape,
    val owners: Set<String>,
    val members: Set<String>,
    val groups: Set<String>,
    val priority: Int,
    val flags: List<FlagBinding>,
    val parentId: String?,
) {
    val regionId: String = artifact?.id?.takeIf { it.isNotBlank() } ?: definition.id
    val children: MutableSet<String> = mutableSetOf()

    fun toRegionModel(): RegionModel = RegionModel(
        regionId = regionId,
        definition = definition,
        artifact = artifact,
        shape = shape,
        owners = owners,
        members = members,
        groups = groups,
        priority = priority,
        flags = flags,
        parentId = parentId,
        children = children.toSet(),
    )
}

private fun resolveShape(
    definition: RegionDefinitionEntry,
    artifact: RegionArtifactEntry?,
): RegionShape {
    return when (artifact) {
        null -> CuboidShape()
        is GlobalRegionArtifactEntry -> {
            RegionArtifactStorage.initialize(artifact)
            RegionArtifactStorage.load(artifact)?.shape ?: artifact.toRegionShape()
        }
        else -> {
            RegionArtifactStorage.initialize(artifact)
            RegionArtifactStorage.load(artifact)?.shape ?: CuboidShape()
        }
    }
}

data class RegionModel(
    val regionId: String,
    val definition: RegionDefinitionEntry,
    val artifact: RegionArtifactEntry?,
    val shape: RegionShape,
    val owners: Set<String>,
    val members: Set<String>,
    val groups: Set<String>,
    val priority: Int,
    val flags: List<FlagBinding>,
    val parentId: String?,
    val children: Set<String>,
) {
    val id: String get() = regionId
    val bounds: Pair<Position, Position> get() = shape.min() to shape.max()
}
