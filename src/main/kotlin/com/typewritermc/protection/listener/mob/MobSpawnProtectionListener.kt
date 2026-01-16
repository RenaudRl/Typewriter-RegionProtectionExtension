package com.typewritermc.protection.listener.mob

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.slf4j.LoggerFactory

@Singleton
class MobSpawnProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("MobSpawnProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val (evaluation, context) = evaluateFlag(
            key = RegionFlagKey.MOB_SPAWNING,
            event = event,
            location = event.location,
            source = event.entity,
            target = event.entity
        ) {
            runtimeData["mob.spawn.reason"] = event.spawnReason.name
        }

        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) {
                    actionExecutor.handleDenied(context, RegionFlagKey.MOB_SPAWNING, evaluation)
                    logger.debug(
                        "Cancelled {} spawn at {} due to mob-spawning flag in region {}",
                        event.entity.type,
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

