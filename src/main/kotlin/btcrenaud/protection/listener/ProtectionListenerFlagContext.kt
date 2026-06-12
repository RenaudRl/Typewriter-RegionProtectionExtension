package btcrenaud.protection.listener

import btcrenaud.protection.flags.FlagEvaluationContext
import btcrenaud.protection.service.storage.RegionModel
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event

import java.util.concurrent.ConcurrentHashMap

/**
 * Context passed to protection listeners while evaluating a flag on the Bukkit event thread.
 * Distinct from [btcrenaud.protection.flags.FlagContext] which is used
 * for the engine-level flag evaluation pipeline.
 */
data class ProtectionListenerFlagContext(
    val region: RegionModel,
    val event: Event,
    val location: Location? = null,
    val player: Player? = null,
    val source: Entity? = null,
    val target: Entity? = null,
    val runtimeData: MutableMap<String, Any?> = ConcurrentHashMap(),
) {
    fun toEvaluationContext(): FlagEvaluationContext = FlagEvaluationContext(this)

    fun canBypass(): Boolean {
        val actor = player ?: return false
        if (actor.hasPermission("typewriter.protection.bypass")) return true
        val id = actor.uniqueId.toString()
        return region.owners.contains(id) || region.members.contains(id)
    }
}
