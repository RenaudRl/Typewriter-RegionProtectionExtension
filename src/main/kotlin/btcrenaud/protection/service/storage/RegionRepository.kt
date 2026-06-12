package btcrenaud.protection.service.storage

import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entries.get
import btcrenaud.protection.entry.artifact.GlobalRegionArtifactEntry
import btcrenaud.protection.entry.artifact.RegionArtifactEntry
import btcrenaud.protection.entry.region.RegionDefinitionEntry
import btcrenaud.protection.entry.region.groupIds
import btcrenaud.protection.entry.region.parentDefinition
import btcrenaud.protection.flags.FlagBinding
import btcrenaud.protection.flags.FlagEvaluationService
import btcrenaud.protection.flags.RegionFlagKey
import btcrenaud.protection.selection.CuboidShape
import btcrenaud.protection.selection.GlobalRegionShape
import btcrenaud.protection.selection.RegionShape
import btcrenaud.protection.selection.SelectionMode
import btcrenaud.protection.selection.toRegionShape
import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Singleton
class RegionRepository(
    private val regionStorage: RegionStorage,
) {
    private val logger = LoggerFactory.getLogger("RegionRepository")
    private val lock = ReentrantReadWriteLock()
    private val regions = ConcurrentHashMap<String, RegionModel>()
    private val spatialIndex = RegionSpatialIndex()
    private val changeListeners = mutableListOf<RegionChangeListener>()

    fun addChangeListener(listener: RegionChangeListener) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: RegionChangeListener) {
        changeListeners.remove(listener)
    }

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
            spatialIndex.rebuild(regions.values)
            loaded = regions.size
        }
        logger.info("Loaded {} protection regions", loaded)
        FlagEvaluationService.invalidateAllIfReady()
        // Notify listeners of full reload
        changeListeners.forEach { try { it.onRegionReload() } catch (e: Exception) { logger.warn("Error notifying region change listener", e) }
        }
    }

    fun all(): Collection<RegionModel> = lock.read { regions.values.toList() }

    fun findById(id: String): RegionModel? = lock.read { regions[id] }

    fun regionsAt(position: Position): List<RegionModel> = lock.read {
        spatialIndex.query(position)
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
                val updated = current.copy(shape = shape)
                regions[regionId] = updated
                spatialIndex.remove(current)
                spatialIndex.add(updated)
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
                spatialIndex.add(fallback)
                logger.warn(
                    "Created runtime region model for {} while applying a selection (missing during update)",
                    regionId
                )
            }
        }
        // Notify listeners
        changeListeners.forEach { try { it.onRegionChanged(definition.id) } catch (e: Exception) { logger.warn("Error notifying region change listener", e) }
        }
    }

    fun setFlag(regionId: String, flagKey: RegionFlagKey, rawValue: String) {
        lock.write {
            val current = regions[regionId] ?: return
            val parsed = btcrenaud.protection.flags.parseFlagValue(flagKey, rawValue)
            val newFlags = current.flags.filter { it.key != flagKey } + FlagBinding.create(flagKey, parsed)
            val updated = current.copy(flags = newFlags)
            regions[regionId] = updated
            FlagEvaluationService.invalidateAllIfReady()
        }
    }

    fun addOwner(regionId: String, uuid: String) {
        lock.write {
            val current = regions[regionId] ?: return
            val updated = current.copy(owners = current.owners + uuid)
            regions[regionId] = updated
        }
    }

    fun removeOwner(regionId: String, uuid: String) {
        lock.write {
            val current = regions[regionId] ?: return
            val updated = current.copy(owners = current.owners - uuid)
            regions[regionId] = updated
        }
    }

    fun addMember(regionId: String, uuid: String) {
        lock.write {
            val current = regions[regionId] ?: return
            val updated = current.copy(members = current.members + uuid)
            regions[regionId] = updated
        }
    }

    fun removeMember(regionId: String, uuid: String) {
        lock.write {
            val current = regions[regionId] ?: return
            val updated = current.copy(members = current.members - uuid)
            regions[regionId] = updated
        }
    }

    fun removeRegion(regionId: String) {
        lock.write {
            val removed = regions.remove(regionId)
            if (removed != null) {
                spatialIndex.remove(removed)
                // Also remove from children of parent
                removed.parentId?.let { parentId ->
                    regions[parentId]?.let { parent ->
                        val updated = parent.copy(children = parent.children - regionId)
                        regions[parentId] = updated
                    }
                }
            }
        }
        FlagEvaluationService.invalidateAllIfReady()
        changeListeners.forEach { try { it.onRegionChanged(regionId) } catch (e: Exception) { logger.warn("Error notifying region change listener", e) } }
    }

    fun updateArtifact(
        artifact: RegionArtifactEntry,
        shape: RegionShape,
        nodes: List<Position>,
        mode: SelectionMode?,
        actor: Player?,
    ) {
        val selectionMode = mode ?: runBlocking { regionStorage.load(artifact) }?.mode?.let { m ->
            SelectionMode.entries.firstOrNull { it.name == m }
        } ?: SelectionMode.fromShape(shape)
        runBlocking {
            regionStorage.save(artifact, RegionShapeData(mode = selectionMode.name, nodes = nodes))
        }
        val affectedRegionIds: List<String>
        lock.write {
            val regionIds = regions
                .filter { (_, model) -> model.artifact?.id == artifact.id }
                .keys
                .toList()
            affectedRegionIds = regionIds
            if (regionIds.isEmpty()) {
                logger.info(
                    "Persisted shape for artifact {} via {} (no active regions currently use it)",
                    artifact.artifactId,
                    actor?.name ?: "system"
                )
            } else {
                regionIds.forEach { id ->
                    regions[id]?.let { model ->
                        val updated = model.copy(shape = shape)
                        regions[id] = updated
                        spatialIndex.remove(model)
                        spatialIndex.add(updated)
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
        // Notify listeners for all affected regions (outside the lock)
        affectedRegionIds.forEach { id ->
            changeListeners.forEach { try { it.onRegionChanged(id) } catch (e: Exception) { logger.warn("Error notifying region change listener", e) } }
        }
    }

    private fun resolve(
        definition: RegionDefinitionEntry,
        resolved: MutableMap<String, MutableRegionModel>,
        visiting: MutableSet<String> = mutableSetOf(),
    ): MutableRegionModel {
        resolved[definition.id]?.let { return it }
        if (!visiting.add(definition.id)) {
            logger.warn("Circular parent reference detected for region {}; breaking cycle", definition.id)
            return createCycleBreaker(definition, resolved)
        }
        try {
            val artifact = definition.artifact.get()
            val parentDefinition = definition.parentDefinition
            val parentModel = parentDefinition?.let { parent ->
                resolve(parent, resolved, visiting)
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
        } finally {
            visiting.remove(definition.id)
        }
    }

    private fun createCycleBreaker(
        definition: RegionDefinitionEntry,
        resolved: MutableMap<String, MutableRegionModel>,
    ): MutableRegionModel {
        val artifact = definition.artifact.get()
        val model = MutableRegionModel(
            definition = definition,
            artifact = artifact,
            shape = resolveShape(definition, artifact),
            owners = definition.owners.toSet(),
            members = definition.members.toSet(),
            groups = definition.groupIds.toSet(),
            priority = definition.priority,
            flags = definition.flags,
            parentId = null,
        )
        resolved[definition.id] = model
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

    private fun resolveShape(
        definition: RegionDefinitionEntry,
        artifact: RegionArtifactEntry?,
    ): RegionShape {
        if (artifact == null) return CuboidShape()
        return when (artifact) {
            is GlobalRegionArtifactEntry -> {
                runBlocking { regionStorage.initializeGlobal(artifact) }
                runBlocking { regionStorage.loadGlobal(artifact) }?.let { data ->
                    GlobalRegionShape(worlds = data.worlds, minY = data.minY, maxY = data.maxY)
                } ?: artifact.toRegionShape()
            }
            else -> {
                runBlocking { regionStorage.initialize(artifact) }
                runBlocking { regionStorage.load(artifact) }?.let { data ->
                    val mode = SelectionMode.entries.firstOrNull { it.name == data.mode } ?: SelectionMode.CUBOID
                    mode.computeShape(data.nodes) ?: CuboidShape()
                } ?: CuboidShape()
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
        val regionId: String = definition.id
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

/**
 * Listener interface for region change events.
 * Implementations can react to region creation, updates, and full reloads.
 */
interface RegionChangeListener {
    fun onRegionChanged(regionId: String) {}
    fun onRegionReload() {}
}
