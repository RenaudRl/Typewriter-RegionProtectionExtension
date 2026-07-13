package btc.renaud.protection.selection

import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import org.bukkit.Location
import org.bukkit.Server

fun Location.toTWPosition(): Position {
    val worldName = world?.name ?: ""
    return Position(World(worldName), x, y, z, yaw, pitch)
}

/**
 * Converts a Typewriter [Position] to a Bukkit [Location].
 * On Folia, prefer passing the [Server] instance to avoid global [org.bukkit.Bukkit.getWorld] lookups.
 * The no-arg overload uses [com.typewritermc.engine.paper.plugin.server] for Folia compatibility.
 */
fun Position.toBukkitLocation(): Location {
    val bukkitWorld = com.typewritermc.engine.paper.plugin.server.getWorld(world.identifier)
    return Location(bukkitWorld, x, y, z, yaw, pitch)
}

fun Position.toBukkitLocation(server: Server): Location {
    val bukkitWorld = server.getWorld(world.identifier)
    return Location(bukkitWorld, x, y, z, yaw, pitch)
}

