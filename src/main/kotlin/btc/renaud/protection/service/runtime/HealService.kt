package btc.renaud.protection.service.runtime

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.point.Position
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.FlagEvaluationContext
import btc.renaud.protection.flags.FlagHandler
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.flags.TypedFlagBinding
import btc.renaud.protection.selection.toBukkitLocation
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Active heal system — pulses health regeneration based on region flags.
 * Folia-safe: uses GlobalRegionScheduler for the tick loop.
 */
@Singleton
class HealService(
    private val plugin: Plugin,
    private val regionRepository: RegionRepository,
) : Listener {
    private val logger = LoggerFactory.getLogger("HealService")
    private val lastDamageTime = ConcurrentHashMap<UUID, Long>()
    private var task: io.papermc.paper.threadedregions.scheduler.ScheduledTask? = null

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _ ->
            Bukkit.getOnlinePlayers().forEach { player -> processHeal(player) }
        }, 1L, 20L)
        logger.info("HealService started")
    }

    fun stop() {
        task?.cancel()
        task = null
        logger.info("HealService stopped")
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity is Player) {
            lastDamageTime[event.entity.uniqueId] = System.currentTimeMillis()
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastDamageTime.remove(event.player.uniqueId)
    }

    private fun processHeal(player: Player) {
        if (player.isDead || !player.isOnline) return
        val currentHealth = player.health
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        if (currentHealth >= maxHealth) return

        val regions = regionRepository.regionsAt(
            com.typewritermc.core.utils.point.Position(
                com.typewritermc.core.utils.point.World(player.world.name),
                player.location.x,
                player.location.y,
                player.location.z
            )
        )
        if (regions.isEmpty()) return

        for (region in regions) {
            val healAmount = getDoubleFlag(region, RegionFlagKey.HEAL_AMOUNT) ?: continue
            val healDelay = getIntFlag(region, RegionFlagKey.HEAL_DELAY) ?: 20
            val healMin = getDoubleFlag(region, RegionFlagKey.HEAL_MIN) ?: 0.0
            val healMax = getDoubleFlag(region, RegionFlagKey.HEAL_MAX) ?: maxHealth

            if (healAmount <= 0) continue

            // Check combat timeout (5 seconds default)
            val lastDamage = lastDamageTime[player.uniqueId] ?: 0L
            if (System.currentTimeMillis() - lastDamage < 5000) continue

            val newHealth = (currentHealth + healAmount).coerceIn(healMin, healMax)
            if (newHealth > currentHealth) {
                player.health = newHealth
                return
            }
        }
    }

    private fun getDoubleFlag(region: RegionModel, key: RegionFlagKey): Double? {
        val binding = region.flags.firstOrNull { it.key == key } ?: return null
        return when (val v = binding.value) {
            is FlagValue.DoubleValue -> v.value
            is FlagValue.IntValue -> v.value.toDouble()
            else -> null
        }
    }

    private fun getIntFlag(region: RegionModel, key: RegionFlagKey): Int? {
        val binding = region.flags.firstOrNull { it.key == key } ?: return null
        return when (val v = binding.value) {
            is FlagValue.IntValue -> v.value
            is FlagValue.DoubleValue -> v.value.toInt()
            else -> null
        }
    }
}

// ─── Flag Handlers ───

/**
 * Handler for HEAL_AMOUNT — returns the heal amount as metadata.
 */
class HealAmountHandler : FlagHandler<FlagValue.IntValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.IntValue>,
    ): FlagEvaluation {
        val amount = binding.value.value
        if (amount <= 0) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("heal.amount" to amount))
    }
}

/**
 * Handler for HEAL_DELAY — returns the delay in ticks as metadata.
 */
class HealDelayHandler : FlagHandler<FlagValue.IntValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.IntValue>,
    ): FlagEvaluation {
        val delay = binding.value.value.coerceAtLeast(1)
        return FlagEvaluation.modify(mapOf("heal.delay" to delay))
    }
}

/**
 * Handler for HEAL_MIN — minimum health threshold.
 */
class HealMinHandler : FlagHandler<FlagValue.DoubleValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.DoubleValue>,
    ): FlagEvaluation {
        val min = binding.value.value
        if (min <= 0) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("heal.min" to min))
    }
}

/**
 * Handler for HEAL_MAX — maximum health cap.
 */
class HealMaxHandler : FlagHandler<FlagValue.DoubleValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.DoubleValue>,
    ): FlagEvaluation {
        val max = binding.value.value
        if (max <= 0) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("heal.max" to max))
    }
}

/**
 * Handler for TELEPORT — teleports the player to the defined position.
 */
class TeleportFlagHandler : FlagHandler<FlagValue.LocationValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.LocationValue>,
    ): FlagEvaluation {
        val player = context.player ?: return FlagEvaluation.pass()
        val target = binding.value.position
        val location = target.toBukkitLocation()
        player.teleportAsync(location)
        return FlagEvaluation.Allow
    }
}

/**
 * Handler for CHAT_PREFIX — stores prefix in runtime data for ChatPrefixSuffixListener.
 */
class ChatPrefixHandler : FlagHandler<FlagValue.Text> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Text>,
    ): FlagEvaluation {
        val prefix = binding.value.content
        if (prefix.isBlank()) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("chat.prefix" to prefix))
    }
}

/**
 * Handler for CHAT_SUFFIX — stores suffix in runtime data.
 */
class ChatSuffixHandler : FlagHandler<FlagValue.Text> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Text>,
    ): FlagEvaluation {
        val suffix = binding.value.content
        if (suffix.isBlank()) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("chat.suffix" to suffix))
    }
}

/**
 * Handler for ITEM_DURABILITY — prevents durability loss for items used in region.
 */
class ItemDurabilityHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("item.durability.protect" to true))
    }
}

/**
 * Handler for GLIDE — controls elytra gliding.
 * Values: "allow" (force enable), "deny" (force disable), "default" (no change).
 */
class GlideHandler : FlagHandler<FlagValue.Text> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Text>,
    ): FlagEvaluation {
        return when (binding.value.content.lowercase()) {
            "allow" -> FlagEvaluation.modify(mapOf("glide.force" to true))
            "deny" -> FlagEvaluation.modify(mapOf("glide.force" to false))
            else -> FlagEvaluation.pass()
        }
    }
}

/**
 * Handler for CHUNK_UNLOAD — keeps chunks loaded permanently.
 */
class ChunkUnloadHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("chunk.keep_loaded" to true))
    }
}

/**
 * Handler for FROSTWALKER — controls frost walker ice formation.
 */
class FrostWalkerHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("frostwalker.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for JOIN_LOCATION — sets spawn location when player is in region.
 */
class JoinLocationHandler : FlagHandler<FlagValue.LocationValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.LocationValue>,
    ): FlagEvaluation {
        val player = context.player ?: return FlagEvaluation.pass()
        val target = binding.value.position
        val location = target.toBukkitLocation()
        player.teleportAsync(location)
        return FlagEvaluation.Allow
    }
}

/**
 * Handler for ENTRY_MIN_LEVEL — minimum vanilla XP level to enter.
 */
class EntryMinLevelHandler : FlagHandler<FlagValue.Text> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Text>,
    ): FlagEvaluation {
        val level = binding.value.content.toIntOrNull() ?: return FlagEvaluation.pass()
        val player = context.player ?: return FlagEvaluation.pass()
        if (player.level < level) {
            return FlagEvaluation.deny("entry.min_level.required")
        }
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for ENTRY_MAX_LEVEL — maximum vanilla XP level to enter.
 */
class EntryMaxLevelHandler : FlagHandler<FlagValue.Text> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Text>,
    ): FlagEvaluation {
        val level = binding.value.content.toIntOrNull() ?: return FlagEvaluation.pass()
        val player = context.player ?: return FlagEvaluation.pass()
        if (player.level > level) {
            return FlagEvaluation.deny("entry.max_level.exceeded")
        }
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for PERMIT_COMPLETELY — items allowed despite BUILD deny.
 */
class PermitCompletelyHandler : FlagHandler<FlagValue.ListValue> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.ListValue>,
    ): FlagEvaluation {
        val items = binding.value.entries
        if (items.isEmpty()) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("permit.completely" to items))
    }
}

/**
 * Handler for WORLD_EDIT — blocks WorldEdit usage.
 */
class WorldEditHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("worldedit.block" to true))
    }
}

/**
 * Handler for MOBTRAP_USE — controls MobTrap usage.
 */
class MobTrapHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("mobtrap.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for MANA_REGEN — controls mana regeneration.
 */
class ManaRegenHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        return if (binding.value.enabled) {
            FlagEvaluation.modify(mapOf("mana.regen" to true))
        } else {
            FlagEvaluation.modify(mapOf("mana.regen" to false))
        }
    }
}

/**
 * Handler for DOUBLE_DROP — controls double drops.
 */
class DoubleDropHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("double_drop.enabled" to true))
    }
}

/**
 * Handler for GRAPPLING_HOOK — controls grappling hook usage.
 */
class GrapplingHookHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("grappling.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for FISHING — controls fishing.
 */
class FishingHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("fishing.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for ALCHEMY — controls custom alchemy.
 */
class AlchemyHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("alchemy.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for ENCHANTING_OVERRIDE — controls custom enchanting.
 */
class EnchantingOverrideHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("enchanting.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * Handler for ELYTRA_AUTO_SWITCH — controls elytra auto-switch.
 */
class ElytraAutoSwitchHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        return if (binding.value.enabled) {
            FlagEvaluation.modify(mapOf("elytra.auto_switch" to true))
        } else {
            FlagEvaluation.pass()
        }
    }
}

/**
 * Handler for GROUND_ITEM_GLOW — controls ground item glow.
 */
class GroundItemGlowHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf("ground_item.glow" to true))
    }
}
