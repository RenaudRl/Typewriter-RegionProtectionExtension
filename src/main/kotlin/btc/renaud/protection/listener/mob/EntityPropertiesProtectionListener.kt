package btc.renaud.protection.listener.mob

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
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
        val (evaluation, _) = evaluateFlag(RegionFlagKey.VEHICLE_DESTROY, event, event.vehicle.location, attacker, source = attacker, target = event.vehicle)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakByEntityEvent) {
        val remover = event.remover as? Player ?: return
        val (evaluation, _) = evaluateFlag(RegionFlagKey.ENTITY_PAINTING_DESTROY, event, event.entity.location, remover, source = remover, target = event.entity)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEndermanGrief(event: EntityChangeBlockEvent) {
        if (event.entity !is Enderman) return
        val (evaluation, _) = evaluateFlag(RegionFlagKey.ENDERMAN_GRIEF, event, event.block.location, source = event.entity)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEnderPearl(event: PlayerTeleportEvent) {
        if (event.cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return
        val (evaluation, _) = evaluateFlag(RegionFlagKey.ENDER_PEARL, event, event.to, event.player)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity

        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.FALL_DAMAGE, event, entity.location, entity as? Player, target = entity)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
            }
        }

        if (entity is Player) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.INVINCIBLE, event, entity.location, entity, target = entity)
            when (evaluation) {
                is FlagEvaluation.Allow, is FlagEvaluation.Modify -> event.isCancelled = true
                else -> {}
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPotionSplash(event: PotionSplashEvent) {
        // Checking location of potion entity as proxy for affected area
        val (evaluation, _) = evaluateFlag(RegionFlagKey.POTION_SPLASH, event, event.entity.location, source = event.entity)
        if (evaluation is FlagEvaluation.Denied) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val (evaluation, _) = evaluateFlag(RegionFlagKey.EXP_DROPS, event, event.entity.location, target = event.entity)
        if (evaluation is FlagEvaluation.Denied) {
            event.droppedExp = 0
        }
    }
}
