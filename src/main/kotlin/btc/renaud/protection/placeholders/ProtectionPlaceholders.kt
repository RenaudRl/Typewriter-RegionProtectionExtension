package btc.renaud.protection.placeholders

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.extensions.placeholderapi.PlaceholderHandler
import btc.renaud.protection.entry.artifact.GlobalRegionArtifactEntry
import btc.renaud.protection.flags.FlagResolution
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.flags.RegionFlagRegistry
import btc.renaud.protection.flags.displayName
import btc.renaud.protection.flags.formatFlagValue
import btc.renaud.protection.flags.resolveFlagResolutions
import btc.renaud.protection.flags.FlagBinding
import btc.renaud.protection.flags.FlagValue
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

@Singleton
class ProtectionPlaceholders(
    private val repository: RegionRepository,
    private val registry: RegionFlagRegistry,
) : PlaceholderHandler {

    private val legacy = LegacyComponentSerializer.legacySection()

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        val segments = params.split(':')
        if (segments.isEmpty()) return null
        if (!segments[0].equals("regions", ignoreCase = true)) return null
        val path = segments.drop(1)
        if (path.isEmpty()) return null

        val cache = mutableMapOf<String, List<FlagResolution>>()
        return when (path[0].lowercase()) {
            "count" -> handleCount(player)
            "list" -> handleList(player) { it.regionId }
            "list_names" -> handleList(player) { displayName(it) }
            "stack" -> handleStack(player)
            "has" -> handleHas(player, path.drop(1))
            "primary", "dominant" -> handlePrimary(player, path.drop(1), cache)
            "active" -> handleActive(player, path.drop(1), cache)
            "flag" -> handleTopFlag(player, path.drop(1), cache)
            "by_id" -> handleById(path.drop(1), cache)
            "can_build" -> handleCanBuild(player)
            "can_interact" -> handleCanInteract(player)
            "exists" -> handleExists(path.drop(1))
            "nearest" -> handleNearest(player, path.drop(1), cache)
            else -> null
        }
    }

    private fun handleCount(player: Player?): String {
        player ?: return "0"
        return activeRegions(player).size.toString()
    }

    private fun handleList(player: Player?, extractor: (RegionModel) -> String): String {
        player ?: return ""
        val regions = activeRegions(player)
        return regions.joinToString(", ") { extractor(it) }
    }

    private fun handleStack(player: Player?): String {
        player ?: return ""
        val regions = activeRegions(player)
        return regions.joinToString(" | ") { region ->
            "P${region.priority} ${displayName(region)}"
        }
    }

    private fun handleHas(player: Player?, path: List<String>): String {
        player ?: return "false"
        if (path.isEmpty()) return "false"
        val targets = path[0].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (targets.isEmpty()) return "false"
        val regions = activeRegions(player)
        val match = regions.any { region ->
            targets.any { target -> region.regionId.equals(target, ignoreCase = true) }
        }
        return match.toString()
    }

    private fun handlePrimary(
        player: Player?,
        path: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
    ): String {
        player ?: return ""
        val regions = activeRegions(player)
        val region = regions.firstOrNull() ?: return ""
        return resolveRegionField(region, path, cache, totalActive = regions.size, position = 1)
    }

    private fun handleActive(
        player: Player?,
        path: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
    ): String {
        player ?: return ""
        if (path.isEmpty()) return ""
        val index = path[0].toIntOrNull()?.takeIf { it >= 1 } ?: return ""
        val regions = activeRegions(player)
        val region = regions.getOrNull(index - 1) ?: return ""
        val nested = path.drop(1)
        return resolveRegionField(region, nested, cache, totalActive = regions.size, position = index)
    }

    private fun handleTopFlag(
        player: Player?,
        path: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
    ): String {
        player ?: return ""
        val regions = activeRegions(player)
        val region = regions.firstOrNull() ?: return ""
        if (path.isEmpty()) return ""
        return resolveFlagField(region, path, cache)
    }

    private fun handleById(
        path: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
    ): String {
        val regionId = path.firstOrNull() ?: return ""
        val region = findRegion(regionId) ?: return ""
        val nested = path.drop(1)
        return resolveRegionField(region, nested, cache, totalActive = null, position = null)
    }

    private fun resolveRegionField(
        region: RegionModel,
        fieldPath: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
        totalActive: Int?,
        position: Int?,
    ): String {
        if (fieldPath.isEmpty()) {
            return displayName(region)
        }
        val key = fieldPath[0].lowercase()
        val rest = fieldPath.drop(1)
        return when (key) {
            "id" -> region.regionId
            "name" -> displayName(region)
            "priority" -> region.priority.toString()
            "position" -> position?.toString() ?: ""
            "overlap_count" -> totalActive?.toString() ?: ""
            "parent" -> region.parentId ?: ""
            "parent_name" -> region.parentId?.let { parentName(it) } ?: ""
            "owners" -> join(region.owners)
            "owner_count" -> region.owners.size.toString()
            "members" -> join(region.members)
            "member_count" -> region.members.size.toString()
            "groups" -> join(region.groups)
            "group_count" -> region.groups.size.toString()
            "is_global" -> (region.artifact is GlobalRegionArtifactEntry).toString()
            "depth" -> computeDepth(region).toString()
            "path" -> lineage(region).asReversed().joinToString(" -> ") { displayName(it) }
            "flags_count" -> effectiveFlags(region, cache).size.toString()
            "flags_local_count" -> effectiveFlags(region, cache).count { !it.isInherited }.toString()
            "flags_inherited_count" -> effectiveFlags(region, cache).count { it.isInherited }.toString()
            "flags_override_count" -> effectiveFlags(region, cache).count { it.overridesParent }.toString()
            "flags_blocked_count" -> resolutionsFor(region, cache).count { it.blockedByInheritance }.toString()
            "flags_list" -> effectiveFlags(region, cache).joinToString(", ") { resolution ->
                "${resolution.key.id}=${format(resolution.effective!!.binding.value)}"
            }
            "flag" -> resolveFlagField(region, rest, cache)
            else -> ""
        }
    }

    private fun resolveFlagField(
        region: RegionModel,
        path: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
    ): String {
        if (path.isEmpty()) return ""
        val flagId = path[0]
        val resolution = findFlagResolution(region, flagId, cache) ?: return ""
        val mode = path.getOrNull(1)?.lowercase()
        return when (mode) {
            null, "value" -> resolution.effective?.binding?.value?.let { format(it) } ?: ""
            "state" -> flagState(resolution)
            "source" -> resolution.effective?.region?.regionId ?: ""
            "source_name" -> resolution.effective?.region?.let { displayName(it) } ?: ""
            "history" -> resolution.history.joinToString(" -> ") { it.region.regionId }
            "parent" -> resolution.parentSource?.binding?.value?.let { format(it) } ?: ""
            "parent_source" -> resolution.parentSource?.region?.regionId ?: ""
            "parent_source_name" -> resolution.parentSource?.region?.let { displayName(it) } ?: ""
            else -> ""
        }
    }

    private fun flagState(resolution: FlagResolution): String {
        return when {
            !resolution.isEffective && resolution.blockedByInheritance -> "blocked"
            !resolution.isEffective -> "unset"
            resolution.isInherited -> "inherited"
            else -> "local"
        }
    }

    private fun findFlagResolution(
        region: RegionModel,
        flagId: String,
        cache: MutableMap<String, List<FlagResolution>>,
    ): FlagResolution? {
        val key = RegionFlagKey.entries.firstOrNull { it.id.equals(flagId, ignoreCase = true) } ?: return null
        return resolutionsFor(region, cache).firstOrNull { it.key == key }
    }

    private fun effectiveFlags(
        region: RegionModel,
        cache: MutableMap<String, List<FlagResolution>>,
    ): List<FlagResolution> = resolutionsFor(region, cache).filter { it.isEffective }

    private fun resolutionsFor(
        region: RegionModel,
        cache: MutableMap<String, List<FlagResolution>>,
    ): List<FlagResolution> = cache.getOrPut(region.regionId) {
        resolveFlagResolutions(region, repository, registry)
    }

    private fun activeRegions(player: Player): List<RegionModel> {
        return repository.regionsAt(player.location.toTWPosition())
    }

    private fun join(values: Collection<String>): String = values.joinToString(", ")

    private fun format(value: btc.renaud.protection.flags.FlagValue): String =
        legacy.serialize(formatFlagValue(value))

    private fun computeDepth(region: RegionModel): Int = lineage(region).size - 1

    private fun lineage(region: RegionModel): List<RegionModel> {
        val chain = mutableListOf<RegionModel>()
        val visited = mutableSetOf<String>()
        var current: RegionModel? = region
        while (current != null && visited.add(current.regionId)) {
            chain += current
            current = current.parentId?.let { repository.findById(it) }
        }
        return chain
    }

    private fun parentName(parentId: String): String =
        repository.findById(parentId)?.let { displayName(it) } ?: ""

    private fun findRegion(id: String): RegionModel? {
        repository.findById(id)?.let { return it }
        return repository.all().firstOrNull { it.regionId.equals(id, ignoreCase = true) }
    }

    private fun handleCanBuild(player: Player?): String {
        player ?: return "false"
        return checkFlag(player, RegionFlagKey.BUILD).toString()
    }

    private fun handleCanInteract(player: Player?): String {
        player ?: return "false"
        return checkFlag(player, RegionFlagKey.INTERACT).toString()
    }

    private fun handleExists(path: List<String>): String {
        if (path.isEmpty()) return "false"
        return (findRegion(path[0]) != null).toString()
    }

    private fun handleNearest(
        player: Player?,
        path: List<String>,
        cache: MutableMap<String, List<FlagResolution>>,
    ): String {
        player ?: return ""
        val playerPos = player.location.toTWPosition()
        val allRegions = repository.all()
        if (allRegions.isEmpty()) return ""

        val nearest = allRegions.minByOrNull { region ->
            val min = region.shape.min()
            val dx = playerPos.x - min.x
            val dy = playerPos.y - min.y
            val dz = playerPos.z - min.z
            dx * dx + dy * dy + dz * dz
        } ?: return ""

        if (path.isEmpty()) return displayName(nearest)
        return resolveRegionField(nearest, path, cache, totalActive = null, position = null)
    }

    private fun checkFlag(player: Player, flagKey: RegionFlagKey): Boolean {
        val regions = activeRegions(player)
        for (region in regions) {
            val binding = region.flags.firstOrNull { it.key == flagKey } ?: continue
            val value = binding.value as? FlagValue.Boolean ?: continue
            return value.enabled
        }
        return true
    }
}
