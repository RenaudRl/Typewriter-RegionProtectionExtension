package com.typewritermc.protection.entry.artifact

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Entry as TWEntry
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import com.typewritermc.protection.selection.content.ProtectionRegionContentMode

@Tags("protection_region")
@Entry(
    "protection_region_artifact",
    "Persistent data for a TypeWriter-managed protection region",
    Colors.GREEN,
    "mdi:shield-lock"
)
open class RegionArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @ContentEditor(ProtectionRegionContentMode::class)
    override val artifactId: String = "typewritermc:protection_region",
) : ArtifactEntry, TWEntry {
    override val path: String
        get() = "artifacts/${artifactId.sanitizedForPath()}.$extension"
}

private fun String.sanitizedForPath(): String {
    val sanitized = replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return sanitized.ifBlank { this }
}

