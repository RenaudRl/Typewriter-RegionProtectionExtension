package btc.renaud.protection.api

import com.typewritermc.core.utils.point.Position
import btc.renaud.protection.entry.region.RegionDefinitionEntry
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.listener.ProtectionListenerFlagContext
import btc.renaud.protection.selection.RegionShape
import btc.renaud.protection.selection.toTWPosition
import btc.renaud.protection.service.storage.RegionChangeListener
import btc.renaud.protection.service.storage.RegionModel
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Default implementation of [ProtectionApi].
 * Provides programmatic access to region management, flag queries, and change listeners.
 */
class ProtectionApiImpl(
    private val regionRepository: RegionRepository,
    private val flagActionExecutor: FlagActionExecutor,
) : ProtectionApi {

    override fun createRegion(definition: RegionDefinitionEntry, shape: RegionShape): String {
        val artifact = definition.artifact.get()
            ?: throw IllegalStateException("Region definition '${definition.id}' has no artifact")
        regionRepository.updateRegion(definition, shape, emptyList(), btc.renaud.protection.selection.SelectionMode.CUBOID, null)
        return definition.id
    }

    override fun addFlag(regionId: String, key: RegionFlagKey, value: String) {
        regionRepository.setFlag(regionId, key, value)
    }

    override fun removeFlag(regionId: String, key: RegionFlagKey) {
        regionRepository.setFlag(regionId, key, "")
    }

    override fun addOwner(regionId: String, playerId: String) {
        regionRepository.addOwner(regionId, playerId)
    }

    override fun removeOwner(regionId: String, playerId: String) {
        regionRepository.removeOwner(regionId, playerId)
    }

    override fun getRegionsAt(location: Location): List<RegionModel> {
        return regionRepository.regionsAt(location.toTWPosition())
    }

    override fun getRegionsAt(position: Position): List<RegionModel> {
        return regionRepository.regionsAt(position)
    }

    override fun findById(regionId: String): RegionModel? {
        return regionRepository.findById(regionId)
    }

    override fun getAllRegions(): Collection<RegionModel> {
        return regionRepository.all()
    }

    override fun addChangeListener(listener: RegionChangeListener) {
        regionRepository.addChangeListener(listener)
    }

    override fun removeChangeListener(listener: RegionChangeListener) {
        regionRepository.removeChangeListener(listener)
    }

    override fun canBuild(player: Player, location: Location): Boolean {
        val regions = regionRepository.regionsAt(location.toTWPosition())
        for (region in regions) {
            val context = ProtectionListenerFlagContext(
                region = region,
                event = org.bukkit.event.block.BlockPlaceEvent(
                    location.block,
                    location.block.state,
                    location.block,
                    player.inventory.itemInMainHand,
                    player,
                    true,
                    org.bukkit.inventory.EquipmentSlot.HAND,
                ),
                location = location,
                player = player,
                source = player,
            )
            val result = flagActionExecutor.evaluate(context, RegionFlagKey.BUILD)
            if (result is FlagEvaluation.Denied) return false
            val blockResult = flagActionExecutor.evaluate(context, RegionFlagKey.BLOCK_PLACE)
            if (blockResult is FlagEvaluation.Denied) return false
        }
        return true
    }

    override fun canInteract(player: Player, location: Location): Boolean {
        val regions = regionRepository.regionsAt(location.toTWPosition())
        for (region in regions) {
            val context = ProtectionListenerFlagContext(
                region = region,
                event = org.bukkit.event.player.PlayerInteractEvent(
                    player,
                    org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                    player.inventory.itemInMainHand,
                    location.block,
                    org.bukkit.block.BlockFace.UP,
                ),
                location = location,
                player = player,
                source = player,
            )
            val result = flagActionExecutor.evaluate(context, RegionFlagKey.INTERACT)
            if (result is FlagEvaluation.Denied) return false
            val useResult = flagActionExecutor.evaluate(context, RegionFlagKey.USE)
            if (useResult is FlagEvaluation.Denied) return false
        }
        return true
    }

    override fun deleteRegion(regionId: String) {
        regionRepository.removeRegion(regionId)
    }
}
