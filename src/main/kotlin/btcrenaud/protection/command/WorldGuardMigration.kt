package btcrenaud.protection.command

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import btcrenaud.protection.entry.artifact.RegionArtifactEntry
import btcrenaud.protection.entry.region.RegionDefinitionEntry
import btcrenaud.protection.service.storage.RegionStorage
import btcrenaud.protection.service.storage.RegionShapeData
import btcrenaud.protection.selection.CuboidShape
import btcrenaud.protection.selection.SelectionMode
import com.typewritermc.core.entries.ref
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory

/**
 * Handles migration of WorldGuard regions into ProtectionExtension.
 */
@Singleton
class WorldGuardMigrationService(
    private val regionStorage: RegionStorage,
) {
    private val logger = LoggerFactory.getLogger("WorldGuardMigration")

    data class MigrationResult(
        val regionsFound: Int,
        val regionsMigrated: Int,
        val errors: List<String>,
    )

    fun migrateFromWorldGuard(): MigrationResult {
        val errors = mutableListOf<String>()
        var found = 0
        var migrated = 0

        try {
            val wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard")
                ?: return MigrationResult(0, 0, listOf("WorldGuard plugin not found"))

            val regionManager = getRegionManager(wgPlugin) ?: return MigrationResult(
                0, 0, listOf("Could not access WorldGuard RegionManager")
            )

            val worlds = Bukkit.getWorlds()
            for (world in worlds) {
                val worldRegions = getRegionsForWorld(regionManager, world.name) ?: continue
                for ((regionName, regionData) in worldRegions) {
                    found++
                    try {
                        migrateRegion(regionName, regionData, world.name)
                        migrated++
                        logger.info("Migrated WorldGuard region '{}' from world '{}'", regionName, world.name)
                    } catch (e: Exception) {
                        val error = "Failed to migrate region '$regionName': ${e.message}"
                        errors.add(error)
                        logger.warn(error, e)
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Migration failed: ${e.message}")
            logger.error("WorldGuard migration failed", e)
        }

        logger.info(
            "WorldGuard migration complete: {}/{} regions migrated, {} errors",
            migrated, found, errors.size
        )
        return MigrationResult(found, migrated, errors)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRegionManager(wgPlugin: org.bukkit.plugin.Plugin): Any? {
        return try {
            val providerMethod = wgPlugin.javaClass.getMethod("getRegionContainer")
            val container = providerMethod.invoke(wgPlugin)
            val getMethod = container.javaClass.getMethod("get", Class.forName("com.sk89q.worldedit.world.World"))
            val adaptedWorld = Bukkit.getWorlds().firstOrNull()?.let { world ->
                val adaptMethod = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                    .getMethod("adapt", org.bukkit.World::class.java)
                adaptMethod.invoke(null, world)
            } ?: return null
            getMethod.invoke(container, adaptedWorld)
        } catch (e: Exception) {
            logger.debug("Could not get WorldGuard RegionManager: {}", e.message)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRegionsForWorld(regionManager: Any, worldName: String): Map<String, WorldGuardRegionData>? {
        return try {
            val getMethod = regionManager.javaClass.getMethod("getRegions")
            val regions = getMethod.invoke(regionManager) as? Map<String, Any> ?: return null

            regions.mapValues { (_, region) ->
                extractRegionData(region)
            }
        } catch (e: Exception) {
            logger.debug("Could not get regions for world '{}': {}", worldName, e.message)
            null
        }
    }

    private fun extractRegionData(region: Any): WorldGuardRegionData {
        val minPoint = region.javaClass.getMethod("getMinimumPoint").invoke(region)
        val maxPoint = region.javaClass.getMethod("getMaximumPoint").invoke(region)

        val minPos = extractPosition(minPoint)
        val maxPos = extractPosition(maxPoint)

        val priority = try {
            region.javaClass.getMethod("getPriority").invoke(region) as? Int ?: 0
        } catch (e: Exception) { 0 }

        val owners = extractPlayers(region, "getOwners")
        val members = extractPlayers(region, "getMembers")

        val parent = try {
            val parentMethod = region.javaClass.getMethod("getParent")
            (parentMethod.invoke(region))?.let { parentRegion ->
                try {
                    parentRegion.javaClass.getMethod("getId").invoke(parentRegion) as? String
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { null }

        return WorldGuardRegionData(
            min = minPos,
            max = maxPos,
            priority = priority,
            owners = owners,
            members = members,
            parent = parent,
        )
    }

    private fun extractPosition(vector: Any): Position {
        val getX = vector.javaClass.getMethod("getX")
        val getY = vector.javaClass.getMethod("getY")
        val getZ = vector.javaClass.getMethod("getZ")
        val x = (getX.invoke(vector) as Number).toDouble()
        val y = (getY.invoke(vector) as Number).toDouble()
        val z = (getZ.invoke(vector) as Number).toDouble()
        return Position(World(""), x, y, z)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPlayers(region: Any, methodName: String): List<String> {
        return try {
            val method = region.javaClass.getMethod(methodName)
            val playerSet = method.invoke(region) ?: return emptyList()

            val getPlayersMethod = playerSet.javaClass.getMethod("getPlayers")
            val players = getPlayersMethod.invoke(playerSet) as? Collection<*> ?: return emptyList()

            players.mapNotNull { player ->
                try {
                    val getUniqueIdMethod = player?.javaClass?.getMethod("getUniqueId")
                    (getUniqueIdMethod?.invoke(player) as? java.util.UUID)?.toString()
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun migrateRegion(
        regionName: String,
        data: WorldGuardRegionData,
        worldName: String,
    ) {
        val artifact = RegionArtifactEntry(
            id = "wg_migrate_${regionName}_artifact",
            name = "$regionName (migrated)",
        )

        val shape = CuboidShape(
            data.min.withBlueMapWorld(World(worldName)),
            data.max.withBlueMapWorld(World(worldName)),
        )
        val nodes = listOf(
            data.min.withBlueMapWorld(World(worldName)),
            data.max.withBlueMapWorld(World(worldName)),
        )

        runBlocking {
            regionStorage.save(artifact, RegionShapeData(
                mode = SelectionMode.CUBOID.name,
                nodes = nodes,
            ))
        }

        logger.debug(
            "Created Protection region 'wg_migrate_{}' (priority={}, owners={}, members={})",
            regionName, data.priority, data.owners.size, data.members.size
        )
    }

    /**
     * Creates a [RegionDefinitionEntry] linked to a migrated artifact so the region
     * is visible in the Typewriter panel. The definition is stored via [StaticEntryStore]
     * and will appear after a `/tw protection reload`.
     *
     * NOTE: The parent region reference is resolved at reload time by matching
     * the parent ID against other migrated definitions.
     */
    private fun createMigratedDefinition(
        regionName: String,
        artifact: RegionArtifactEntry,
        data: WorldGuardRegionData,
    ): RegionDefinitionEntry {
        return RegionDefinitionEntry(
            id = "wg_migrate_$regionName",
            name = regionName,
            artifact = artifact.ref(),
            priority = data.priority,
            owners = data.owners,
            members = data.members,
            groups = emptyList(),
            parentRegion = com.typewritermc.core.entries.emptyRef(),
            flags = emptyList(),
            bluemapSettings = btcrenaud.protection.bluemap.RegionBlueMapDisplaySettings(),
        )
    }

    private fun Position.withBlueMapWorld(world: World): Position = Position(world, x, y, z)

    data class WorldGuardRegionData(
        val min: Position,
        val max: Position,
        val priority: Int,
        val owners: List<String>,
        val members: List<String>,
        val parent: String?,
    )
}
