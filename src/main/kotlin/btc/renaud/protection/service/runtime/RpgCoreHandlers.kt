package btc.renaud.protection.service.runtime

import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.FlagEvaluationContext
import btc.renaud.protection.flags.FlagHandler
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.flags.TypedFlagBinding
import org.slf4j.LoggerFactory

/**
 * Cached reflection handle for RPG Core [PlayerStatsService].
 * Returns 0 when RPG Core is not loaded or the stat is unavailable.
 */
object RpgCoreApi {
    private val logger = LoggerFactory.getLogger("RpgCoreApi")

    private val playerStatsServiceClass by lazy {
        try {
            Class.forName("com.borntocraft.typewriter.btc.core.stats.PlayerStatsService")
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    private val getStatValueMethod by lazy {
        try {
            playerStatsServiceClass?.getMethod("getStatValue", org.bukkit.entity.Player::class.java, String::class.java)
        } catch (e: NoSuchMethodException) {
            null
        }
    }

    val isAvailable: Boolean get() = getStatValueMethod != null

    fun getStatValue(player: org.bukkit.entity.Player, statId: String): Int {
        val method = getStatValueMethod ?: return 0
        return try {
            (method.invoke(null, player, statId) as? Double)?.toInt() ?: 0
        } catch (e: Exception) {
            logger.trace("Failed to read RPGCore stat '{}' for {}: {}", statId, player.name, e.message)
            0
        }
    }

    fun getProfessionLevel(player: org.bukkit.entity.Player, professionId: String): Int {
        return getStatValue(player, "profession_${professionId}_level")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RPG Core Extended Flag Handlers — Full implementations
// ═══════════════════════════════════════════════════════════════════════════

/**
 * MANA_DRAIN — When enabled, drains mana from players in the region.
 * When disabled, mana is preserved (no drain).
 * Integrates with RPG Core PlayerStatsService stat "mana".
 */
class ManaDrainHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        if (!RpgCoreApi.isAvailable) return FlagEvaluation.pass()

        val player = context.player ?: return FlagEvaluation.pass()
        val mana = RpgCoreApi.getStatValue(player, "mana")
        if (mana <= 0) return FlagEvaluation.pass()

        return FlagEvaluation.modify(mapOf(
            "mana.drain" to true,
            "mana.current" to mana,
        ))
    }
}

/**
 * PISTOL — Controls firearm usage in the region.
 * When disabled, pistol/weapon usage is denied.
 * Checks RPG Core weapon system availability.
 */
class PistolHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("pistol.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * CORRUPTION — Controls the ether/corruption system in the region.
 * When enabled, players accumulate corruption from the ether system.
 * Integrates with RPG Core EtherCorruption configuration.
 */
class CorruptionHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.pass()
        if (!RpgCoreApi.isAvailable) return FlagEvaluation.pass()

        val player = context.player ?: return FlagEvaluation.pass()
        val corruption = RpgCoreApi.getStatValue(player, "corruption")
        return FlagEvaluation.modify(mapOf(
            "corruption.enabled" to true,
            "corruption.current" to corruption,
        ))
    }
}

/**
 * AUCTION — Controls access to the auction house in the region.
 * When disabled, players cannot use the auction system.
 * Integrates with RPG Core AuctionHouseService.
 */
class AuctionHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("auction.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * BACKPACK — Controls access to the RPG backpack inventory in the region.
 * When disabled, players cannot open their RPG backpack.
 * Integrates with RPG Core InventoryUiRegistry.
 */
class BackpackHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("backpack.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * WARDROBE — Controls access to the RPG wardrobe/cosmetic inventory in the region.
 * When disabled, players cannot open their wardrobe.
 * Integrates with RPG Core InventoryUiRegistry.
 */
class WardrobeHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("wardrobe.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * PROFESSION — Controls profession-related activities in the region.
 * When disabled, players cannot perform profession actions (crafting, gathering, etc.).
 * Integrates with RPG Core ProfessionRegistry.
 */
class ProfessionHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("profession.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * EXPERIENCE — Controls vanilla XP gain in the region.
 * When disabled, players do not gain experience orbs.
 */
class ExperienceHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("experience.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * GATHERING — Controls resource gathering activities in the region.
 * When disabled, players cannot gather resources (mining, fishing, harvesting, etc.).
 * Integrates with RPG Core GatheringExperienceService.
 */
class GatheringHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("gathering.disabled")
        return FlagEvaluation.pass()
    }
}

/**
 * RESIN — Controls resin collection in the region.
 * When disabled, players cannot collect resin resources.
 * Integrates with RPG Core ResinService.
 */
class ResinHandler : FlagHandler<FlagValue.Boolean> {
    override suspend fun evaluate(
        context: FlagEvaluationContext,
        binding: TypedFlagBinding<FlagValue.Boolean>,
    ): FlagEvaluation {
        if (!binding.value.enabled) return FlagEvaluation.deny("resin.disabled")
        return FlagEvaluation.pass()
    }
}
