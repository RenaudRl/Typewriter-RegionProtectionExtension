package com.typewritermc.protection.entry.event

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.EntryListener
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.EventEntry
import com.typewritermc.engine.paper.entry.triggerAllFor
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.events.ProtectionRegionsEnterEvent

@Entry(
    "on_enter_protection_region",
    "When a player enters a protection region",
    Colors.YELLOW,
    "fa6-solid:door-open"
)
class EnterRegionEventEntry(
    override val id: String = "",
    override val name: String = "",
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    @Help("If left blank, the entry will trigger for all regions")
    val region: Ref<RegionDefinitionEntry> = emptyRef(),
) : EventEntry

@EntryListener(EnterRegionEventEntry::class)
fun onEnterProtectionRegions(
    event: ProtectionRegionsEnterEvent,
    query: Query<EnterRegionEventEntry>,
) {
    query.findWhere { entry ->
        val region = entry.region.get()
        region == null || event.contains(region)
    }.triggerAllFor(event.player, context())
}

