package com.typewritermc.protection.flags

import com.typewritermc.core.extension.annotations.AlgebraicTypeInfo
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.utils.point.Position
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
    ENTRY_MESSAGE("entry-deny-message"),
    EXIT_MESSAGE("exit-deny-message"),
    PASS_THROUGH("passthrough"),
    INVINCIBLE("invincible"),
    FALL_DAMAGE("fall-damage"),
    HUNGER("hunger"),
    HEAL_AMOUNT("heal-amount"),
    HEAL_DELAY("heal-delay"),
    HEAL_MIN("heal-min-health"),
    HEAL_MAX("heal-max-health"),
    TELEPORT("teleport"),
    SPAWN_LOCATION("spawn-location"),
    RESPAWN_LOCATION("respawn-location"),
    BLOCKED_EFFECTS("blocked-effects"),
    GIVE_EFFECTS("give-effects"),
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
    TELEPORT_ON_ENTRY("teleport-on-entry"),
    TELEPORT_ON_EXIT("teleport-on-exit"),
    COMMAND_ON_ENTRY("command-on-entry"),
    COMMAND_ON_EXIT("command-on-exit"),
    CONSOLE_COMMAND_ON_ENTRY("console-command-on-entry"),
    CONSOLE_COMMAND_ON_EXIT("console-command-on-exit"),
    PLAY_SOUNDS("play-sounds"),
    BLOCKED_ITEMS("blocked-items"),
    GIVE_ITEMS("give-items"),
    ENTRY_MIN_LEVEL("entry-min-level"),
    ENTRY_MAX_LEVEL("entry-max-level"),
    PERMIT_COMPLETELY("permit-completely"),
    WORLD_EDIT("worldedit"),
    MESSAGE_ON_ENTRY("message-on-entry"),
    MESSAGE_ON_EXIT("message-on-exit"),
    INVENTORY_LOADOUT("inventory-loadout"),
    COMMAND_BLACKLIST("command-blacklist"),
    COMMAND_WHITELIST("command-whitelist"),
    PLACEHOLDER_GATE("placeholder-gate")
}

enum class RegionFlagCategory {
    GENERAL,
    BLOCKS,
    COMBAT,
    MOVEMENT,
    INVENTORY,
    CHAT,
    AUDIO,
    MISC
}

enum class FlagValueKind {
    BOOLEAN,
    INTEGER,
    DOUBLE,
    STRING,
    COLOR,
    ENUM,
    LIST,
    COMMANDS,
    LOCATION,
    VECTOR,
    SOUND,
    POTION_LIST,
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
    LOW,
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

    @AlgebraicTypeInfo("flag_value_int", Colors.AMBER, "mdi:numeric")
    data class IntValue(
        @field:Help("Simple integer payload")
        val value: Int = 0,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_double", Colors.AMBER, "mdi:numeric-1-box-multiple-outline")
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

    @AlgebraicTypeInfo("flag_value_commands", Colors.ORANGE, "mdi:console")
    data class Commands(
        @field:Help("List of commands executed sequentially")
        val commands: List<String> = emptyList(),
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

    @AlgebraicTypeInfo("flag_value_vector", Colors.PURPLE, "mdi:axis-arrow")
    data class VectorValue(
        @field:Help("X component") val x: Double = 0.0,
        @field:Help("Y component") val y: Double = 0.0,
        @field:Help("Z component") val z: Double = 0.0,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_sound", Colors.GOLD, "mdi:music-note")
    data class SoundValue(
        @field:Help("Sound key (namespaced id)") val sound: String = "minecraft:block.note_block.pling",
        @field:Help("Volume multiplier") val volume: Float = 1.0f,
        @field:Help("Pitch multiplier") val pitch: Float = 1.0f,
    ) : FlagValue

    @AlgebraicTypeInfo("flag_value_potion", Colors.MEDIUM_PURPLE, "mdi:flask")
    data class PotionValue(
        @field:Help("Potion effect identifier") val effect: String = "minecraft:speed",
        @field:Help("Amplifier applied to the effect") val amplifier: Int = 0,
        @field:Help("Effect duration in ticks") val durationTicks: Int = 200,
    ) : FlagValue
}

class FlagBinding(
    key: RegionFlagKey = RegionFlagKey.BUILD,
    value: FlagValue = key.defaultFlagValue(),
) {
    var key: RegionFlagKey = key
        set(value) {
            if (field == value) {
                return
            }
            field = value
            this.value = value.defaultFlagValue()
        }

    var value: FlagValue = value.ensureCompatible(this.key)
        set(value) {
            field = value.ensureCompatible(this.key)
        }

    init {
        this.key = key
        this.value = value
    }

    fun copy(key: RegionFlagKey = this.key, value: FlagValue = this.value): FlagBinding {
        return FlagBinding(key, value)
    }

    operator fun component1(): RegionFlagKey = key

    operator fun component2(): FlagValue = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlagBinding) return false
        return key == other.key && value == other.value
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String {
        return "FlagBinding(key=$key, value=$value)"
    }
}

private fun FlagValue.ensureCompatible(key: RegionFlagKey): FlagValue {
    val definition = regionFlagDefinitions[key] ?: return this
    val expected = definition.defaultFlagValue()
    return if (expected::class == this::class) this else expected
}
