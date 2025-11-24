package com.typewritermc.protection.entry.fact

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.entries.GroupEntry
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.facts.FactData
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.koin.java.KoinJavaComponent

@Entry(
    "in_protection_region_fact",
    "If the player is in a protection region",
    Colors.PURPLE,
    "fa6-solid:road-barrier"
)
class InRegionFact(
    override val id: String = "",
    override val name: String = "",
    override val comment: String = "",
    override val group: Ref<GroupEntry> = emptyRef(),
    val region: Ref<RegionDefinitionEntry> = emptyRef(),
) : ReadableFactEntry {

    private val repository: RegionRepository by lazy {
        KoinJavaComponent.get(RegionRepository::class.java)
    }

    override fun readSinglePlayer(player: Player): FactData {
        val target = region.get() ?: return FactData(0)
        val active = repository.regionsAt(player.location.toTWPosition())
        val value = if (active.any { it.id == target.id }) 1 else 0
        return FactData(value)
    }
}

