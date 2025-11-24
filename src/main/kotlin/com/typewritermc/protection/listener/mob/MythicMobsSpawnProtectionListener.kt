package com.typewritermc.protection.listener.mob

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
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
        val location = event.location
        val region = dominantRegion(location)
        if (region == null) {
            logger.trace("No region matched MythicMob pre-spawn at {}", location)
            return
        }

        val context = createContext(
            region = region,
            event = event,
            location = location,
        )
        context.runtimeData["mob.spawn.reason"] = event.spawnReason.name

        when (val evaluation = actionExecutor.evaluate(context, RegionFlagKey.MOB_SPAWNING)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.MOB_SPAWNING, evaluation)
                logger.debug(
                    "Cancelled MythicMob pre-spawn at {} due to mob-spawning flag in region {}",
                    location,
                    region.id,
                )
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, evaluation)
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onMythicMobSpawn(event: MythicMobSpawnEvent) {
        val location = event.location
        val region = dominantRegion(location)
        if (region == null) {
            logger.trace("No region matched MythicMob spawn at {}", location)
            return
        }

        val entity = event.entity as? LivingEntity
        val context = createContext(
            region = region,
            event = event,
            location = location,
            source = entity,
            target = entity,
        )
        context.runtimeData["mob.spawn.reason"] = event.spawnReason.name

        when (val evaluation = actionExecutor.evaluate(context, RegionFlagKey.MOB_SPAWNING)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.MOB_SPAWNING, evaluation)
                logger.debug(
                    "Cancelled MythicMob spawn of {} at {} due to mob-spawning flag in region {}",
                    entity?.type,
                    location,
                    region.id,
                )
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, evaluation)
            else -> Unit
        }
    }
}

