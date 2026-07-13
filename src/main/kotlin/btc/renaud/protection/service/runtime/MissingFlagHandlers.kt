package btc.renaud.protection.service.runtime

import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.FlagEvaluationContext
import btc.renaud.protection.flags.FlagHandler
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.flags.TypedFlagBinding
import org.bukkit.potion.PotionEffectType
import org.slf4j.LoggerFactory

// ═══════════════════════════════════════════════════════════════════════════
// Handlers for previously missing flags
// ═══════════════════════════════════════════════════════════════════════════

/**
 * WALK_SPEED — Modifies player walk speed based on region flag value.
 * Value is a double multiplier (1.0 = normal, 0.5 = half, 2.0 = double).
 * Applies via Attribute.GENERIC_MOVEMENT_SPEED.
 */
class WalkSpeedHandler : FlagHandler<FlagValue.DoubleValue> {
    private val logger = LoggerFactory.getLogger("WalkSpeedHandler")

    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.DoubleValue>,
    ): FlagEvaluation {
        val player = context.player ?: return FlagEvaluation.pass()
        val speed = binding.value.value.coerceIn(0.0, 10.0)
        return FlagEvaluation.modify(mapOf(
            "walk.speed" to speed,
            "walk.player" to player.uniqueId.toString(),
        ))
    }
}

/**
 * FLY_SPEED — Modifies player fly speed based on region flag value.
 * Value is a double multiplier (1.0 = normal, 0.5 = half, 2.0 = double).
 */
class FlySpeedHandler : FlagHandler<FlagValue.DoubleValue> {
    private val logger = LoggerFactory.getLogger("FlySpeedHandler")

    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.DoubleValue>,
    ): FlagEvaluation {
        val player = context.player ?: return FlagEvaluation.pass()
        val speed = binding.value.value.coerceIn(0.0, 10.0)
        return FlagEvaluation.modify(mapOf(
            "fly.speed" to speed,
            "fly.player" to player.uniqueId.toString(),
        ))
    }
}

/**
 * BLOCKED_EFFECTS — Prevents specified potion effects from being applied.
 * Value is a list of PotionEffectType names (e.g. ["BLINDNESS", "NAUSEA"]).
 * When a player in the region would receive a blocked effect, it is cancelled.
 */
class BlockedEffectsHandler : FlagHandler<FlagValue.ListValue> {
    private val logger = LoggerFactory.getLogger("BlockedEffectsHandler")

    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.ListValue>,
    ): FlagEvaluation {
        val entries = binding.value.entries
        if (entries.isEmpty()) return FlagEvaluation.pass()

        val player = context.player ?: return FlagEvaluation.pass()
        val blockedTypes = entries.mapNotNull { name ->
            try {
                PotionEffectType.getByName(name.uppercase())
            } catch (_: Exception) {
                logger.warn("Unknown potion effect type '{}' in BLOCKED_EFFECTS", name)
                null
            }
        }

        if (blockedTypes.isEmpty()) return FlagEvaluation.pass()

        return FlagEvaluation.modify(mapOf(
            "blocked_effects.types" to blockedTypes,
            "blocked_effects.player" to player.uniqueId.toString(),
        ))
    }
}

/**
 * BIOME — Overrides the biome for players in the region.
 * Value is a biome key string (e.g. "minecraft:plains").
 * Note: Actual biome override requires client-side chunk resend.
 * This handler emits metadata for consumers (e.g. listeners) to apply.
 */
class BiomeHandler : FlagHandler<FlagValue.Text> {
    private val logger = LoggerFactory.getLogger("BiomeHandler")

    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Text>,
    ): FlagEvaluation {
        val biomeKey = binding.value.content
        if (biomeKey.isBlank()) return FlagEvaluation.pass()

        val player = context.player ?: return FlagEvaluation.pass()
        return FlagEvaluation.modify(mapOf(
            "biome.override" to biomeKey,
            "biome.player" to player.uniqueId.toString(),
        ))
    }
}
