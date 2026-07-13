package btc.renaud.protection.bluemap

import com.typewritermc.core.extension.annotations.Help

/**
 * BlueMap display settings for a protection region.
 * Configures how the region appears on the BlueMap web interface.
 */
data class RegionBlueMapDisplaySettings(
    @Help("Enable BlueMap integration for this region")
    val enabled: Boolean = false,

    /**
     * Marker set id where this region's marker will be placed.
     * If the marker set doesn't exist, it will be created automatically.
     * If multiple regions share the same marker set id, they'll be grouped together.
     */
    @Help("Marker set id for grouping regions on BlueMap. Regions with the same set id are grouped together.")
    val markerSetId: String = "protection_regions",

    @Help("Label for the marker set (only used when creating a new marker set)")
    val markerSetLabel: String = "Protection Regions",

    @Help("Whether the marker set can be toggled on/off in the BlueMap UI")
    val markerSetToggleable: Boolean = true,

    @Help("Whether the marker set is hidden by default in the BlueMap UI")
    val markerSetDefaultHidden: Boolean = false,

    /**
     * Shape type for the marker.
     * EXTRUDE = 3D extruded shape (from minY to maxY)
     * SHAPE = flat 2D shape at a specific height
     */
    @Help("Marker shape type: EXTRUDE (3D) or SHAPE (flat)")
    val shapeType: BlueMapShapeType = BlueMapShapeType.EXTRUDE,

    @Help("Line color for the region border (CSS color format: #RRGGBB or #AARRGGBB)")
    val lineColor: String = "#FF0000",

    @Help("Fill color for the region area (CSS color format: #RRGGBB or #AARRGGBB)")
    val fillColor: String = "#FF000040",

    @Help("Line width in pixels for the region border")
    val lineWidth: Int = 2,

    @Help("Enable depth test (hide marker when behind terrain)")
    val depthTestEnabled: Boolean = true,

    @Help("Minimum Y height for extruded markers")
    val minY: Double = -64.0,

    @Help("Maximum Y height for extruded markers")
    val maxY: Double = 320.0,

    @Help("Label template for the marker. Placeholders: {name}, {id}, {priority}")
    val labelTemplate: String = "{name}",

    @Help("Sorting order for this marker within its set (lower = first)")
    val sorting: Int = 0,
)

enum class BlueMapShapeType {
    EXTRUDE,
    SHAPE
}
