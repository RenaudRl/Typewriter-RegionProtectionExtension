package btcrenaud.protection.listener.mob

import com.typewritermc.core.extension.annotations.Singleton
import btcrenaud.protection.flags.FlagEvaluation
import btcrenaud.protection.flags.RegionFlagKey
import btcrenaud.protection.listener.AbstractProtectionListener
import btcrenaud.protection.listener.FlagActionExecutor
import btcrenaud.protection.service.storage.RegionRepository
import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

@Singleton
class MythicMobsSpawnProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("MythicMobsSpawnProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onMythicMobPreSpawn(event: MythicMobPreSpawnEvent) {
        val (evaluation, context) = evaluateFlag(
            key = RegionFlagKey.MOB_SPAWNING,
            event = event,
            location = event.location
        ) {
            runtimeData["mob.spawn.reason"] = event.spawnReason.name
        }

        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) {
                    actionExecutor.handleDenied(context, RegionFlagKey.MOB_SPAWNING, evaluation)
                    logger.debug(
                        "Cancelled MythicMob pre-spawn at {} due to mob-spawning flag in region {}",
                        event.location,
                        context.region.id,
                    )
                }
            }
            is FlagEvaluation.Modify -> {
                if (context != null) {
                    actionExecutor.applyModifications(context, evaluation)
                }
            }
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onMythicMobSpawn(event: MythicMobSpawnEvent) {
        val entity = event.entity as? LivingEntity
        val (evaluation, context) = evaluateFlag(
            key = RegionFlagKey.MOB_SPAWNING,
            event = event,
            location = event.location,
            source = entity,
            target = entity
        ) {
            runtimeData["mob.spawn.reason"] = event.spawnReason.name
        }

        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) {
                    actionExecutor.handleDenied(context, RegionFlagKey.MOB_SPAWNING, evaluation)
                    logger.debug(
                        "Cancelled MythicMob spawn of {} at {} due to mob-spawning flag in region {}",
                        entity?.type,
                        event.location,
                        context.region.id,
                    )
                }
            }
            is FlagEvaluation.Modify -> {
                if (context != null) {
                    actionExecutor.applyModifications(context, evaluation)
                }
            }
            else -> Unit
        }
    }
}
