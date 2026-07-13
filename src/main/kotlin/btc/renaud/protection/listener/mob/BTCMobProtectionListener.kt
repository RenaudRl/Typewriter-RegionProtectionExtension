package btc.renaud.protection.listener.mob

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.slf4j.LoggerFactory

@Singleton
class BTCMobProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("BTCMobProtectionListener")

    // Cached reflection handles for performance — resolved once and reused.
    private val registryMethod by lazy { btcMobsApi?.javaClass?.getMethod("getRegistry") }
    private val isActiveMobMethod by lazy {
        registryMethod?.let {
            val registry = it.invoke(btcMobsApi)
            registry?.javaClass?.getMethod("isActiveMob", LivingEntity::class.java)
        }
    }

    private val btcMobsApi: Any? by lazy {
        try {
            val registration = Bukkit.getServicesManager().getRegistration(
                Class.forName("com.borntocraft.btcmobs.api.BTCMobsApi")
            )
            registration?.provider
        } catch (e: Exception) {
            logger.debug("BTCMobs API not available: {}", e.message)
            null
        }
    }

    private val isBTCMobsAvailable: Boolean get() = btcMobsApi != null

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (!isBTCMobsAvailable) return
        val entity = event.entity
        if (!isBTCMobEntity(entity)) return

        val (evaluation, context) = evaluateFlag(
            key = RegionFlagKey.BTCMOB_SPAWNING,
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
                    actionExecutor.handleDenied(context, RegionFlagKey.BTCMOB_SPAWNING, evaluation)
                    logger.debug(
                        "Cancelled BTCMob spawn of {} at {} due to btcmob-spawning flag in region {}",
                        entity.type,
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
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!isBTCMobsAvailable) return

        val damager = event.damager
        val victim = event.entity

        // BTCMob damaging a player
        if (victim is Player && isBTCMobEntity(damager)) {
            checkBTCMobDamage(event, damager, victim)
        }

        // Player damaging a BTCMob
        if (damager is Player && isBTCMobEntity(victim)) {
            checkPlayerDamageBTCMob(event, damager, victim as LivingEntity)
        }
    }

    private fun checkBTCMobDamage(event: EntityDamageByEntityEvent, damager: org.bukkit.entity.Entity, victim: Player) {
        val (evaluation, context) = evaluateFlag(
            key = RegionFlagKey.BTCMOB_DAMAGE,
            event = event,
            location = victim.location,
            player = victim,
            source = damager,
            target = victim
        )

        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) {
                    actionExecutor.handleDenied(context, RegionFlagKey.BTCMOB_DAMAGE, evaluation)
                    logger.debug(
                        "Cancelled BTCMob damage to {} in region {}",
                        victim.name,
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

    private fun checkPlayerDamageBTCMob(event: EntityDamageByEntityEvent, damager: Player, victim: LivingEntity) {
        val (evaluation, context) = evaluateFlag(
            key = RegionFlagKey.BTCMOB_DAMAGE,
            event = event,
            location = victim.location,
            player = damager,
            source = damager,
            target = victim
        )

        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) {
                    actionExecutor.handleDenied(context, RegionFlagKey.BTCMOB_DAMAGE, evaluation)
                    logger.debug(
                        "Cancelled player damage to BTCMob {} in region {}",
                        victim.type,
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

    private fun isBTCMobEntity(entity: org.bukkit.entity.Entity): Boolean {
        if (entity !is LivingEntity) return false
        try {
            val api = btcMobsApi ?: return false
            val regMethod = registryMethod ?: return false
            val registry = regMethod.invoke(api) ?: return false
            val activeMobMethod = isActiveMobMethod ?: return false
            return activeMobMethod.invoke(registry, entity) as? Boolean ?: false
        } catch (e: Exception) {
            logger.trace("Failed to check BTCMob entity: {}", e.message)
            return false
        }
    }
}
