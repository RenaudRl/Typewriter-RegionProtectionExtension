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
        val (evaluation, context) = evaluateFlag(RegionFlagKey.PVP, event, attacker.location, attacker, source = attacker, target = event.entity)
        return when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.PVP, evaluation)
                false
            }
            is FlagEvaluation.Modify -> {
                applyDamageModification(event, evaluation)
                if (context != null) actionExecutor.applyModifications(context, evaluation)
                true
            }
            else -> true
        }
    }

    private fun handleVictim(event: EntityDamageByEntityEvent, victim: Player) {
        val (evaluation, context) = evaluateFlag(RegionFlagKey.MOB_DAMAGE, event, victim.location, victim, source = event.damager, target = victim)
        
        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.MOB_DAMAGE, evaluation)
            }
            is FlagEvaluation.Modify -> {
                applyDamageModification(event, evaluation)
                if (context != null) actionExecutor.applyModifications(context, evaluation)
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

