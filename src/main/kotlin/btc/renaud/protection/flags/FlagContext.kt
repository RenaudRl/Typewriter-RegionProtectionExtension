package btc.renaud.protection.flags

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

/**
 * Immutable snapshot describing the actor and positions involved in a flag evaluation.
 * This is the engine-level context used by [FlagEvaluationService].
 * For the listener-level context, see [btc.renaud.protection.listener.ProtectionListenerFlagContext].
 */
data class FlagContext(
    val player: Player?,
    val sourceEntity: Entity?,
    val targetEntity: Entity?,
    val origin: Location?,
    val destination: Location?,
    val action: FlagAction,
)

/** Identifies the high level action being evaluated (e.g. "protection:block-break"). */
@JvmInline
value class FlagAction(val id: String) {
    init {
        require(id.isNotBlank()) { "Flag action identifier must not be blank." }
        require(':' in id) { "Flag action identifier must contain a namespace (source:action)." }
    }

    override fun toString(): String = id

    companion object {
        val UNKNOWN = FlagAction("internal:unknown")
    }
}
