package com.typewritermc.protection.flags

import com.typewritermc.core.utils.point.Position
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.text.DecimalFormat

private val numberFormat = DecimalFormat("0.##")

fun resolveFlagResolutions(
    target: RegionModel,
    repository: RegionRepository,
    registry: RegionFlagRegistry,
): List<FlagResolution> {
    val lineage = buildLineage(target, repository)
    if (lineage.isEmpty()) return emptyList()

    val histories = linkedMapOf<RegionFlagKey, MutableList<FlagSource>>()
    lineage.forEach { model ->
        model.definition.flags.forEach { binding ->
            histories.getOrPut(binding.key) { mutableListOf() }.add(FlagSource(model, binding))
        }
    }

    return histories.map { (key, sources) ->
        val definition = registry.definition(key)
        val effective = resolveEffectiveSource(target, definition, sources)
        FlagResolution(key, definition, sources.toList(), effective, target)
    }.sortedBy { it.key.id }
}

fun displayName(region: RegionModel): String {
    return region.definition.name.ifBlank { region.artifact?.name ?: region.id }
}

fun formatFlagValue(value: FlagValue): Component = when (value) {
    is FlagValue.Boolean -> if (value.enabled) {
        Component.text("true", NamedTextColor.GREEN)
    } else {
        Component.text("false", NamedTextColor.RED)
    }
    is FlagValue.IntValue -> Component.text(value.value.toString(), NamedTextColor.AQUA)
    is FlagValue.DoubleValue -> Component.text(numberFormat.format(value.value), NamedTextColor.AQUA)
    is FlagValue.ColorValue -> Component.text(value.hex, NamedTextColor.LIGHT_PURPLE)
    is FlagValue.Text -> Component.text(truncate(value.content), NamedTextColor.WHITE)
    is FlagValue.Actions -> Component.text(
        value.actions.joinToString("; ") { it.id },
        NamedTextColor.GOLD
    )
    is FlagValue.ListValue -> Component.text(value.entries.joinToString(", "), NamedTextColor.GOLD)
    is FlagValue.LocationValue -> Component.text(formatPosition(value.position), NamedTextColor.BLUE)
    is FlagValue.VectorValue -> Component.text(
        "${numberFormat.format(value.x)}, ${numberFormat.format(value.y)}, ${numberFormat.format(value.z)}",
        NamedTextColor.BLUE
    )
    is FlagValue.SoundValue -> Component.text(
        "${value.sound} (${numberFormat.format(value.volume)}×/${numberFormat.format(value.pitch)}×)",
        NamedTextColor.YELLOW
    )
    is FlagValue.PotionValue -> Component.text(
        "${value.effect} lvl ${value.amplifier + 1} (${value.durationTicks}t)",
        NamedTextColor.LIGHT_PURPLE
    )
}

private fun buildLineage(target: RegionModel, repository: RegionRepository): List<RegionModel> {
    val lineage = mutableListOf<RegionModel>()
    val visited = mutableSetOf<String>()
    var current: RegionModel? = target
    while (current != null && visited.add(current.id)) {
        lineage.add(current)
        current = current.parentId?.let { repository.findById(it) }
    }
    return lineage.asReversed()
}

private fun resolveEffectiveSource(
    target: RegionModel,
    definition: RegionFlagDefinition?,
    sources: List<FlagSource>,
): FlagSource? {
    if (sources.isEmpty()) return null
    val inheritance = definition?.inheritance ?: FlagInheritance.ALWAYS
    return when (inheritance) {
        FlagInheritance.ALWAYS -> sources.last()
        FlagInheritance.OVERRIDE_ONLY, FlagInheritance.NEVER -> sources.lastOrNull { it.region.id == target.id }
    }
}

private fun truncate(input: String, limit: Int = 60): String {
    val normalized = input.replace("\n", " ").trim()
    if (normalized.length <= limit) return normalized
    return normalized.substring(0, limit - 1) + "…"
}

private fun formatPosition(position: Position): String {
    return "${numberFormat.format(position.x)}, ${numberFormat.format(position.y)}, ${numberFormat.format(position.z)} (${position.world.identifier})"
}

data class FlagSource(
    val region: RegionModel,
    val binding: FlagBinding,
)

data class FlagResolution(
    val key: RegionFlagKey,
    val definition: RegionFlagDefinition?,
    val history: List<FlagSource>,
    val effective: FlagSource?,
    val target: RegionModel,
) {
    val isEffective: Boolean get() = effective != null
    val isLocal: Boolean get() = effective?.region?.id == target.id
    val isInherited: Boolean get() = isEffective && !isLocal
    val overridesParent: Boolean get() = isLocal && history.size > 1
    val parentSource: FlagSource? get() = history.dropLast(1).lastOrNull()
    val blockedByInheritance: Boolean
        get() = !isEffective && history.isNotEmpty() && (definition?.inheritance == FlagInheritance.OVERRIDE_ONLY || definition?.inheritance == FlagInheritance.NEVER)
}
