package com.typewritermc.protection.flags

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

private fun currentThreadAsync(): Boolean = runCatching { !Bukkit.isPrimaryThread() }.getOrDefault(true)

/**
 * Fired when a region flag grants an action after evaluation.
 */
class RegionFlagAllowEvent(
    val context: FlagEvaluationContext,
    val key: RegionFlagKey,
    val binding: TypedFlagBinding<*>,
) : Event(currentThreadAsync()) {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}

/**
 * Fired when a region flag blocks an action during evaluation.
 */
class RegionFlagDenyEvent(
    val context: FlagEvaluationContext,
    val key: RegionFlagKey,
    val binding: TypedFlagBinding<*>,
    val reason: String?,
) : Event(currentThreadAsync()) {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}

