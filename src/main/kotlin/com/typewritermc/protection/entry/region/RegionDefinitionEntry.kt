package com.typewritermc.protection.entry.region

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Entry as TWEntry
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.GroupEntry
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.protection.entry.artifact.RegionArtifactEntry
import com.typewritermc.protection.flags.FlagBinding

@Tags("protection_region")
@Entry("region_definition", "Protection region definition", Colors.ORANGE, "mdi:shield-home")
open class RegionDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Primary artifact storing runtime data for this region")
    val artifact: Ref<RegionArtifactEntry> = emptyRef(),
    @Help("Priority applied when regions overlap (higher wins)")
    val priority: Int = 0,
    @Help("Players allowed to manage the region")
    val owners: List<String> = emptyList(),
    @Help("Players allowed to interact inside the region")
    val members: List<String> = emptyList(),
    @Help("Groups synchronized with the region")
    val groups: List<Ref<GroupEntry>> = emptyList(),
    @Help("Optional parent region used for inheritance")
    val parentRegion: Ref<RegionDefinitionEntry> = emptyRef(),
    @Help("Flags applied to the region")
    val flags: List<FlagBinding> = emptyList(),
) : ManifestEntry, TWEntry

val RegionDefinitionEntry.parentDefinition: RegionDefinitionEntry?
    get() = parentRegion.get()

val RegionDefinitionEntry.groupIds: List<String>
    get() = groups.mapNotNull { ref ->
        val entry = ref.get()
        if (entry != null && entry.id.isNotBlank()) entry.id else null
    }

