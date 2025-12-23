package com.typewritermc.protection.listener.mob

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.entity.Enderman
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.slf4j.LoggerFactory

@Singleton
class EntityPropertiesProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("EntityPropertiesProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val attacker = event.attacker as? Player ?: return
        val region = dominantRegion(event.vehicle.location) ?: return
        val context = createContext(region, event, event.vehicle.location, attacker, source = attacker, target = event.vehicle)

        when (actionExecutor.evaluate(context, RegionFlagKey.VEHICLE_DESTROY)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakByEntityEvent) {
        val remover = event.remover as? Player ?: return
        val region = dominantRegion(event.entity.location) ?: return
        val context = createContext(region, event, event.entity.location, remover, source = remover, target = event.entity)

        when (actionExecutor.evaluate(context, RegionFlagKey.ENTITY_PAINTING_DESTROY)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEndermanGrief(event: EntityChangeBlockEvent) {
        if (event.entity !is Enderman) return
        val region = dominantRegion(event.block.location) ?: return
        val context = createContext(region, event, event.block.location, source = event.entity)

        when (actionExecutor.evaluate(context, RegionFlagKey.ENDERMAN_GRIEF)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEnderPearl(event: PlayerTeleportEvent) {
        if (event.cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return
        val region = dominantRegion(event.to) ?: return
        val context = createContext(region, event, event.to, event.player)

        when (actionExecutor.evaluate(context, RegionFlagKey.ENDER_PEARL)) {
            is FlagEvaluation.Denied -> event.isCancelled = true
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        val region = dominantRegion(entity.location) ?: return
        val context = createContext(region, event, entity.location, entity as? Player, target = entity)

        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            when (actionExecutor.evaluate(context, RegionFlagKey.FALL_DAMAGE)) {
                is FlagEvaluation.Denied -> event.isCancelled = true
                else -> {}
            }
        }

        if (entity is Player) {
            when (actionExecutor.evaluate(context, RegionFlagKey.INVINCIBLE)) {
                is FlagEvaluation.Allow, is FlagEvaluation.Modify -> event.isCancelled = true
                else -> {}
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPotionSplash(event: PotionSplashEvent) {
        // This is tricky as multiple entities are affected. 
        // We usually check where the potion lands or the affected entities. 
        // For simplicity, checking where the potion entity is.
        val region = dominantRegion(event.entity.location) ?: return
        val context = createContext(region, event, event.entity.location, source = event.entity)
        
        when (actionExecutor.evaluate(context, RegionFlagKey.POTION_SPLASH)) {
             is FlagEvaluation.Denied -> event.isCancelled = true
             else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val region = dominantRegion(event.entity.location) ?: return
        // No explicit player involved necessarily, but context is region
        val context = createContext(region, event, event.entity.location, target = event.entity)

        when (actionExecutor.evaluate(context, RegionFlagKey.EXP_DROPS)) {
            is FlagEvaluation.Denied -> event.droppedExp = 0
            else -> {}
        }
    }
}
