package com.typewritermc.protection.entry.artifact

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Entry as TWEntry
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry

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
    @Help("Unique identifier for the artifact.")
    override val artifactId: String = "protection_region",
) : ArtifactEntry, TWEntry

