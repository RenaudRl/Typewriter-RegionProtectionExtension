package com.typewritermc.protection.flags

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.listener.FlagContext as ListenerFlagContext
import com.typewritermc.protection.service.storage.RegionModel
import org.bukkit.event.Event
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.safeCast

/**
 * Central registry mapping [RegionFlagKey] to metadata and handlers.
 * The registry is populated on startup and consumed by [FlagEvaluationService].
 */
@Singleton
class RegionFlagRegistry {
    private val logger = LoggerFactory.getLogger("RegionFlagRegistry")
    private val definitions = buildDefinitions()
    private val handlers = ConcurrentHashMap<RegionFlagKey, HandlerEntry<out FlagValue>>()

    init {
        registerDefaults()
    }

    fun definition(key: RegionFlagKey): RegionFlagDefinition? = definitions[key]

    internal fun handlerEntry(key: RegionFlagKey): HandlerEntry<out FlagValue>? = handlers[key]

    inline fun <reified T : FlagValue> registerHandler(key: RegionFlagKey, handler: FlagHandler<T>) {
        registerHandler(key, T::class, handler)
    }

    fun <T : FlagValue> registerHandler(key: RegionFlagKey, type: KClass<T>, handler: FlagHandler<T>) {
        val definition = definitions[key]
        if (definition == null) {
            logger.warn("Attempted to register handler for unknown flag {}", key)
            return
        }
        if (!definition.valueType.java.isAssignableFrom(type.java)) {
            logger.warn(
                "Handler type {} is incompatible with {} (expected {})",
                type.simpleName,
                key.id,
                definition.valueType.simpleName
            )
        }
        handlers[key] = HandlerEntry(type, handler)
    }

    private fun registerDefaults() {
        val denyWhenFalse = FlagHandler<FlagValue.Boolean> { _, binding ->
            if (binding.value.enabled) FlagEvaluation.pass() else FlagEvaluation.deny("${binding.key.id}.denied")
        }
        listOf(
            RegionFlagKey.BUILD,
            RegionFlagKey.BLOCK_BREAK,
            RegionFlagKey.BLOCK_PLACE,
            RegionFlagKey.USE,
            RegionFlagKey.INTERACT,
            RegionFlagKey.ITEM_PICKUP,
            RegionFlagKey.ITEM_DROP,
            RegionFlagKey.ENTRY,
            RegionFlagKey.EXIT,
            RegionFlagKey.MOB_DAMAGE,
            RegionFlagKey.CREEPER_EXPLOSION,
            RegionFlagKey.TNT,
            RegionFlagKey.FIRE_SPREAD,
            RegionFlagKey.WORLD_EDIT
        ).forEach { registerHandler(it, denyWhenFalse) }

        registerHandler(RegionFlagKey.PVP, denyWhenFalse)

        val playerActionsHandler = FlagHandler<FlagValue.Actions> { _, binding ->
            val actions = binding.value.actions.filter { it.isSet }
            if (actions.isEmpty()) {
                FlagEvaluation.pass()
            } else {
                FlagEvaluation.modify(mapOf("actions.player.${binding.sourceRegionId}" to actions))
            }
        }

        registerHandler(RegionFlagKey.ENTRY_ACTION, playerActionsHandler)
        registerHandler(RegionFlagKey.EXIT_ACTION, playerActionsHandler)
    }

    companion object {
        internal fun buildDefinitions(): Map<RegionFlagKey, RegionFlagDefinition> {
        fun booleanDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            priority: FlagEvaluationPriority = FlagEvaluationPriority.DEFAULT,
            inheritance: FlagInheritance = FlagInheritance.ALWAYS,
            compatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.BOOLEAN,
            category = category,
            valueType = FlagValue.Boolean::class,
            evaluationPriority = priority,
            inheritance = inheritance,
            paperCompatibility = compatibility,
        )

        fun textDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            compatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
            allowedValues: List<String> = emptyList(),
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.STRING,
            category = category,
            valueType = FlagValue.Text::class,
            paperCompatibility = compatibility,
            allowedValues = allowedValues,
        )

        fun actionsDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            compatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.ACTIONS,
            category = category,
            valueType = FlagValue.Actions::class,
            paperCompatibility = compatibility,
        )

        fun listDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            compatibility: PaperCompatibility = PaperCompatibility.EXPERIMENTAL,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.LIST,
            category = category,
            valueType = FlagValue.ListValue::class,
            paperCompatibility = compatibility,
        )

        fun locationDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            priority: FlagEvaluationPriority = FlagEvaluationPriority.DEFAULT,
            inheritance: FlagInheritance = FlagInheritance.OVERRIDE_ONLY,
            compatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.LOCATION,
            category = category,
            valueType = FlagValue.LocationValue::class,
            evaluationPriority = priority,
            inheritance = inheritance,
            paperCompatibility = compatibility,
        )

        fun intDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            compatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.INTEGER,
            category = category,
            valueType = FlagValue.IntValue::class,
            paperCompatibility = compatibility,
        )

        fun doubleDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            compatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.DOUBLE,
            category = category,
            valueType = FlagValue.DoubleValue::class,
            paperCompatibility = compatibility,
        )

        fun soundDefinition(
            key: RegionFlagKey,
            description: String,
            category: RegionFlagCategory,
            compatibility: PaperCompatibility = PaperCompatibility.EXPERIMENTAL,
        ) = RegionFlagDefinition(
            key = key,
            description = description,
            valueKind = FlagValueKind.SOUND,
            category = category,
            valueType = FlagValue.SoundValue::class,
            paperCompatibility = compatibility,
        )

        val definitions = mapOf(
            RegionFlagKey.BUILD to booleanDefinition(
                RegionFlagKey.BUILD,
                "Allow players to build",
                RegionFlagCategory.GENERAL,
                FlagEvaluationPriority.HIGH,
            ),
            RegionFlagKey.BLOCK_BREAK to booleanDefinition(
                RegionFlagKey.BLOCK_BREAK,
                "Controls block breaking",
                RegionFlagCategory.BLOCKS,
                FlagEvaluationPriority.HIGH,
            ),
            RegionFlagKey.BLOCK_PLACE to booleanDefinition(
                RegionFlagKey.BLOCK_PLACE,
                "Controls block placement",
                RegionFlagCategory.BLOCKS,
                FlagEvaluationPriority.HIGH,
            ),
            RegionFlagKey.USE to booleanDefinition(
                RegionFlagKey.USE,
                "Allow lever, button and container use",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.INTERACT to booleanDefinition(
                RegionFlagKey.INTERACT,
                "Controls right click interactions",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.PVP to booleanDefinition(
                RegionFlagKey.PVP,
                "Allow player damage",
                RegionFlagCategory.COMBAT,
                FlagEvaluationPriority.CRITICAL,
            ),
            RegionFlagKey.MOB_DAMAGE to booleanDefinition(
                RegionFlagKey.MOB_DAMAGE,
                "Allow mob damage",
                RegionFlagCategory.COMBAT,
                FlagEvaluationPriority.HIGH,
            ),
            RegionFlagKey.MOB_SPAWNING to booleanDefinition(
                RegionFlagKey.MOB_SPAWNING,
                "Allow creature spawning",
                RegionFlagCategory.COMBAT,
            ),
            RegionFlagKey.CREEPER_EXPLOSION to booleanDefinition(
                RegionFlagKey.CREEPER_EXPLOSION,
                "Creeper explosions",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.TNT to booleanDefinition(
                RegionFlagKey.TNT,
                "TNT explosions",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.FIRE_SPREAD to booleanDefinition(
                RegionFlagKey.FIRE_SPREAD,
                "Fire spread",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.LIGHTNING to booleanDefinition(
                RegionFlagKey.LIGHTNING,
                "Lightning ignitions",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.ENDERMAN_GRIEF to booleanDefinition(
                RegionFlagKey.ENDERMAN_GRIEF,
                "Enderman block grief",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.GHAST_FIREBALL to booleanDefinition(
                RegionFlagKey.GHAST_FIREBALL,
                "Ghast fireball explosions",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.LAVA_FIRE to booleanDefinition(
                RegionFlagKey.LAVA_FIRE,
                "Lava igniting blocks",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.LAVA_FLOW to booleanDefinition(
                RegionFlagKey.LAVA_FLOW,
                "Lava fluid spread",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.WATER_FLOW to booleanDefinition(
                RegionFlagKey.WATER_FLOW,
                "Water fluid spread",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.ICE_MELT to booleanDefinition(
                RegionFlagKey.ICE_MELT,
                "Prevent ice melting",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.SNOW_MELT to booleanDefinition(
                RegionFlagKey.SNOW_MELT,
                "Prevent snow melting",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.LEAF_DECAY to booleanDefinition(
                RegionFlagKey.LEAF_DECAY,
                "Leaf decay",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.GRASS_GROWTH to booleanDefinition(
                RegionFlagKey.GRASS_GROWTH,
                "Grass and mycelium spread",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.VINE_GROWTH to booleanDefinition(
                RegionFlagKey.VINE_GROWTH,
                "Vine growth",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.ENTITY_PAINTING_DESTROY to booleanDefinition(
                RegionFlagKey.ENTITY_PAINTING_DESTROY,
                "Painting break protection",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.VEHICLE_PLACE to booleanDefinition(
                RegionFlagKey.VEHICLE_PLACE,
                "Vehicle placement",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.VEHICLE_DESTROY to booleanDefinition(
                RegionFlagKey.VEHICLE_DESTROY,
                "Vehicle destruction",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.ENDER_PEARL to booleanDefinition(
                RegionFlagKey.ENDER_PEARL,
                "Use of ender pearls",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.POTION_SPLASH to booleanDefinition(
                RegionFlagKey.POTION_SPLASH,
                "Area potion effects",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.EXP_DROPS to booleanDefinition(
                RegionFlagKey.EXP_DROPS,
                "Experience orb drops",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.ITEM_PICKUP to booleanDefinition(
                RegionFlagKey.ITEM_PICKUP,
                "Allow item pickup",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.ITEM_DROP to booleanDefinition(
                RegionFlagKey.ITEM_DROP,
                "Allow item drops",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.ENTRY to booleanDefinition(
                RegionFlagKey.ENTRY,
                "Allow entering region",
                RegionFlagCategory.MOVEMENT,
                FlagEvaluationPriority.CRITICAL,
            ),
            RegionFlagKey.EXIT to booleanDefinition(
                RegionFlagKey.EXIT,
                "Allow exiting region",
                RegionFlagCategory.MOVEMENT,
                FlagEvaluationPriority.CRITICAL,
            ),
            RegionFlagKey.ENTRY_ACTION to actionsDefinition(
                RegionFlagKey.ENTRY_ACTION,
                "Typewriter actions executed when entry is blocked",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.EXIT_ACTION to actionsDefinition(
                RegionFlagKey.EXIT_ACTION,
                "Typewriter actions executed when exit is blocked",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.PASS_THROUGH to booleanDefinition(
                RegionFlagKey.PASS_THROUGH,
                "Skip membership checks for child regions",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.INVINCIBLE to booleanDefinition(
                RegionFlagKey.INVINCIBLE,
                "Enable invincibility",
                RegionFlagCategory.COMBAT,
            ),
            RegionFlagKey.FALL_DAMAGE to booleanDefinition(
                RegionFlagKey.FALL_DAMAGE,
                "Control fall damage",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.HUNGER to booleanDefinition(
                RegionFlagKey.HUNGER,
                "Natural hunger drain",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.HEAL_AMOUNT to intDefinition(
                RegionFlagKey.HEAL_AMOUNT,
                "Amount of health restored per tick",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.HEAL_DELAY to intDefinition(
                RegionFlagKey.HEAL_DELAY,
                "Ticks between healing pulses",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.HEAL_MIN to doubleDefinition(
                RegionFlagKey.HEAL_MIN,
                "Minimum health threshold for healing",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.HEAL_MAX to doubleDefinition(
                RegionFlagKey.HEAL_MAX,
                "Maximum health cap during healing",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.TELEPORT to locationDefinition(
                RegionFlagKey.TELEPORT,
                "Teleport destination",
                RegionFlagCategory.MOVEMENT,
                FlagEvaluationPriority.HIGH,
            ),
            RegionFlagKey.BLOCKED_EFFECTS to listDefinition(
                RegionFlagKey.BLOCKED_EFFECTS,
                "Potion effect identifiers to remove",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.FLY to booleanDefinition(
                RegionFlagKey.FLY,
                "Allow flight",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.WALK_SPEED to doubleDefinition(
                RegionFlagKey.WALK_SPEED,
                "Custom walk speed",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.FLY_SPEED to doubleDefinition(
                RegionFlagKey.FLY_SPEED,
                "Custom fly speed",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.KEEP_INVENTORY to booleanDefinition(
                RegionFlagKey.KEEP_INVENTORY,
                "Keep inventory on death",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.KEEP_EXP to booleanDefinition(
                RegionFlagKey.KEEP_EXP,
                "Keep experience on death",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.CHAT_PREFIX to textDefinition(
                RegionFlagKey.CHAT_PREFIX,
                "Chat prefix applied inside the region",
                RegionFlagCategory.CHAT,
            ),
            RegionFlagKey.CHAT_SUFFIX to textDefinition(
                RegionFlagKey.CHAT_SUFFIX,
                "Chat suffix applied inside the region",
                RegionFlagCategory.CHAT,
            ),
            RegionFlagKey.GODMODE to booleanDefinition(
                RegionFlagKey.GODMODE,
                "Toggle god mode",
                RegionFlagCategory.COMBAT,
            ),
            RegionFlagKey.FROSTWALKER to booleanDefinition(
                RegionFlagKey.FROSTWALKER,
                "Allow frost walker ice",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.NETHER_PORTALS to booleanDefinition(
                RegionFlagKey.NETHER_PORTALS,
                "Allow portal creation",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.GLIDE to textDefinition(
                RegionFlagKey.GLIDE,
                "Force or block elytra gliding",
                RegionFlagCategory.MOVEMENT,
                PaperCompatibility.EXPERIMENTAL,
                listOf("allow", "deny", "default"),
            ),
            RegionFlagKey.CHUNK_UNLOAD to booleanDefinition(
                RegionFlagKey.CHUNK_UNLOAD,
                "Keep chunks loaded",
                RegionFlagCategory.MISC,
                compatibility = PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.ITEM_DURABILITY to booleanDefinition(
                RegionFlagKey.ITEM_DURABILITY,
                "Prevent durability loss",
                RegionFlagCategory.INVENTORY,
                compatibility = PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.JOIN_LOCATION to locationDefinition(
                RegionFlagKey.JOIN_LOCATION,
                "Login spawn location",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.ENTRY_MIN_LEVEL to textDefinition(
                RegionFlagKey.ENTRY_MIN_LEVEL,
                "Minimum PlaceholderAPI level required to enter",
                RegionFlagCategory.MOVEMENT,
                PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.ENTRY_MAX_LEVEL to textDefinition(
                RegionFlagKey.ENTRY_MAX_LEVEL,
                "Maximum PlaceholderAPI level allowed to enter",
                RegionFlagCategory.MOVEMENT,
                PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.PERMIT_COMPLETELY to listDefinition(
                RegionFlagKey.PERMIT_COMPLETELY,
                "Items fully permitted despite other restrictions",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.WORLD_EDIT to booleanDefinition(
                RegionFlagKey.WORLD_EDIT,
                "Allow WorldEdit",
                RegionFlagCategory.MISC,
                inheritance = FlagInheritance.OVERRIDE_ONLY,
            ),
        )

            return RegionFlagKey.entries.associateWith { key ->
                definitions[key] ?: RegionFlagDefinition(
                    key = key,
                    description = "Reserved flag with no implementation yet",
                    valueKind = FlagValueKind.STRING,
                    category = RegionFlagCategory.MISC,
                    valueType = FlagValue.Text::class,
                    paperCompatibility = PaperCompatibility.EXPERIMENTAL,
                )
            }
        }
    }
}

/**
 * Contract for resolving the outcome of a flag binding. Implementations should be side-effect free and
 * rely on the supplied [FlagEvaluationContext].
 */
fun interface FlagHandler<T : FlagValue> {
    suspend fun evaluate(context: FlagEvaluationContext, binding: TypedFlagBinding<T>): FlagEvaluation
}

/**
 * Contextual data supplied to handlers and evaluation services while checking a flag.
 *
 * The [flag] snapshot is cheap to copy thanks to the pooling infrastructure exposed by [FlagContextPool].
 */
data class FlagEvaluationContext(
    val flag: ListenerFlagContext,
) {
    val region: RegionModel get() = flag.region
    val event: Event get() = flag.event
    val runtimeData: MutableMap<String, Any?> get() = flag.runtimeData
    val player get() = flag.player
    val location get() = flag.location
}

/** Represents the possible outcomes for a flag evaluation. */
sealed interface FlagEvaluation {
    data object Allow : FlagEvaluation
    data object Pass : FlagEvaluation
    data class Denied(val reason: String? = null) : FlagEvaluation
    data class Modify(val metadata: Map<String, Any?> = emptyMap()) : FlagEvaluation

    companion object {
        fun pass(): FlagEvaluation = Pass
        fun deny(reason: String? = null): FlagEvaluation = Denied(reason)
        fun modify(metadata: Map<String, Any?> = emptyMap()): FlagEvaluation = Modify(metadata)
    }
}

/**
 * Wrapper passed to handlers exposing the resolved value together with provenance information.
 */
data class TypedFlagBinding<T : FlagValue>(
    val key: RegionFlagKey,
    val value: T,
    val sourceRegionId: String,
    val priority: Int,
    val definition: RegionFlagDefinition?,
    val original: FlagBinding,
)

internal data class HandlerResult(
    val evaluation: FlagEvaluation,
    val binding: TypedFlagBinding<out FlagValue>,
)

internal class HandlerEntry<T : FlagValue>(
    private val type: KClass<T>,
    private val handler: FlagHandler<T>,
) {
    suspend fun evaluate(
        context: FlagEvaluationContext,
        key: RegionFlagKey,
        resolved: ResolvedFlagBinding,
        definition: RegionFlagDefinition?,
    ): HandlerResult? {
        val value = type.safeCast(resolved.binding.value) ?: return null
        val typedBinding = TypedFlagBinding(
            key = key,
            value = value,
            sourceRegionId = resolved.regionId,
            priority = resolved.priority,
            definition = definition,
            original = resolved.binding,
        )
        val evaluation = handler.evaluate(context, typedBinding)
        return HandlerResult(evaluation, typedBinding)
    }
}

