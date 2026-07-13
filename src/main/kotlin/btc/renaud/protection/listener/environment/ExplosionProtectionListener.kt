package btc.renaud.protection.listener.environment

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.listener.ProtectionListenerFlagContext
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.entity.BreezeWindCharge
import org.bukkit.entity.Creeper
import org.bukkit.entity.DragonFireball
import org.bukkit.entity.Entity
import org.bukkit.entity.Fireball
import org.bukkit.entity.SmallFireball
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.WindCharge
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
            logger.debug("Ignoring explosion from {} at {} (no matching flag)", event.entity.type, event.location)
            return
        }

        val (evaluation, context) = evaluateFlag(flag, event, event.location, source = event.entity)
        handleExplosion(evaluation, context, flag) {
            event.isCancelled = true
            event.blockList().clear()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        val flag = when (event.block.type) {
            org.bukkit.Material.TNT, org.bukkit.Material.TNT_MINECART -> RegionFlagKey.TNT
            org.bukkit.Material.RESPAWN_ANCHOR -> RegionFlagKey.TNT // Reuse TNT flag for respawn anchors
            else -> {
                logger.debug("Ignoring block explosion from {} at {} (unhandled block type)", event.block.type, event.block.location)
                return
            }
        }
        val (evaluation, context) = evaluateFlag(flag, event, event.block.location)
        handleExplosion(evaluation, context, flag) {
            event.isCancelled = true
            event.blockList().clear()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPrime(event: ExplosionPrimeEvent) {
        val flag = resolveFlag(event.entity)
        if (flag == null) {
            logger.debug("Ignoring explosion prime from {} at {} (no matching flag)", event.entity.type, event.entity.location)
            return
        }
        
        val (evaluation, context) = evaluateFlag(flag, event, event.entity.location, source = event.entity)
        handleExplosion(evaluation, context, flag) {
            event.isCancelled = true
        }
    }

    private fun handleExplosion(
        evaluation: FlagEvaluation,
        context: ProtectionListenerFlagContext?,
        flag: RegionFlagKey,
        cancel: () -> Unit
    ) {
        when (evaluation) {
            is FlagEvaluation.Denied -> {
                cancel()
                if (context != null) actionExecutor.handleDenied(context, flag, evaluation)
            }
            is FlagEvaluation.Modify -> {
                if (context != null) actionExecutor.applyModifications(context, evaluation)
            }
            else -> Unit
        }
    }

    private fun resolveFlag(entity: Entity?): RegionFlagKey? {
        return when (entity) {
            is Creeper -> RegionFlagKey.CREEPER_EXPLOSION
            is TNTPrimed, is ExplosiveMinecart -> RegionFlagKey.TNT
            is Fireball, is SmallFireball, is DragonFireball, is WitherSkull -> RegionFlagKey.GHAST_FIREBALL
            is WindCharge, is BreezeWindCharge -> RegionFlagKey.GHAST_FIREBALL
            else -> null
        }
    }
}

