package com.typewritermc.protection.entry.group

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.entries.GroupEntry
import com.typewritermc.engine.paper.entry.entries.GroupId
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.koin.java.KoinJavaComponent

@Entry(
    "protection_region_group",
    "All players grouped by protection regions",
    Colors.MYRTLE_GREEN,
    "fa6-solid:object-group"
)
class RegionGroup(
    override val id: String = "",
    override val name: String = "",
    val regions: List<Ref<RegionDefinitionEntry>> = emptyList(),
) : GroupEntry {

    private val repository: RegionRepository by lazy {
        KoinJavaComponent.get(RegionRepository::class.java)
    }

    override fun groupId(player: Player): GroupId? {
        val regionIds = regions.mapNotNull { ref ->
            ref.get()?.id?.takeIf { it.isNotBlank() }
        }
        if (regionIds.isEmpty()) return null
        val active = repository.regionsAt(player.location.toTWPosition())
        val match = active.firstOrNull { model -> regionIds.contains(model.id) } ?: return null
        return GroupId(match.id)
    }
}

