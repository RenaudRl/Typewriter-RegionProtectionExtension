package btc.renaud.protection.bluemap

import com.flowpowered.math.vector.Vector2d
import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.selection.CuboidShape
import btc.renaud.protection.selection.CylinderShape
import btc.renaud.protection.selection.FlatPolygonShape
import btc.renaud.protection.selection.GlobalRegionShape
import btc.renaud.protection.selection.PolygonPrismShape
import btc.renaud.protection.selection.RegionShape
import btc.renaud.protection.service.storage.RegionChangeListener
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import de.bluecolored.bluemap.api.BlueMapAPI
import de.bluecolored.bluemap.api.BlueMapMap
import de.bluecolored.bluemap.api.BlueMapWorld
import de.bluecolored.bluemap.api.markers.ExtrudeMarker
import de.bluecolored.bluemap.api.markers.MarkerSet
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that synchronizes protection regions with BlueMap markers.
 *
 * Lifecycle:
 * - On region create/update → create/update BlueMap marker
 * - On region delete → remove BlueMap marker
 * - Marker sets are created automatically when first referenced
 * - Marker sets are deleted automatically when last region is removed
 */
@Singleton
class BlueMapIntegrationService(
    private val regionRepository: RegionRepository,
) : RegionChangeListener {
    private val logger = LoggerFactory.getLogger("BlueMapIntegration")

    // Track which marker sets were created by us (for auto-cleanup)
    private val managedMarkerSets = ConcurrentHashMap<String, MutableSet<String>>() // markerSetId -> set of regionIds

    // Track BlueMap availability
    private var blueMapAvailable: Boolean = false

    init {
        // Register as a region change listener
        regionRepository.addChangeListener(this)

        // Register with BlueMap API
        try {
            BlueMapAPI.onEnable { api ->
                blueMapAvailable = true
                logger.info("BlueMap API enabled — synchronizing all regions")
                synchronizeAllRegions(api)
            }
            BlueMapAPI.onDisable {
                blueMapAvailable = false
                logger.info("BlueMap API disabled")
            }
            // If BlueMap is already enabled
            BlueMapAPI.getInstance().ifPresent { api ->
                blueMapAvailable = true
                synchronizeAllRegions(api)
            }
        } catch (e: Exception) {
            logger.warn("BlueMap API not available: {}", e.message)
        }
    }

    override fun onRegionChanged(regionId: String) {
        if (!blueMapAvailable) return
        val region = regionRepository.findById(regionId) ?: return
        BlueMapAPI.getInstance().ifPresent { api ->
            try {
                updateRegionMarker(api, region)
            } catch (e: Exception) {
                logger.error("Failed to update BlueMap marker for region '{}'", regionId, e)
            }
        }
    }

    override fun onRegionReload() {
        if (!blueMapAvailable) return
        BlueMapAPI.getInstance().ifPresent { api ->
            synchronizeAllRegions(api)
        }
    }

    /**
     * Called when a region is created or updated.
     * Creates or updates the corresponding BlueMap marker.
     */
    fun onRegionUpdated(region: RegionModel) {
        if (!blueMapAvailable) return
        BlueMapAPI.getInstance().ifPresent { api ->
            try {
                updateRegionMarker(api, region)
            } catch (e: Exception) {
                logger.error("Failed to update BlueMap marker for region '{}'", region.regionId, e)
            }
        }
    }

    /**
     * Called when a region is deleted.
     * Removes the corresponding BlueMap marker and cleans up empty marker sets.
     */
    fun onRegionRemoved(regionId: String) {
        if (!blueMapAvailable) return
        BlueMapAPI.getInstance().ifPresent { api ->
            try {
                removeRegionMarker(api, regionId)
            } catch (e: Exception) {
                logger.error("Failed to remove BlueMap marker for region '{}'", regionId, e)
            }
        }
    }

    /**
     * Synchronize all regions with BlueMap.
     * Called on BlueMap enable or plugin startup.
     */
    private fun synchronizeAllRegions(api: BlueMapAPI) {
        val regions = regionRepository.all()
        var synced = 0
        for (region in regions) {
            try {
                if (region.definition.bluemapSettings.enabled) {
                    updateRegionMarker(api, region)
                    synced++
                }
            } catch (e: Exception) {
                logger.error("Failed to sync region '{}' with BlueMap", region.regionId, e)
            }
        }
        logger.info("Synchronized {} regions with BlueMap", synced)
    }

    /**
     * Create or update a BlueMap marker for a region.
     */
    private fun updateRegionMarker(api: BlueMapAPI, region: RegionModel) {
        val settings = region.definition.bluemapSettings
        if (!settings.enabled) {
            removeRegionMarker(api, region.regionId)
            return
        }

        val worldId = getWorldId(region) ?: return
        val blueMapWorld = api.getWorld(worldId).orElse(null) ?: run {
            logger.debug("BlueMap world '{}' not found for region '{}'", worldId, region.regionId)
            return
        }

        // Find the first available map for this world
        val map = blueMapWorld.getMaps().firstOrNull() ?: run {
            logger.debug("No BlueMap map found for world '{}'", worldId)
            return
        }

        // Get or create the marker set
        val markerSet = getOrCreateMarkerSet(map, settings)

        // Build the marker
        val markerId = "protection_${region.regionId}"
        val label = buildLabel(settings.labelTemplate, region)

        val shape = region.shape
        val marker = when (settings.shapeType) {
            BlueMapShapeType.EXTRUDE -> createExtrudeMarker(markerId, label, shape, settings)
            BlueMapShapeType.SHAPE -> createShapeMarker(markerId, label, shape, settings)
        } ?: return

        // Add marker to set
        markerSet.getMarkers().put(markerId, marker)

        // Track this marker set as managed
        managedMarkerSets.getOrPut(settings.markerSetId) { ConcurrentHashMap.newKeySet() }.add(region.regionId)

        logger.debug("Updated BlueMap marker for region '{}' in marker set '{}'", region.regionId, settings.markerSetId)
    }

    /**
     * Remove a BlueMap marker for a region.
     */
    private fun removeRegionMarker(api: BlueMapAPI, regionId: String) {
        val worldId = findWorldIdForRegion(api, regionId) ?: return
        val blueMapWorld = api.getWorld(worldId).orElse(null) ?: return

        for (map in blueMapWorld.getMaps()) {
            val markerId = "protection_$regionId"
            for ((setId, markerSet) in map.getMarkerSets()) {
                if (markerSet.getMarkers().remove(markerId) != null) {
                    // Track removal
                    managedMarkerSets[setId]?.remove(regionId)

                    // Clean up empty marker sets that we manage
                    if (markerSet.getMarkers().isEmpty() && managedMarkerSets[setId]?.isEmpty() == true) {
                        map.getMarkerSets().remove(setId)
                        managedMarkerSets.remove(setId)
                        logger.debug("Removed empty BlueMap marker set '{}'", setId)
                    }

                    logger.debug("Removed BlueMap marker for region '{}'", regionId)
                    return
                }
            }
        }
    }

    /**
     * Get an existing marker set or create a new one.
     */
    private fun getOrCreateMarkerSet(map: BlueMapMap, settings: RegionBlueMapDisplaySettings): MarkerSet {
        return map.getMarkerSets().getOrPut(settings.markerSetId) {
            val newSet = MarkerSet.builder()
                .label(settings.markerSetLabel)
                .toggleable(settings.markerSetToggleable)
                .defaultHidden(settings.markerSetDefaultHidden)
                .sorting(settings.sorting)
                .build()
            logger.info("Created new BlueMap marker set '{}'", settings.markerSetId)
            newSet
        }
    }

    /**
     * Create an ExtrudeMarker from a Protection region shape.
     */
    private fun createExtrudeMarker(
        markerId: String,
        label: String,
        shape: RegionShape,
        settings: RegionBlueMapDisplaySettings,
    ): ExtrudeMarker? {
        val blueMapShape = shape.toBlueMapShape() ?: return null
        val lineColor = parseColor(settings.lineColor)
        val fillColor = parseColor(settings.fillColor)

        return ExtrudeMarker.builder()
            .label(label)
            .shape(blueMapShape, settings.minY.toFloat(), settings.maxY.toFloat())
            .lineColor(lineColor)
            .fillColor(fillColor)
            .lineWidth(settings.lineWidth)
            .depthTestEnabled(settings.depthTestEnabled)
            .centerPosition()
            .build()
    }

    /**
     * Create a ShapeMarker from a Protection region shape.
     */
    private fun createShapeMarker(
        markerId: String,
        label: String,
        shape: RegionShape,
        settings: RegionBlueMapDisplaySettings,
    ): de.bluecolored.bluemap.api.markers.ShapeMarker? {
        val blueMapShape = shape.toBlueMapShape() ?: return null
        val lineColor = parseColor(settings.lineColor)
        val fillColor = parseColor(settings.fillColor)
        val y = when (shape) {
            is FlatPolygonShape -> shape.y
            else -> 64.0
        }

        return de.bluecolored.bluemap.api.markers.ShapeMarker.builder()
            .label(label)
            .shape(blueMapShape, y.toFloat())
            .lineColor(lineColor)
            .fillColor(fillColor)
            .lineWidth(settings.lineWidth)
            .depthTestEnabled(settings.depthTestEnabled)
            .centerPosition()
            .build()
    }

    /**
     * Convert a Protection RegionShape to a BlueMap Shape.
     */
    private fun RegionShape.toBlueMapShape(): de.bluecolored.bluemap.api.math.Shape? {
        return when (this) {
            is CuboidShape -> {
                val min = this.min()
                val max = this.max()
                de.bluecolored.bluemap.api.math.Shape.createRect(
                    min.x, min.z,
                    max.x, max.z
                )
            }
            is PolygonPrismShape -> {
                if (this.vertices.size < 3) return null
                val builder = de.bluecolored.bluemap.api.math.Shape.builder()
                for (vertex in this.vertices) {
                    builder.addPoint(Vector2d(vertex.x, vertex.z))
                }
                builder.build()
            }
            is FlatPolygonShape -> {
                if (this.vertices.size < 3) return null
                val builder = de.bluecolored.bluemap.api.math.Shape.builder()
                for (vertex in this.vertices) {
                    builder.addPoint(Vector2d(vertex.x, vertex.z))
                }
                builder.build()
            }
            is CylinderShape -> {
                val center = this.center
                de.bluecolored.bluemap.api.math.Shape.createCircle(
                    center.x, center.z,
                    this.radius, 32
                )
            }
            is GlobalRegionShape -> {
                // For global regions, create a large rectangle covering the world
                // BlueMap will clip it to the actual world bounds
                de.bluecolored.bluemap.api.math.Shape.createRect(
                    -30_000_000.0, -30_000_000.0,
                    30_000_000.0, 30_000_000.0
                )
            }
        }
    }

    /**
     * Parse a CSS color string to a BlueMap Color.
     */
    private fun parseColor(colorString: String): de.bluecolored.bluemap.api.math.Color {
        return try {
            de.bluecolored.bluemap.api.math.Color(colorString)
        } catch (e: Exception) {
            logger.warn("Invalid color '{}', defaulting to red", colorString)
            de.bluecolored.bluemap.api.math.Color(255, 0, 0, 1f)
        }
    }

    /**
     * Build the marker label from a template.
     */
    private fun buildLabel(template: String, region: RegionModel): String {
        return template
            .replace("{name}", region.definition.name.ifBlank { region.regionId })
            .replace("{id}", region.regionId)
            .replace("{priority}", region.priority.toString())
    }

    /**
     * Get the world ID for a region based on its shape.
     */
    private fun getWorldId(region: RegionModel): String? {
        val shape = region.shape
        val world = when (shape) {
            is CuboidShape -> shape.min().world
            is PolygonPrismShape -> shape.vertices.firstOrNull()?.world
            is FlatPolygonShape -> shape.vertices.firstOrNull()?.world
            is CylinderShape -> shape.center.world
            is GlobalRegionShape -> {
                // For global regions, use the first world in the list
                return shape.worlds.firstOrNull()
            }
        }
        return world?.identifier?.takeIf { it.isNotBlank() }
    }

    /**
     * Find the world ID for a region by searching all BlueMap worlds.
     */
    private fun findWorldIdForRegion(api: BlueMapAPI, regionId: String): String? {
        for (world in api.getWorlds()) {
            for (map in world.getMaps()) {
                val markerId = "protection_$regionId"
                for (markerSet in map.getMarkerSets().values) {
                    if (markerSet.getMarkers().containsKey(markerId)) {
                        return world.getId()
                    }
                }
            }
        }
        return null
    }
}
