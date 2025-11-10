package com.typewritermc.protection.listener

import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event

abstract class AbstractProtectionListener(
    private val regionRepository: RegionRepository,
    protected val actionExecutor: FlagActionExecutor,
) {
    protected fun dominantRegion(location: Location?): RegionModel? {
        if (location == null) return null
        return regionRepository.regionsAt(location.toTWPosition()).firstOrNull()
    }

    protected fun createContext(
        region: RegionModel,
        event: Event,
        location: Location?,
        player: Player? = null,
        source: Entity? = null,
        target: Entity? = null,
    ): FlagContext = FlagContext(
        region = region,
        event = event,
        location = location,
        player = player,
        source = source,
        target = target,
    )
}
