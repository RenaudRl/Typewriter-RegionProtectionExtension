package btcrenaud.protection.listener.combat

import com.typewritermc.core.extension.annotations.Singleton
import btcrenaud.protection.flags.FlagEvaluation
import btcrenaud.protection.flags.RegionFlagKey
import btcrenaud.protection.listener.AbstractProtectionListener
import btcrenaud.protection.listener.FlagActionExecutor
import btcrenaud.protection.service.storage.RegionRepository
import org.bukkit.entity.Animals
import org.bukkit.entity.Entity
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.minecart.ExplosiveMinecart
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
        val victim = event.entity
        val attacker = event.damager

        // Check for PVP (Player vs Player)
        if (victim is org.bukkit.entity.Player && attacker is org.bukkit.entity.Player) {
            handleAttacker(event, attacker)
            return
        }

        // Check for DAMAGE_ANIMALS
        if (victim is Animals && attacker is org.bukkit.entity.Player) {
            val (evaluation, context) = evaluateFlag(RegionFlagKey.DAMAGE_ANIMALS, event, victim.location, attacker, source = attacker, target = victim)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.DAMAGE_ANIMALS, evaluation)
                return
            }
        }

        // Check for TNT_DAMAGE
        if (attacker is TNTPrimed || attacker is ExplosiveMinecart) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.TNT_DAMAGE, event, victim.location)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                return
            }
        }

        // Check for FIREWORK_DAMAGE
        if (attacker is Firework) {
            val (evaluation, _) = evaluateFlag(RegionFlagKey.FIREWORK_DAMAGE, event, victim.location)
            if (evaluation is FlagEvaluation.Denied) {
                event.isCancelled = true
                return
            }
        }

        // Check for general MOB_DAMAGE if victim is a player
        if (victim is org.bukkit.entity.Player) {
            handleVictim(event, victim)
        }
    }

    private fun handleAttacker(event: EntityDamageByEntityEvent, attacker: Player): Boolean {
        val victim = event.entity
        val (evaluation, context) = evaluateFlag(RegionFlagKey.PVP, event, victim.location, attacker, source = attacker, target = victim)
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

