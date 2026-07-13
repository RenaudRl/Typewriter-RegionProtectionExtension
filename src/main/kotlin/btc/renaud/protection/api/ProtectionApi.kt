package btc.renaud.protection.api

import com.typewritermc.core.utils.point.Position
import btc.renaud.protection.entry.region.RegionDefinitionEntry
import btc.renaud.protection.flags.FlagBinding
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.selection.RegionShape
import btc.renaud.protection.service.storage.RegionChangeListener
import btc.renaud.protection.service.storage.RegionModel
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Public API for ProtectionExtension.
 * Allows other plugins to programmatically create regions, manage flags,
 * and listen to region events.
 */
interface ProtectionApi {
    /**
     * Creates a new region definition.
     */
    fun createRegion(definition: RegionDefinitionEntry, shape: RegionShape): String

    /**
     * Adds a flag to an existing region.
     */
    fun addFlag(regionId: String, key: RegionFlagKey, value: String)

    /**
     * Removes a flag from a region.
     */
    fun removeFlag(regionId: String, key: RegionFlagKey)

    /**
     * Adds an owner to a region.
     */
    fun addOwner(regionId: String, playerId: String)

    /**
     * Removes an owner from a region.
     */
    fun removeOwner(regionId: String, playerId: String)

    /**
     * Gets all regions at a specific location.
     */
    fun getRegionsAt(location: Location): List<RegionModel>

    /**
     * Gets all regions at a specific position.
     */
    fun getRegionsAt(position: Position): List<RegionModel>

    /**
     * Finds a region by its ID.
     */
    fun findById(regionId: String): RegionModel?

    /**
     * Gets all active regions.
     */
    fun getAllRegions(): Collection<RegionModel>

    /**
     * Registers a listener for region changes.
     */
    fun addChangeListener(listener: RegionChangeListener)

    /**
     * Removes a region change listener.
     */
    fun removeChangeListener(listener: RegionChangeListener)

    /**
     * Checks if a player can build at a specific location.
     */
    fun canBuild(player: Player, location: Location): Boolean

    /**
     * Checks if a player can interact at a specific location.
     */
    fun canInteract(player: Player, location: Location): Boolean

    /**
     * Deletes a region.
     */
    fun deleteRegion(regionId: String)
}
