package com.typewritermc.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.slf4j.LoggerFactory

@Singleton
class PlayerStateProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("PlayerStateProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFoodChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val region = dominantRegion(player.location) ?: return
        val context = createContext(region, event, player.location, player)

        // HUNGER flag: Deny means disable hunger loss.
        // So if new level < old level (loss), cancel.
        if (event.foodLevel < player.foodLevel) {
            when (actionExecutor.evaluate(context, RegionFlagKey.HUNGER)) {
                is FlagEvaluation.Denied -> event.isCancelled = true
                else -> {}
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val region = dominantRegion(player.location) ?: return
        val context = createContext(region, event, player.location, player)

        when (actionExecutor.evaluate(context, RegionFlagKey.KEEP_INVENTORY)) {
            is FlagEvaluation.Allow, is FlagEvaluation.Modify -> {
                event.keepInventory = true
                event.drops.clear()
            }
            else -> {}
        }

        when (actionExecutor.evaluate(context, RegionFlagKey.KEEP_EXP)) {
             is FlagEvaluation.Allow, is FlagEvaluation.Modify -> {
                 event.keepLevel = true
                 event.droppedExp = 0
             }
             else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val region = dominantRegion(event.player.location) ?: return
        val context = createContext(region, event, event.player.location, event.player)

        when (actionExecutor.evaluate(context, RegionFlagKey.ITEM_DURABILITY)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    // Note: Chat flags handling is complex because of formatting.
    // Usually handled by a Chat Renderer or simply prepending to the format.
    // For now, simple implementation if using AsyncChatEvent.
}
