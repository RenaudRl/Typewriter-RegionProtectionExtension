package btc.renaud.protection.listener.player

import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.listener.AbstractProtectionListener
import btc.renaud.protection.listener.FlagActionExecutor
import btc.renaud.protection.listener.ProtectionListenerFlagContext
import btc.renaud.protection.service.storage.RegionRepository
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemDamageEvent
import org.slf4j.LoggerFactory

/**
 * Protects item durability when ITEM_DURABILITY flag is active in region.
 * Only applies to items used within the region (tools, weapons, armor).
 */
@Singleton
class ItemDurabilityListener(
    repository: RegionRepository,
    actionExecutor: FlagActionExecutor,
) : AbstractProtectionListener(repository, actionExecutor), Listener {
    private val logger = LoggerFactory.getLogger("ItemDurabilityListener")

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val player = event.player
        val location = player.location
        val item = event.item
        val itemType = item.type

        // Only protect actual usable items (tools, weapons, armor)
        if (!isDamageable(itemType)) return

        val (evaluation, context) = evaluateFlag(
            RegionFlagKey.ITEM_DURABILITY,
            event,
            location,
            player,
            source = player
        )
        if (evaluation is FlagEvaluation.Modify) {
            val shouldProtect = evaluation.metadata["item.durability.protect"] as? Boolean ?: false
            if (shouldProtect) {
                event.isCancelled = true
                logger.debug("Protected durability for {} using {} in region {}",
                    player.name, itemType, context?.region?.id ?: "unknown")
            }
        }
    }

    private fun isDamageable(type: Material): Boolean {
        val name = type.name
        return name.contains("SWORD") || name.contains("AXE") || name.contains("PICKAXE") ||
                name.contains("SHOVEL") || name.contains("HOE") ||
                name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS") ||
                type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT ||
                type == Material.FISHING_ROD || type == Material.SHEARS ||
                type == Material.FLINT_AND_STEEL || type == Material.BRUSH ||
                type == Material.MACE || type == Material.WOLF_ARMOR
    }
}
