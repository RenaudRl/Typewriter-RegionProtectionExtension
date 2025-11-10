package com.typewritermc.protection.selection

import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import org.bukkit.Bukkit
import org.bukkit.Location

fun Location.toTWPosition(): Position {
    val worldName = world?.name ?: ""
    return Position(World(worldName), x, y, z, yaw, pitch)
}

fun Position.toBukkitLocation(): Location {
    val bukkitWorld = Bukkit.getWorld(world.identifier)
    return Location(bukkitWorld, x, y, z, yaw, pitch)
}
