package btcrenaud.protection.flags

import btcrenaud.protection.listener.ProtectionListenerFlagContext
import btcrenaud.protection.service.storage.RegionModel
import kotlin.reflect.full.safeCast
import org.bukkit.entity.Player
import org.bukkit.event.Event

/**
 * Contextual data supplied to handlers and evaluation services while checking a flag.
 *
 * The [flag] snapshot is cheap to copy thanks to the pooling infrastructure exposed by [FlagContextPool].
 */
data class FlagEvaluationContext(
    val flag: ProtectionListenerFlagContext,
) {
    val region: RegionModel get() = flag.region
    val event: Event get() = flag.event
    val runtimeData: MutableMap<String, Any?> get() = flag.runtimeData
    val player: Player? get() = flag.player
    val location get() = flag.location
}

/**
 * Represents the possible outcomes for a flag evaluation.
 */
sealed interface FlagEvaluation {
    data object Allow : FlagEvaluation
    data object Pass : FlagEvaluation
    data class Denied(val reason: String? = null) : FlagEvaluation
    data class Modify(val metadata: Map<String, Any?> = emptyMap()) : FlagEvaluation

    companion object {
        fun pass(): FlagEvaluation = Pass
        fun deny(reason: String? = null): FlagEvaluation = Denied(reason)
        fun modify(metadata: Map<String, Any?> = emptyMap()): FlagEvaluation = Modify(metadata)
    }
}

/**
 * Wrapper passed to handlers exposing the resolved value together with provenance information.
 */
data class TypedFlagBinding<T : FlagValue>(
    val key: RegionFlagKey,
    val value: T,
    val sourceRegionId: String,
    val priority: Int,
    val definition: RegionFlagDefinition?,
    val original: FlagBinding,
)

/**
 * Contract for resolving the outcome of a flag binding. Implementations should be side-effect free and
 * rely on the supplied [FlagEvaluationContext].
 */
fun interface FlagHandler<T : FlagValue> {
    suspend fun evaluate(context: FlagEvaluationContext, binding: TypedFlagBinding<T>): FlagEvaluation
}

internal data class HandlerResult(
    val evaluation: FlagEvaluation,
    val binding: TypedFlagBinding<out FlagValue>,
)

internal class HandlerEntry<T : FlagValue>(
    private val type: kotlin.reflect.KClass<T>,
    private val handler: FlagHandler<T>,
) {
    suspend fun evaluate(
        context: FlagEvaluationContext,
        key: RegionFlagKey,
        resolved: ResolvedFlagBinding,
        definition: RegionFlagDefinition?,
    ): HandlerResult? {
        val value = type.safeCast(resolved.binding.value) ?: return null
        val typedBinding = TypedFlagBinding(
            key = key,
            value = value,
            sourceRegionId = resolved.regionId,
            priority = resolved.priority,
            definition = definition,
            original = resolved.binding,
        )
        val evaluation = handler.evaluate(context, typedBinding)
        return HandlerResult(evaluation, typedBinding)
    }
}
