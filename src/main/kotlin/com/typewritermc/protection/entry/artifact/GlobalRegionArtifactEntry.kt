package com.typewritermc.protection.entry.artifact

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import java.util.Locale

@Tags("protection_region", "protection_region_global")
@Entry(
    "global_protection_region_artifact",
    "Persistent data for a world-wide protection region",
    Colors.GREEN,
    "mdi:earth"
)
class GlobalRegionArtifactEntry(
    id: String = "",
    name: String = "",
    artifactId: String = "typewritermc:global_protection_region",
    @Help("World identifiers covered by this region")
    val worlds: List<String> = emptyList(),
    @Help("Minimum height included in the region")
    val minY: Double = -64.0,
    @Help("Maximum height included in the region")
    val maxY: Double = 320.0,
) : RegionArtifactEntry(
    id = id,
    name = name,
    artifactId = artifactId,
) {
    override val path: String
        get() = "artifacts/${artifactId.replace(':', '/')}.${extension}"

    internal fun resolvedWorlds(): List<String> {
        if (worlds.isEmpty()) return emptyList()
        val collected = linkedMapOf<String, String>()
        worlds.forEach { candidate ->
            val trimmed = candidate.trim()
            if (trimmed.isNotEmpty()) {
                collected.putIfAbsent(trimmed.lowercase(Locale.ROOT), trimmed)
            }
        }
        return collected.values.toList()
    }
}
