package com.typewritermc.protection.listener

import com.typewritermc.protection.flags.FlagEvaluationContext
import com.typewritermc.protection.service.storage.RegionModel
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event

/**
 * Context passed to protection listeners while evaluating a flag.
 */
data class FlagContext(
    val region: RegionModel,
    val event: Event,
    val location: Location? = null,
    val player: Player? = null,
    val source: Entity? = null,
    val target: Entity? = null,
    val runtimeData: MutableMap<String, Any?> = mutableMapOf(),
) {
    fun toEvaluationContext(): FlagEvaluationContext = FlagEvaluationContext(this)

    fun canBypass(): Boolean {
        val actor = player ?: return false
        val id = actor.uniqueId.toString()
        return region.owners.contains(id) ||
            region.members.contains(id) ||
            region.owners.contains(actor.name) ||
            region.members.contains(actor.name)
    }

}

