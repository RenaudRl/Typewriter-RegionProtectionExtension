package com.typewritermc.protection.flags

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.slf4j.LoggerFactory
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves flag bindings for a region hierarchy while caching the effective graph.
 */
@Singleton
class FlagEvaluationService(
    private val regionRepository: RegionRepository,
    private val registry: RegionFlagRegistry,
) {
    init {
        Companion.instance = this
    }

    private val logger = LoggerFactory.getLogger("FlagEvaluationService")
    private val bindingCache = ConcurrentHashMap<String, Map<RegionFlagKey, List<ResolvedFlagBinding>>>()
    private val updateFlow = MutableSharedFlow<FlagUpdateEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Exposes a hot stream of flag cache invalidations. */
    val updates: SharedFlow<FlagUpdateEvent> = updateFlow.asSharedFlow()

    /**
     * Evaluates [key] against the supplied [context], respecting parent bindings and handler priorities.
     */
    suspend fun evaluate(context: FlagEvaluationContext, key: RegionFlagKey): FlagEvaluation {
        val entry = registry.handlerEntry(key)
        if (entry == null) {
            logger.debug("No handler registered for flag {}", key.id)
            return FlagEvaluation.pass()
        }
        val definition = registry.definition(key)
        val bindings = bindingsFor(context.region)[key] ?: return FlagEvaluation.pass()
        if (bindings.isEmpty()) return FlagEvaluation.pass()

        var allow = false
        var allowBinding: TypedFlagBinding<out FlagValue>? = null
        val modifications = LinkedHashMap<String, Any?>()
        for (resolved in bindings) {
            val handlerResult = entry.evaluate(context, key, resolved, definition)
            if (handlerResult == null) {
                logger.debug(
                    "Skipping flag {} binding from {} due to incompatible value {}",
                    key.id,
                    resolved.regionId,
                    resolved.binding.value::class.simpleName
                )
                continue
            }
            when (val evaluation = handlerResult.evaluation) {
                is FlagEvaluation.Denied -> {
                    logger.debug(
                        "Flag {} denied action in region {} (source {})",
                        key.id,
                        context.region.id,
                        resolved.regionId
                    )
                    publishDecisionEvent(
                        RegionFlagDenyEvent(
                            context = context,
                            key = key,
                            binding = handlerResult.binding,
                            reason = evaluation.reason
                        )
                    )
                    return evaluation
                }
                is FlagEvaluation.Modify -> modifications.putAll(evaluation.metadata)
                FlagEvaluation.Allow -> {
                    allow = true
                    allowBinding = handlerResult.binding
                }
                FlagEvaluation.Pass -> Unit
            }
        }
        return when {
            modifications.isNotEmpty() -> FlagEvaluation.Modify(modifications)
            allow -> {
                allowBinding?.let {
                    publishDecisionEvent(
                        RegionFlagAllowEvent(
                            context = context,
                            key = key,
                            binding = it
                        )
                    )
                }
                FlagEvaluation.Allow
            }
            else -> FlagEvaluation.pass()
        }
    }

    /** Clears cache entries for the supplied region and its descendants. */
    fun invalidate(regionId: String) {
        cascadeInvalidate(regionId, mutableSetOf())
    }

    /** Clears the entire cache and notifies listeners. */
    fun invalidateAll() {
        bindingCache.clear()
        updateFlow.tryEmit(FlagUpdateEvent.All)
    }

    private fun bindingsFor(region: RegionModel): Map<RegionFlagKey, List<ResolvedFlagBinding>> {
        return bindingCache.computeIfAbsent(region.id) { computeBindings(region) }
    }

    private fun computeBindings(region: RegionModel): Map<RegionFlagKey, List<ResolvedFlagBinding>> {
        val lineage = mutableListOf<RegionModel>()
        val visited = mutableSetOf<String>()
        var current: RegionModel? = region
        while (current != null && visited.add(current.id)) {
            lineage.add(current)
            current = current.parentId?.let { regionRepository.findById(it) }
        }
        val resolved = LinkedHashMap<RegionFlagKey, MutableList<ResolvedFlagBinding>>()
        for (model in lineage) {
            if (model.definition.flags.isEmpty()) continue
            for (binding in model.definition.flags) {
                val list = resolved.getOrPut(binding.key) { mutableListOf() }
                list.add(ResolvedFlagBinding(model.id, model.priority, binding))
            }
        }
        return resolved.mapValues { (_, value) ->
            value.sortedWith(
                compareByDescending<ResolvedFlagBinding> { it.priority }
                    .thenBy { it.regionId }
            )
        }
    }

    private fun cascadeInvalidate(regionId: String, visited: MutableSet<String>) {
        if (!visited.add(regionId)) return
        bindingCache.remove(regionId)
        regionRepository.findById(regionId)?.children?.forEach { child ->
            cascadeInvalidate(child, visited)
        }
        updateFlow.tryEmit(FlagUpdateEvent.Region(regionId))
    }

    private fun publishDecisionEvent(event: Event) {
        try {
            Bukkit.getPluginManager().callEvent(event)
        } catch (ignored: IllegalStateException) {
            logger.trace("Skipping {} publication: {}", event.eventName, ignored.message)
        } catch (ignored: NullPointerException) {
            logger.trace("Skipping {} publication: {}", event.eventName, ignored.message)
        }
    }

    companion object {
        @Volatile
        private var instance: FlagEvaluationService? = null

        /**
         * Allows external callers (e.g. RegionRepository) to trigger a cache flush without
         * re-entering Koin resolution and causing a circular dependency during startup.
         */
        fun invalidateAllIfReady() {
            instance?.invalidateAll()
        }
    }
}

internal data class ResolvedFlagBinding(
    val regionId: String,
    val priority: Int,
    val binding: FlagBinding,
)

/**
 * Events emitted when the cached binding graph changes.
 */
sealed interface FlagUpdateEvent {
    data object All : FlagUpdateEvent
    data class Region(val regionId: String) : FlagUpdateEvent
}

