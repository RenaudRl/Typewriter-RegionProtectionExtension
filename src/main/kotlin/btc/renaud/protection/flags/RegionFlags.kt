package btc.renaud.protection.flags

import com.typewritermc.core.extension.annotations.AlgebraicTypeInfo
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.engine.paper.content.modes.custom.PositionContentMode
import kotlin.reflect.KClass

/** Enumerates all supported protection flags, covering WorldGuard and ExtraFlags Plus. */
enum class RegionFlagKey(val id: String) {
    BUILD("build"),
    BLOCK_BREAK("block-break"),
    BLOCK_PLACE("block-place"),
    USE("use"),
    INTERACT("interact"),
    PVP("pvp"),
    MOB_DAMAGE("mob-damage"),
    MOB_SPAWNING("mob-spawning"),
    CREEPER_EXPLOSION("creeper-explosion"),
    TNT("tnt"),
    FIRE_SPREAD("fire-spread"),
    LIGHTNING("lightning"),
    ENDERMAN_GRIEF("enderman-grief"),
    GHAST_FIREBALL("ghast-fireball"),
    LAVA_FIRE("lava-fire"),
    LAVA_FLOW("lava-flow"),
    WATER_FLOW("water-flow"),
    ICE_MELT("ice-melt"),
    SNOW_MELT("snow-melt"),
    LEAF_DECAY("leaf-decay"),
    GRASS_GROWTH("grass-growth"),
    VINE_GROWTH("vine-growth"),
    ENTITY_PAINTING_DESTROY("entity-painting-destroy"),
    VEHICLE_PLACE("vehicle-place"),
    VEHICLE_DESTROY("vehicle-destroy"),
    ENDER_PEARL("ender-pearl"),
    POTION_SPLASH("potion-splash"),
    EXP_DROPS("exp-drops"),
    ITEM_PICKUP("item-pickup"),
    ITEM_DROP("item-drop"),
    ENTRY("entry"),
    EXIT("exit"),
    ENTRY_ACTION("entry-deny-action"),
    EXIT_ACTION("exit-deny-action"),
    PASS_THROUGH("passthrough"),
    INVINCIBLE("invincible"),
    FALL_DAMAGE("fall-damage"),
    HUNGER("hunger"),
    HEAL_AMOUNT("heal-amount"),
    HEAL_DELAY("heal-delay"),
    HEAL_MIN("heal-min-health"),
    HEAL_MAX("heal-max-health"),
    TELEPORT("teleport"),
    BLOCKED_EFFECTS("blocked-effects"),
    FLY("fly"),
    WALK_SPEED("walk-speed"),
    FLY_SPEED("fly-speed"),
    KEEP_INVENTORY("keep-inventory"),
    KEEP_EXP("keep-exp"),
    CHAT_PREFIX("chat-prefix"),
    CHAT_SUFFIX("chat-suffix"),
    GODMODE("godmode"),
    FROSTWALKER("frostwalker"),
    NETHER_PORTALS("nether-portals"),
    GLIDE("glide"),
    CHUNK_UNLOAD("chunk-unload"),
    ITEM_DURABILITY("item-durability"),
    JOIN_LOCATION("join-location"),
    ENTRY_MIN_LEVEL("entry-min-level"),
    ENTRY_MAX_LEVEL("entry-max-level"),
    PERMIT_COMPLETELY("permit-completely"),
    WORLD_EDIT("worldedit"),
    BIOME("biome"),
    MOBTRAP_USE("mobtrap-use"),
    LIGHTER("lighter"),
    CHEST_ACCESS("chest-access"),
    PISTONS("pistons"),
    SNOW_FALL("snow-fall"),
    ICE_FORM("ice-form"),
    SEND_CHAT("send-chat"),
    RECEIVE_CHAT("receive-chat"),
    BLOCKED_CMDS("blocked-cmds"),
    ALLOWED_CMDS("allowed-cmds"),
    DAMAGE_ANIMALS("damage-animals"),
    SLEEP("sleep"),
    TNT_DAMAGE("tnt-damage"),
    FIREWORK_DAMAGE("firework-damage"),
    RAVAGER_GRIEF("ravager-grief"),
    CROP_TRAMPLING("crop-trampling"),
    BTCMOB_SPAWNING("btcmob-spawning"),
    BTCMOB_DAMAGE("btcmob-damage"),
    MANA_REGEN("mana-regen"),
    DOUBLE_DROP("double-drop"),
    GRAPPLING_HOOK("grappling-hook"),
    FISHING("fishing"),
    ALCHEMY("alchemy"),
    ENCHANTING_OVERRIDE("enchanting-override"),
    ELYTRA_AUTO_SWITCH("elytra-auto-switch"),
    GROUND_ITEM_GLOW("ground-item-glow"),
    MANA_DRAIN("mana-drain"),
    PISTOL("pistol"),
    CORRUPTION("corruption"),
    AUCTION("auction"),
    BACKPACK("backpack"),
    WARDROBE("wardrobe"),
    PROFESSION("profession"),
    EXPERIENCE("experience"),
    GATHERING("gathering"),
    RESIN("resin"),
}

enum class RegionFlagCategory {
    GENERAL,
    BLOCKS,
    COMBAT,
    MOVEMENT,
    INVENTORY,
    CHAT,
    MISC,
    ENVIRONMENT
}

enum class FlagValueKind {
    BOOLEAN,
    INTEGER,
    DOUBLE,
    STRING,
    COLOR,
    LIST,
    ACTIONS,
    LOCATION,
    SOUND,
    NONE
}

data class RegionFlagDefinition(
    val key: RegionFlagKey,
    val description: String,
    val valueKind: FlagValueKind,
    val category: RegionFlagCategory,
    val valueType: KClass<out FlagValue>,
    val evaluationPriority: FlagEvaluationPriority = FlagEvaluationPriority.DEFAULT,
    val inheritance: FlagInheritance = FlagInheritance.ALWAYS,
    val paperCompatibility: PaperCompatibility = PaperCompatibility.FOLIA_SAFE,
    val allowedValues: List<String> = emptyList(),
    val defaultValue: String? = null,
)

/** Represents the relative priority applied when evaluating a flag. */
enum class FlagEvaluationPriority {
    DEFAULT,
    HIGH,
    CRITICAL,
}

/** Controls how a flag propagates across a region hierarchy. */
enum class FlagInheritance {
    ALWAYS,
    OVERRIDE_ONLY,
    NEVER,
}

/** Describes the level of support a flag handler has on Paper/Folia. */
enum class PaperCompatibility {
    PAPER_ONLY,
    FOLIA_SAFE,
    EXPERIMENTAL,
}

sealed interface FlagValue {
    @AlgebraicTypeInfo("flag_value_bool", Colors.GREEN, "mdi:toggle-switch")
    data class Boolean(
        @field:Help("Boolean payload for true/false flags")
        val enabled: kotlin.Boolean = true,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_int", "#FFB300", "mdi:numeric")
    data class IntValue(
        @field:Help("Simple integer payload")
        val value: Int = 0,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_double", "#FFB300", "mdi:numeric-1-box-multiple-outline")
    data class DoubleValue(
        @field:Help("Double precision payload")
        val value: Double = 0.0,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_color", Colors.PINK, "mdi:palette")
    data class ColorValue(
        @field:Help("Represents RGB hex color strings like #00FFAA")
        val hex: String = "#FFFFFF",
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_text", Colors.BLUE, "mdi:text")
    data class Text(
        @field:Help("String payload for text fields")
        @field:MultiLine
        @field:Placeholder
        val content: String = "",
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_actions", Colors.ORANGE, "mdi:play")
    data class Actions(
        @field:Help("Typewriter action entries executed sequentially")
        val actions: List<Ref<ActionEntry>> = emptyList(),
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_list", Colors.CYAN, "mdi:format-list-bulleted")
    data class ListValue(
        @field:Help("List of ids or textual tokens")
        val entries: List<String> = emptyList(),
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_location", Colors.PURPLE, "mdi:map-marker")
    data class LocationValue(
        @field:Help("Exact location used by the flag")
        @field:ContentEditor(PositionContentMode::class)
        val position: Position = Position.ORIGIN,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_sound", Colors.YELLOW, "mdi:music-note")
    data class SoundValue(
        @field:Help("Sound key (namespaced id)") val sound: String = "minecraft:block.note_block.pling",
        @field:Help("Volume multiplier") val volume: Float = 1.0f,
        @field:Help("Pitch multiplier") val pitch: Float = 1.0f,
    ) : FlagValue
}

data class FlagBinding(
    val key: RegionFlagKey = RegionFlagKey.BUILD,
    val value: FlagValue = FlagValue.Boolean(true),
) {
    companion object {
        fun create(
            key: RegionFlagKey? = RegionFlagKey.BUILD,
            value: FlagValue? = null,
        ): FlagBinding {
            val sanitizedKey = key ?: RegionFlagKey.BUILD
            val effectiveValue = value ?: sanitizedKey.defaultFlagValue()
            return FlagBinding(
                key = sanitizedKey,
                value = effectiveValue.ensureCompatible(sanitizedKey),
            )
        }
    }
}

private fun FlagValue.ensureCompatible(key: RegionFlagKey): FlagValue {
    val definition = regionFlagDefinitions[key] ?: return this
    val expected = definition.defaultFlagValue()
    return if (expected::class == this::class) this else expected
}
