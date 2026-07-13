package btc.renaud.protection.flags

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.runtime.*
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
        validateDefinitions()
    }

    private fun validateDefinitions() {
        val missing = RegionFlagKey.entries.filter { !definitions.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn(
                "The following {} flags have no definition in buildDefinitions(): {}",
                missing.size,
                missing.joinToString { it.id }
            )
        }
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
            // General
            RegionFlagKey.BUILD,
            RegionFlagKey.PASS_THROUGH,
            RegionFlagKey.HUNGER,
            // Blocks
            RegionFlagKey.BLOCK_BREAK,
            RegionFlagKey.BLOCK_PLACE,
            RegionFlagKey.CREEPER_EXPLOSION,
            RegionFlagKey.TNT,
            RegionFlagKey.FIRE_SPREAD,
            RegionFlagKey.LIGHTNING,
            RegionFlagKey.ENDERMAN_GRIEF,
            RegionFlagKey.GHAST_FIREBALL,
            RegionFlagKey.LAVA_FIRE,
            RegionFlagKey.LAVA_FLOW,
            RegionFlagKey.WATER_FLOW,
            RegionFlagKey.ICE_MELT,
            RegionFlagKey.SNOW_MELT,
            RegionFlagKey.LEAF_DECAY,
            RegionFlagKey.GRASS_GROWTH,
            RegionFlagKey.VINE_GROWTH,
            RegionFlagKey.ENTITY_PAINTING_DESTROY,
            RegionFlagKey.LIGHTER,
            RegionFlagKey.PISTONS,
            RegionFlagKey.SNOW_FALL,
            RegionFlagKey.ICE_FORM,
            RegionFlagKey.RAVAGER_GRIEF,
            RegionFlagKey.CROP_TRAMPLING,
            RegionFlagKey.FROSTWALKER,
            RegionFlagKey.NETHER_PORTALS,
            // Combat
            RegionFlagKey.PVP,
            RegionFlagKey.MOB_DAMAGE,
            RegionFlagKey.MOB_SPAWNING,
            RegionFlagKey.INVINCIBLE,
            RegionFlagKey.GODMODE,
            RegionFlagKey.DAMAGE_ANIMALS,
            RegionFlagKey.TNT_DAMAGE,
            RegionFlagKey.FIREWORK_DAMAGE,
            // Movement
            RegionFlagKey.ENTRY,
            RegionFlagKey.EXIT,
            RegionFlagKey.ENDER_PEARL,
            RegionFlagKey.FALL_DAMAGE,
            RegionFlagKey.FLY,
            // Inventory
            RegionFlagKey.EXP_DROPS,
            RegionFlagKey.ITEM_PICKUP,
            RegionFlagKey.ITEM_DROP,
            RegionFlagKey.KEEP_INVENTORY,
            RegionFlagKey.KEEP_EXP,
            RegionFlagKey.CHEST_ACCESS,
            // Chat
            RegionFlagKey.SEND_CHAT,
            RegionFlagKey.RECEIVE_CHAT,
            // Misc
            RegionFlagKey.USE,
            RegionFlagKey.INTERACT,
            RegionFlagKey.VEHICLE_PLACE,
            RegionFlagKey.VEHICLE_DESTROY,
            RegionFlagKey.POTION_SPLASH,
            RegionFlagKey.CHUNK_UNLOAD,
            RegionFlagKey.WORLD_EDIT,
            RegionFlagKey.MOBTRAP_USE,
            RegionFlagKey.SLEEP,
            RegionFlagKey.BTCMOB_SPAWNING,
            RegionFlagKey.BTCMOB_DAMAGE
        ).forEach { registerHandler(it, denyWhenFalse) }

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

        val listModifyHandler = FlagHandler<FlagValue.ListValue> { _, binding ->
            val entries = binding.value.entries
            if (entries.isEmpty()) {
                FlagEvaluation.pass()
            } else {
                FlagEvaluation.modify(mapOf(binding.key.id to entries))
            }
        }
        registerHandler(RegionFlagKey.BLOCKED_CMDS, listModifyHandler)
        registerHandler(RegionFlagKey.ALLOWED_CMDS, listModifyHandler)

        // ─── Custom handlers for typed flags ───
        registerHandler(RegionFlagKey.HEAL_AMOUNT, HealAmountHandler())
        registerHandler(RegionFlagKey.HEAL_DELAY, HealDelayHandler())
        registerHandler(RegionFlagKey.HEAL_MIN, HealMinHandler())
        registerHandler(RegionFlagKey.HEAL_MAX, HealMaxHandler())
        registerHandler(RegionFlagKey.TELEPORT, TeleportFlagHandler())
        registerHandler(RegionFlagKey.CHAT_PREFIX, ChatPrefixHandler())
        registerHandler(RegionFlagKey.CHAT_SUFFIX, ChatSuffixHandler())
        registerHandler(RegionFlagKey.ITEM_DURABILITY, ItemDurabilityHandler())
        registerHandler(RegionFlagKey.GLIDE, GlideHandler())
        registerHandler(RegionFlagKey.CHUNK_UNLOAD, ChunkUnloadHandler())
        registerHandler(RegionFlagKey.FROSTWALKER, FrostWalkerHandler())
        registerHandler(RegionFlagKey.JOIN_LOCATION, JoinLocationHandler())
        registerHandler(RegionFlagKey.ENTRY_MIN_LEVEL, EntryMinLevelHandler())
        registerHandler(RegionFlagKey.ENTRY_MAX_LEVEL, EntryMaxLevelHandler())
        registerHandler(RegionFlagKey.PERMIT_COMPLETELY, PermitCompletelyHandler())
        registerHandler(RegionFlagKey.WORLD_EDIT, WorldEditHandler())
        registerHandler(RegionFlagKey.MOBTRAP_USE, MobTrapHandler())
        registerHandler(RegionFlagKey.MANA_REGEN, ManaRegenHandler())
        registerHandler(RegionFlagKey.DOUBLE_DROP, DoubleDropHandler())
        registerHandler(RegionFlagKey.GRAPPLING_HOOK, GrapplingHookHandler())
        registerHandler(RegionFlagKey.FISHING, FishingHandler())
        registerHandler(RegionFlagKey.ALCHEMY, AlchemyHandler())
        registerHandler(RegionFlagKey.ENCHANTING_OVERRIDE, EnchantingOverrideHandler())
        registerHandler(RegionFlagKey.ELYTRA_AUTO_SWITCH, ElytraAutoSwitchHandler())
        registerHandler(RegionFlagKey.GROUND_ITEM_GLOW, GroundItemGlowHandler())

        // ─── RPG Core Extended handlers ───
        registerHandler(RegionFlagKey.MANA_DRAIN, ManaDrainHandler())
        registerHandler(RegionFlagKey.PISTOL, PistolHandler())
        registerHandler(RegionFlagKey.CORRUPTION, CorruptionHandler())
        registerHandler(RegionFlagKey.AUCTION, AuctionHandler())
        registerHandler(RegionFlagKey.BACKPACK, BackpackHandler())
        registerHandler(RegionFlagKey.WARDROBE, WardrobeHandler())
        registerHandler(RegionFlagKey.PROFESSION, ProfessionHandler())
        registerHandler(RegionFlagKey.EXPERIENCE, ExperienceHandler())
        registerHandler(RegionFlagKey.GATHERING, GatheringHandler())
        registerHandler(RegionFlagKey.RESIN, ResinHandler())

        // ─── Missing flag handlers ───
        registerHandler(RegionFlagKey.WALK_SPEED, WalkSpeedHandler())
        registerHandler(RegionFlagKey.FLY_SPEED, FlySpeedHandler())
        registerHandler(RegionFlagKey.BLOCKED_EFFECTS, BlockedEffectsHandler())
        registerHandler(RegionFlagKey.BIOME, BiomeHandler())
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
            // ─── Heal System ───
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
                "Teleport destination on region entry",
                RegionFlagCategory.MOVEMENT,
                FlagEvaluationPriority.HIGH,
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
            RegionFlagKey.GLIDE to textDefinition(
                RegionFlagKey.GLIDE,
                "Force or block elytra gliding (allow/deny/default)",
                RegionFlagCategory.MOVEMENT,
                PaperCompatibility.EXPERIMENTAL,
                listOf("allow", "deny", "default"),
            ),
            RegionFlagKey.CHUNK_UNLOAD to booleanDefinition(
                RegionFlagKey.CHUNK_UNLOAD,
                "Keep chunks loaded permanently",
                RegionFlagCategory.MISC,
                compatibility = PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.JOIN_LOCATION to locationDefinition(
                RegionFlagKey.JOIN_LOCATION,
                "Login spawn location when inside region",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.ENTRY_MIN_LEVEL to textDefinition(
                RegionFlagKey.ENTRY_MIN_LEVEL,
                "Minimum vanilla XP level required to enter",
                RegionFlagCategory.MOVEMENT,
                PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.ENTRY_MAX_LEVEL to textDefinition(
                RegionFlagKey.ENTRY_MAX_LEVEL,
                "Maximum vanilla XP level allowed to enter",
                RegionFlagCategory.MOVEMENT,
                PaperCompatibility.EXPERIMENTAL,
            ),
            RegionFlagKey.PERMIT_COMPLETELY to listDefinition(
                RegionFlagKey.PERMIT_COMPLETELY,
                "Material names fully permitted despite other restrictions",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.WORLD_EDIT to booleanDefinition(
                RegionFlagKey.WORLD_EDIT,
                "Allow WorldEdit commands and API",
                RegionFlagCategory.MISC,
                inheritance = FlagInheritance.OVERRIDE_ONLY,
            ),
            RegionFlagKey.MOBTRAP_USE to booleanDefinition(
                RegionFlagKey.MOBTRAP_USE,
                "Allow MobTrap capturing and throwing",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.FROSTWALKER to booleanDefinition(
                RegionFlagKey.FROSTWALKER,
                "Allow frost walker ice formation",
                RegionFlagCategory.BLOCKS,
            ),
            RegionFlagKey.MANA_REGEN to booleanDefinition(
                RegionFlagKey.MANA_REGEN,
                "Enable mana regeneration in region",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.DOUBLE_DROP to booleanDefinition(
                RegionFlagKey.DOUBLE_DROP,
                "Enable double drops in region",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.GRAPPLING_HOOK to booleanDefinition(
                RegionFlagKey.GRAPPLING_HOOK,
                "Allow grappling hook usage in region",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.FISHING to booleanDefinition(
                RegionFlagKey.FISHING,
                "Allow fishing in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.ALCHEMY to booleanDefinition(
                RegionFlagKey.ALCHEMY,
                "Allow custom alchemy in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.ENCHANTING_OVERRIDE to booleanDefinition(
                RegionFlagKey.ENCHANTING_OVERRIDE,
                "Allow custom enchanting in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.ELYTRA_AUTO_SWITCH to booleanDefinition(
                RegionFlagKey.ELYTRA_AUTO_SWITCH,
                "Enable elytra auto-switch in region",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.GROUND_ITEM_GLOW to booleanDefinition(
                RegionFlagKey.GROUND_ITEM_GLOW,
                "Make ground items glow in region (client-side)",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.MANA_DRAIN to booleanDefinition(
                RegionFlagKey.MANA_DRAIN,
                "Enable mana drain in region",
                RegionFlagCategory.GENERAL,
            ),
            RegionFlagKey.PISTOL to booleanDefinition(
                RegionFlagKey.PISTOL,
                "Allow pistol usage in region",
                RegionFlagCategory.COMBAT,
            ),
            RegionFlagKey.CORRUPTION to booleanDefinition(
                RegionFlagKey.CORRUPTION,
                "Enable corruption system in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.AUCTION to booleanDefinition(
                RegionFlagKey.AUCTION,
                "Allow auction house usage in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.BACKPACK to booleanDefinition(
                RegionFlagKey.BACKPACK,
                "Allow backpack access in region",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.WARDROBE to booleanDefinition(
                RegionFlagKey.WARDROBE,
                "Allow wardrobe access in region",
                RegionFlagCategory.INVENTORY,
            ),
            RegionFlagKey.PROFESSION to booleanDefinition(
                RegionFlagKey.PROFESSION,
                "Allow profession activities in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.EXPERIENCE to booleanDefinition(
                RegionFlagKey.EXPERIENCE,
                "Allow experience gain in region",
                RegionFlagCategory.COMBAT,
            ),
            RegionFlagKey.GATHERING to booleanDefinition(
                RegionFlagKey.GATHERING,
                "Allow gathering in region",
                RegionFlagCategory.MISC,
            ),
            RegionFlagKey.RESIN to booleanDefinition(
                RegionFlagKey.RESIN,
                "Allow resin collection in region",
                RegionFlagCategory.MISC,
            ),
            // ─── Movement (missing) ───
            RegionFlagKey.WALK_SPEED to doubleDefinition(
                RegionFlagKey.WALK_SPEED,
                "Walk speed multiplier (1.0 = normal)",
                RegionFlagCategory.MOVEMENT,
            ),
            RegionFlagKey.FLY_SPEED to doubleDefinition(
                RegionFlagKey.FLY_SPEED,
                "Fly speed multiplier (1.0 = normal)",
                RegionFlagCategory.MOVEMENT,
            ),
            // ─── Environment (missing) ───
            RegionFlagKey.BLOCKED_EFFECTS to listDefinition(
                RegionFlagKey.BLOCKED_EFFECTS,
                "List of potion effect names blocked in the region",
                RegionFlagCategory.ENVIRONMENT,
            ),
            RegionFlagKey.BIOME to textDefinition(
                RegionFlagKey.BIOME,
                "Override biome for the region",
                RegionFlagCategory.ENVIRONMENT,
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

