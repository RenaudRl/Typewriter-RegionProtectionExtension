package com.typewritermc.protection.listener.combat

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.AbstractProtectionListener
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.slf4j.LoggerFactory

@Singleton
class CombatProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("CombatProtectionListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player
        if (attacker != null && !handleAttacker(event, attacker)) {
            return
        }
        val victimPlayer = event.entity as? Player ?: return
        handleVictim(event, victimPlayer)
    }

    private fun handleAttacker(event: EntityDamageByEntityEvent, attacker: Player): Boolean {
        val region = dominantRegion(attacker.location)
        if (region == null) {
            logger.debug("No region matched attacker {} at {}", attacker.name, attacker.location)
            return true
        }
        val context = createContext(region, event, attacker.location, attacker, source = attacker, target = event.entity)
        return when (val result = actionExecutor.evaluate(context, RegionFlagKey.PVP)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.PVP, result)
                false
            }
            is FlagEvaluation.Modify -> {
                applyDamageModification(event, result)
                actionExecutor.applyModifications(context, result)
                true
            }
            else -> true
        }
    }

    private fun handleVictim(event: EntityDamageByEntityEvent, victim: Player) {
        val region = dominantRegion(victim.location)
        if (region == null) {
            logger.debug("No region matched victim {} at {}", victim.name, victim.location)
            return
        }
        val context = createContext(region, event, victim.location, victim, source = event.damager, target = victim)
        when (val result = actionExecutor.evaluate(context, RegionFlagKey.MOB_DAMAGE)) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                actionExecutor.handleDenied(context, RegionFlagKey.MOB_DAMAGE, result)
            }
            is FlagEvaluation.Modify -> {
                applyDamageModification(event, result)
                actionExecutor.applyModifications(context, result)
            }
            else -> Unit
        }
    }

    private fun applyDamageModification(event: EntityDamageByEntityEvent, result: FlagEvaluation.Modify) {
        val absolute = (result.metadata["damage.amount"] as? Number)?.toDouble()
        val multiplier = (result.metadata["damage.multiplier"] as? Number)?.toDouble()
        when {
            absolute != null -> event.damage = absolute
            multiplier != null -> event.damage = event.damage * multiplier
        }
    }
}

