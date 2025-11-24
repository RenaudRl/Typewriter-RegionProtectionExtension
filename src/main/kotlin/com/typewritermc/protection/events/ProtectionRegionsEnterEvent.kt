package com.typewritermc.protection.events

import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.service.storage.RegionModel
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

class ProtectionRegionsEnterEvent(
    player: Player,
    val regions: Set<RegionModel>,
) : PlayerEvent(player) {

    operator fun contains(region: RegionDefinitionEntry): Boolean {
        val targetId = region.id
        if (targetId.isBlank()) return false
        return contains(targetId)
    }

    operator fun contains(regionId: String): Boolean {
        if (regionId.isBlank()) return false
        return regions.any { it.id == regionId }
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        val HANDLER_LIST: HandlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

