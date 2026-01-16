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
    protected fun regionsAt(location: Location?): List<RegionModel> {
        if (location == null) return emptyList()
        return regionRepository.regionsAt(location.toTWPosition())
    }

    protected fun evaluateFlag(
        key: com.typewritermc.protection.flags.RegionFlagKey,
        event: Event,
        location: Location?,
        player: Player? = null,
        source: Entity? = null,
        target: Entity? = null,
        populateContext: (FlagContext.() -> Unit)? = null
    ): Pair<com.typewritermc.protection.flags.FlagEvaluation, FlagContext?> {
        val regions = regionsAt(location)
        if (regions.isEmpty()) return com.typewritermc.protection.flags.FlagEvaluation.pass() to null

        for (region in regions) {
            val context = createContext(region, event, location, player, source, target)
            populateContext?.invoke(context)
            val result = actionExecutor.evaluate(context, key)
            if (result !is com.typewritermc.protection.flags.FlagEvaluation.Pass) {
                return result to context
            }
        }
        return com.typewritermc.protection.flags.FlagEvaluation.pass() to null
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

