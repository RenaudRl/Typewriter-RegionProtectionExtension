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
        
        // HUNGER flag: Deny means disable hunger loss.
        // So if new level < old level (loss), cancel.
        if (event.foodLevel < player.foodLevel) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.HUNGER, event, player.location, player)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val (invEvaluation, _) = evaluateFlag(RegionFlagKey.KEEP_INVENTORY, event, player.location, player)

        when (invEvaluation) {
            is FlagEvaluation.Allow, is FlagEvaluation.Modify -> {
                event.keepInventory = true
                event.drops.clear()
            }
            else -> {}
        }

        val (expEvaluation, _) = evaluateFlag(RegionFlagKey.KEEP_EXP, event, player.location, player)
        when (expEvaluation) {
             is FlagEvaluation.Allow, is FlagEvaluation.Modify -> {
                 event.keepLevel = true
                 event.droppedExp = 0
             }
             else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val (evaluation, _) = evaluateFlag(RegionFlagKey.ITEM_DURABILITY, event, event.player.location, event.player)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
        }
    }

    // Note: Chat flags handling is complex because of formatting.
    // Usually handled by a Chat Renderer or simply prepending to the format.
    // For now, simple implementation if using AsyncChatEvent.
}
