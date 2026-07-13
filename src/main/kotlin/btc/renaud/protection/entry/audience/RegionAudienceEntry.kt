package btc.renaud.protection.entry.audience

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entries.AudienceEntry
import com.typewritermc.engine.paper.entry.entries.AudienceFilter
import com.typewritermc.engine.paper.entry.entries.AudienceFilterEntry
import com.typewritermc.engine.paper.entry.entries.Invertible
import com.typewritermc.engine.paper.entry.entries.get
import btc.renaud.protection.entry.region.RegionDefinitionEntry
import btc.renaud.protection.events.ProtectionRegionsEnterEvent
import btc.renaud.protection.events.ProtectionRegionsExitEvent
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.koin.java.KoinJavaComponent

@Entry(
    "protection_region_audience",
    "Filter players based on if they are in a protection region",
    Colors.MEDIUM_SEA_GREEN,
    "gis:location-man"
)
class RegionAudienceEntry(
    override val id: String = "",
    override val name: String = "",
    override val children: List<Ref<AudienceEntry>> = emptyList(),
    @Help("Region definition that should be considered by this audience filter")
    val region: Ref<RegionDefinitionEntry> = emptyRef(),
    override val inverted: Boolean = false,
) : AudienceFilterEntry, Invertible {

    override suspend fun display(): AudienceFilter {
        return RegionAudienceFilter(ref(), region)
    }
}

class RegionAudienceFilter(
    ref: Ref<out AudienceFilterEntry>,
    private val region: Ref<RegionDefinitionEntry>,
) : AudienceFilter(ref) {

    private val repository: RegionRepository by lazy {
        KoinJavaComponent.get(RegionRepository::class.java)
    }

    private fun targetId(): String? = region.get()?.id?.takeIf { it.isNotBlank() }

    @EventHandler
    fun onRegionEnter(event: ProtectionRegionsEnterEvent) {
        val id = targetId() ?: return
        if (!canConsider(event.player)) return
        if (!event.contains(id)) return
        event.player.updateFilter(true)
    }

    @EventHandler
    fun onRegionExit(event: ProtectionRegionsExitEvent) {
        val id = targetId() ?: return
        if (!canConsider(event.player)) return
        if (!event.contains(id)) return
        event.player.updateFilter(false)
    }

    override fun filter(player: Player): Boolean {
        val id = targetId() ?: return false
        val regions = repository.regionsAt(player.location.toTWPosition())
        return regions.any { it.id == id }
    }
}

