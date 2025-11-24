package com.typewritermc.protection.listener.environment

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.listener.FlagContext
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.entity.Creeper
import org.bukkit.entity.DragonFireball
import org.bukkit.entity.Entity
import org.bukkit.entity.Fireball
import org.bukkit.entity.SmallFireball
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.WitherSkull
import org.bukkit.entity.minecart.ExplosiveMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ExplosionPrimeEvent
import org.slf4j.LoggerFactory

@Singleton
class ExplosionProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("ExplosionProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val flag = resolveFlag(event.entity)
        if (flag == null) {
            logger.debug("Ignoring explosion from {} at {} (no matching flag)", event.entity?.type, event.location)
            return
        }
        val region = dominantRegion(event.location)
        if (region == null) {
            logger.debug("No region matched explosion at {}", event.location)
            return
        }
        val context = createContext(region, event, event.location, source = event.entity)
        handleExplosion(context, flag) {
            event.isCancelled = true
            event.blockList().clear()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        val region = dominantRegion(event.block.location)
        if (region == null) {
            logger.debug("No region matched block explosion at {}", event.block.location)
            return
        }
        val context = createContext(region, event, event.block.location)
        handleExplosion(context, RegionFlagKey.TNT) {
            event.isCancelled = true
            event.blockList().clear()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPrime(event: ExplosionPrimeEvent) {
        val flag = resolveFlag(event.entity)
        if (flag == null) {
            logger.debug("Ignoring explosion prime from {} at {} (no matching flag)", event.entity?.type, event.entity?.location)
            return
        }
        val region = dominantRegion(event.entity?.location)
        if (region == null) {
            logger.debug("No region matched priming entity {} at {}", event.entity?.type, event.entity?.location)
            return
        }
        val context = createContext(region, event, event.entity?.location, source = event.entity)
        handleExplosion(context, flag) {
            event.isCancelled = true
        }
    }

    private fun handleExplosion(context: FlagContext, flag: RegionFlagKey, cancel: () -> Unit) {
        when (val result = actionExecutor.evaluate(context, flag)) {
            is FlagEvaluation.Denied -> {
                cancel()
                actionExecutor.handleDenied(context, flag, result)
            }
            is FlagEvaluation.Modify -> actionExecutor.applyModifications(context, result)
            else -> Unit
        }
    }

    private fun resolveFlag(entity: Entity?): RegionFlagKey? {
        return when (entity) {
            is Creeper -> RegionFlagKey.CREEPER_EXPLOSION
            is TNTPrimed, is ExplosiveMinecart -> RegionFlagKey.TNT
            is Fireball, is SmallFireball, is DragonFireball, is WitherSkull -> RegionFlagKey.GHAST_FIREBALL
            else -> null
        }
    }
}

