package btcrenaud.protection.listener

import btcrenaud.protection.selection.toTWPosition
import btcrenaud.protection.service.storage.RegionRepository
import btcrenaud.protection.service.storage.RegionModel
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
        key: btcrenaud.protection.flags.RegionFlagKey,
        event: Event,
        location: Location?,
        player: Player? = null,
        source: Entity? = null,
        target: Entity? = null,
        populateContext: (ProtectionListenerFlagContext.() -> Unit)? = null
    ): Pair<btcrenaud.protection.flags.FlagEvaluation, ProtectionListenerFlagContext?> {
        val regions = regionsAt(location)
        if (regions.isEmpty()) return btcrenaud.protection.flags.FlagEvaluation.pass() to null

        for (region in regions) {
            val context = createContext(region, event, location, player, source, target)
            populateContext?.invoke(context)
            val result = actionExecutor.evaluate(context, key)
            if (result !is btcrenaud.protection.flags.FlagEvaluation.Pass) {
                return result to context
            }
        }
        return btcrenaud.protection.flags.FlagEvaluation.pass() to null
    }

    protected fun createContext(
        region: RegionModel,
        event: Event,
        location: Location?,
        player: Player? = null,
        source: Entity? = null,
        target: Entity? = null,
    ): ProtectionListenerFlagContext = ProtectionListenerFlagContext(
        region = region,
        event = event,
        location = location,
        player = player,
        source = source,
        target = target,
    )
}

