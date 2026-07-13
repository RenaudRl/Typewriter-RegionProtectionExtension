package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.slf4j.LoggerFactory

@Singleton
class PlayerPropertiesProtectionListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("PlayerPropertiesProtectionListener")

    companion object {
        private const val DEFAULT_WALK_SPEED = 0.2f
        private const val DEFAULT_FLY_SPEED = 0.1f
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPotionEffect(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        if (event.action != EntityPotionEffectEvent.Action.ADDED &&
            event.action != EntityPotionEffectEvent.Action.CHANGED
        ) return

        val newEffect = event.newEffect ?: return
        val (evaluation, _) = evaluateFlag(
            RegionFlagKey.BLOCKED_EFFECTS,
            event,
            player.location,
            player,
            source = player
        )

        if (evaluation is FlagEvaluation.Modify) {
            val blockedList = evaluation.metadata[RegionFlagKey.BLOCKED_EFFECTS.id] as? List<*>
            val effectId = newEffect.type.key.toString()
            if (blockedList?.any { it.toString().equals(effectId, ignoreCase = true) } == true) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleFlight(event: PlayerToggleFlightEvent) {
        if (!event.isFlying) return

        val player = event.player
        val (evaluation, context) = evaluateFlag(
            RegionFlagKey.FLY,
            event,
            player.location,
            player,
            source = player
        )

        when (evaluation) {
            is FlagEvaluation.Denied -> {
                event.isCancelled = true
                if (context != null) actionExecutor.handleDenied(context, RegionFlagKey.FLY, evaluation)
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return
        applySpeedFlags(event.player, to)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        applySpeedFlags(event.player, event.player.location)
    }

    private fun applySpeedFlags(player: Player, location: org.bukkit.Location) {
        val regions = regionsAt(location)
        if (regions.isEmpty()) return

        applyWalkSpeed(player, regions)
        applyFlySpeed(player, regions)
        enforceFlyFlag(player, regions)
    }

    private fun applyWalkSpeed(player: Player, regions: List<RegionModel>) {
        for (region in regions) {
            val binding = region.flags.firstOrNull { it.key == RegionFlagKey.WALK_SPEED } ?: continue
            val value = binding.value as? FlagValue.DoubleValue ?: continue
            val speed = value.value.toFloat().coerceIn(-1f, 1f)
            if (speed >= 0f) {
                player.walkSpeed = speed
            }
            return
        }
        player.walkSpeed = DEFAULT_WALK_SPEED
    }

    private fun applyFlySpeed(player: Player, regions: List<RegionModel>) {
        for (region in regions) {
            val binding = region.flags.firstOrNull { it.key == RegionFlagKey.FLY_SPEED } ?: continue
            val value = binding.value as? FlagValue.DoubleValue ?: continue
            val speed = value.value.toFloat().coerceIn(-1f, 1f)
            if (speed >= 0f) {
                player.flySpeed = speed
            }
            return
        }
        player.flySpeed = DEFAULT_FLY_SPEED
    }

    private fun enforceFlyFlag(player: Player, regions: List<RegionModel>) {
        for (region in regions) {
            val binding = region.flags.firstOrNull { it.key == RegionFlagKey.FLY } ?: continue
            val value = binding.value as? FlagValue.Boolean ?: continue
            if (!value.enabled) {
                player.isFlying = false
                player.allowFlight = false
                return
            }
            return
        }
    }
}
