package com.typewritermc.protection.selection

import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.World
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class WorldEditGeometry(val shape: RegionShape, val nodes: List<Position>, val mode: SelectionMode)

class WorldEditAdapter {
    private val logger = LoggerFactory.getLogger("WorldEditAdapter")
    private val plugin = Bukkit.getPluginManager().let { manager ->
        manager.getPlugin("FastAsyncWorldEdit") ?: manager.getPlugin("WorldEdit")
    }

    private val modernSupport = ModernWorldEditSupport()
    private val legacySupport = LegacyWorldEditSupport()

    val isAvailable: Boolean
        get() = plugin != null && (modernSupport.isAvailable || legacySupport.isAvailable)

    fun captureSelection(player: Player): WorldEditGeometry? {
        if (!isAvailable) return null
        modernSupport.capture(player)?.let { return it }
        return legacySupport.capture(player)
    }

    private inner class ModernWorldEditSupport {
        private val worldEditClass = tryLoad("com.sk89q.worldedit.WorldEdit")
        private val bukkitAdapterClass = tryLoad("com.sk89q.worldedit.bukkit.BukkitAdapter")
        private val actorClass = tryLoad("com.sk89q.worldedit.extension.platform.Actor")
        private val sessionManagerClass = tryLoad("com.sk89q.worldedit.session.SessionManager")
        private val localSessionClass = tryLoad("com.sk89q.worldedit.session.LocalSession")
        private val weWorldClass = tryLoad("com.sk89q.worldedit.world.World")
        private val regionClass = tryLoad("com.sk89q.worldedit.regions.Region")
        private val polygonRegionClass = tryLoad("com.sk89q.worldedit.regions.Polygonal2DRegion")
        private val cylinderRegionClass = tryLoad("com.sk89q.worldedit.regions.CylinderRegion")
        private val blockVector3Class = tryLoad("com.sk89q.worldedit.math.BlockVector3")
        private val blockVector2Class = tryLoad("com.sk89q.worldedit.math.BlockVector2")
        private val incompleteRegionExceptionClass = tryLoad("com.sk89q.worldedit.IncompleteRegionException")

        val isAvailable: Boolean
            get() = worldEditClass != null &&
                bukkitAdapterClass != null &&
                sessionManagerClass != null &&
                localSessionClass != null &&
                weWorldClass != null &&
                regionClass != null &&
                blockVector3Class != null

        fun capture(player: Player): WorldEditGeometry? {
            if (!isAvailable) return null
            return runCatching {
                val worldEdit = worldEditClass!!.getMethod("getInstance").invoke(null)
                val sessionManager = worldEditClass.getMethod("getSessionManager").invoke(worldEdit)
                val actor = bukkitAdapterClass!!.getMethod("adapt", Player::class.java).invoke(null, player)
                val localSession = sessionManagerClass!!.getMethod("get", actorClass).invoke(sessionManager, actor)
                    ?: return null
                val adaptedWorld = bukkitAdapterClass.getMethod("adapt", org.bukkit.World::class.java)
                    .invoke(null, player.world)
                val region = obtainRegion(localSession, adaptedWorld) ?: return null
                val worldWrapper = World(player.world.name)
                convertRegion(region, worldWrapper)
            }.onFailure { error ->
                if (!isIncomplete(error)) {
                    logger.debug("Failed to import modern WorldEdit selection", error)
                }
            }.getOrNull()
        }

        private fun obtainRegion(localSession: Any, adaptedWorld: Any?): Any? {
            val sessionClazz = localSessionClass ?: return null
            val methods = buildList {
                add(runCatching { sessionClazz.getMethod("getSelection", weWorldClass) }.getOrNull())
                add(runCatching {
                    sessionClazz.getMethod("getSelection", weWorldClass, java.lang.Boolean.TYPE)
                }.getOrNull())
                add(runCatching { sessionClazz.getMethod("getSelection") }.getOrNull())
            }.filterNotNull()
            for (method in methods) {
                try {
                    val result = when (method.parameterCount) {
                        0 -> method.invoke(localSession)
                        1 -> method.invoke(localSession, adaptedWorld)
                        2 -> method.invoke(localSession, adaptedWorld, true)
                        else -> null
                    }
                    if (result != null) return result
                } catch (error: InvocationTargetException) {
                    if (!isIncomplete(error.targetException)) {
                        logger.debug("WorldEdit getSelection invocation failed", error.targetException)
                    }
                } catch (error: Throwable) {
                    if (!isIncomplete(error)) {
                        logger.debug("WorldEdit getSelection invocation failed", error)
                    }
                }
            }
            return null
        }

        private fun convertRegion(region: Any, world: World): WorldEditGeometry? {
            polygonRegionClass?.takeIf { it.isInstance(region) }?.let {
                return convertPolygonRegion(region, world)
            }
            cylinderRegionClass?.takeIf { it.isInstance(region) }?.let {
                return convertCylinderRegion(region, world)
            }
            return convertCuboidRegion(region, world)
        }

        private fun convertCuboidRegion(region: Any, world: World): WorldEditGeometry? {
            val minVector = runCatching { regionClass?.getMethod("getMinimumPoint")?.invoke(region) }.getOrNull()
            val maxVector = runCatching { regionClass?.getMethod("getMaximumPoint")?.invoke(region) }.getOrNull()
            val minPosition = blockVector3ToPosition(minVector, world) ?: return null
            val maxPosition = blockVector3ToPosition(maxVector, world) ?: return null
            return WorldEditGeometry(
                CuboidShape(minPosition, maxPosition),
                listOf(minPosition, maxPosition),
                SelectionMode.CUBOID,
            )
        }

        private fun convertPolygonRegion(region: Any, world: World): WorldEditGeometry? {
            val minY = extractNumber(region.javaClass, region, "getMinimumY", "getMinY") ?: 0.0
            val maxY = extractNumber(region.javaClass, region, "getMaximumY", "getMaxY") ?: minY
            val rawPoints = runCatching {
                polygonRegionClass?.getMethod("getPoints")?.invoke(region) as? Iterable<*>
            }.getOrNull() ?: return null
            val vertices = rawPoints.mapNotNull { point ->
                blockVector2ToPair(point)?.let { (x, z) -> Position(world, x, minY, z) }
            }
            if (vertices.size < 3) return null
            return WorldEditGeometry(
                PolygonPrismShape(minY, maxY, vertices),
                vertices,
                SelectionMode.POLYGON,
            )
        }

        private fun convertCylinderRegion(region: Any, world: World): WorldEditGeometry? {
            val centerVector = runCatching { region.javaClass.getMethod("getCenter").invoke(region) }.getOrNull()
            val center = blockVector3ToPosition(centerVector, world) ?: return null
            val radiusX = extractNumber(region.javaClass, region, "getRadiusX", "getRadius") ?: return null
            val radiusZ = extractNumber(region.javaClass, region, "getRadiusZ", "getRadius") ?: radiusX
            val minY = extractNumber(region.javaClass, region, "getMinimumY", "getMinY") ?: center.y
            val maxY = extractNumber(region.javaClass, region, "getMaximumY", "getMaxY") ?: center.y
            val steps = 32
            val nodes = (0 until steps).map { step ->
                val angle = 2 * PI * step / steps
                val x = center.x + radiusX * cos(angle)
                val z = center.z + radiusZ * sin(angle)
                Position(world, x, minY, z)
            }
            val cylinderCenter = Position(world, center.x, (minY + maxY) / 2.0, center.z)
            return WorldEditGeometry(
                CylinderShape(cylinderCenter, max(radiusX, radiusZ), minY, maxY),
                nodes,
                SelectionMode.POLYGON,
            )
        }

        private fun blockVector3ToPosition(vector: Any?, world: World): Position? {
            if (vector == null) return null
            val clazz = blockVector3Class ?: vector::class.java
            val x = extractNumber(clazz, vector, "getX", "getBlockX", "getDoubleX") ?: return null
            val y = extractNumber(clazz, vector, "getY", "getBlockY", "getDoubleY") ?: return null
            val z = extractNumber(clazz, vector, "getZ", "getBlockZ", "getDoubleZ") ?: return null
            return Position(world, x, y, z)
        }

        private fun blockVector2ToPair(vector: Any?): Pair<Double, Double>? {
            if (vector == null) return null
            val clazz = blockVector2Class ?: vector::class.java
            val x = extractNumber(clazz, vector, "getX", "getBlockX", "getDoubleX") ?: return null
            val z = extractNumber(clazz, vector, "getZ", "getBlockZ", "getDoubleZ") ?: return null
            return x to z
        }

        private fun isIncomplete(error: Throwable?): Boolean {
            val clazz = incompleteRegionExceptionClass ?: return false
            if (error == null) return false
            if (clazz.isInstance(error)) return true
            if (error is InvocationTargetException) {
                return isIncomplete(error.targetException)
            }
            return if (error.cause != null && error.cause !== error) {
                isIncomplete(error.cause)
            } else {
                false
            }
        }

        private fun tryLoad(name: String): Class<*>? = runCatching { Class.forName(name) }.getOrNull()
    }

    private inner class LegacyWorldEditSupport {
        private val worldEditPluginClass = runCatching { Class.forName("com.sk89q.worldedit.bukkit.WorldEditPlugin") }.getOrNull()
        private val fawePluginClass = runCatching { Class.forName("com.fastasyncworldedit.bukkit.WorldEditPlugin") }.getOrNull()
        private val selectionClass = runCatching { Class.forName("com.sk89q.worldedit.bukkit.selections.Selection") }.getOrNull()

        val isAvailable: Boolean
            get() = selectionClass != null

        fun capture(player: Player): WorldEditGeometry? {
            if (!isAvailable) return null
            val pluginInstance = plugin ?: return null
            val selectionClazz = selectionClass ?: return null
            val pluginClass = resolvePluginClass(pluginInstance) ?: return null

            return runCatching {
                val getSelection = pluginClass.getMethod("getSelection", Player::class.java)
                val selection = getSelection.invoke(pluginInstance, player) ?: return null
                val minLocation = selectionClazz.getMethod("getMinimumPoint").invoke(selection) as? Location ?: return null
                val maxLocation = selectionClazz.getMethod("getMaximumPoint").invoke(selection) as? Location ?: return null
                val world = minLocation.world ?: return null
                val worldWrapper = World(world.name)
                val minY = min(minLocation.y, maxLocation.y)
                val maxY = max(minLocation.y, maxLocation.y)

                val polygon = extractPolygon(selection)
                if (!polygon.isNullOrEmpty()) {
                    val vertices = polygon.map { (x, z) -> Position(worldWrapper, x, minY, z) }
                    return@runCatching WorldEditGeometry(
                        PolygonPrismShape(minY, maxY, vertices),
                        vertices,
                        SelectionMode.POLYGON,
                    )
                }

                val cylinder = extractCylinder(selection, minLocation, maxLocation)
                if (cylinder != null) {
                    val steps = 16
                    val nodes = (0 until steps).map { step ->
                        val angle = 2 * PI * step / steps
                        val x = cylinder.center.x + cylinder.radiusX * cos(angle)
                        val z = cylinder.center.z + cylinder.radiusZ * sin(angle)
                        Position(worldWrapper, x, cylinder.minY, z)
                    }
                    val centerPosition = Position(worldWrapper, cylinder.center.x, cylinder.center.y, cylinder.center.z)
                    return@runCatching WorldEditGeometry(
                        CylinderShape(centerPosition, max(cylinder.radiusX, cylinder.radiusZ), cylinder.minY, cylinder.maxY),
                        nodes,
                        SelectionMode.POLYGON,
                    )
                }

                val minPosition = minLocation.toTWPosition()
                val maxPosition = maxLocation.toTWPosition()
                WorldEditGeometry(
                    CuboidShape(minPosition, maxPosition),
                    listOf(minPosition, maxPosition),
                    SelectionMode.CUBOID,
                )
            }.onFailure { error ->
                logger.debug("Failed to import legacy WorldEdit selection", error)
            }.getOrNull()
        }

        private fun resolvePluginClass(pluginInstance: Plugin): Class<*>? {
            worldEditPluginClass?.takeIf { it.isInstance(pluginInstance) }?.let { return it }
            fawePluginClass?.takeIf { it.isInstance(pluginInstance) }?.let { return it }
            val clazz = pluginInstance::class.java
            return if (runCatching { clazz.getMethod("getSelection", Player::class.java) }.isSuccess) clazz else null
        }
    }

    private fun extractPolygon(selection: Any): List<Pair<Double, Double>>? {
        val method = runCatching { selection.javaClass.getMethod("getPoints") }.getOrNull() ?: return null
        val raw = runCatching { method.invoke(selection) }.getOrNull() as? Iterable<*> ?: return null
        val points = raw.mapNotNull { extractXZ(it) }
        return if (points.size >= 3) points else null
    }

    private fun extractCylinder(selection: Any, minLocation: Location, maxLocation: Location): CylinderData? {
        val clazz = selection.javaClass
        val center = runCatching { clazz.getMethod("getCenter").invoke(selection) as? Location }.getOrNull() ?: return null
        val radiusX = extractNumber(clazz, selection, "getRadiusX", "getRadius") ?: return null
        val radiusZ = extractNumber(clazz, selection, "getRadiusZ") ?: radiusX
        val minY = min(minLocation.y, maxLocation.y)
        val maxY = max(minLocation.y, maxLocation.y)
        return CylinderData(center, radiusX, radiusZ, minY, maxY)
    }

    private fun extractXZ(point: Any?): Pair<Double, Double>? {
        if (point == null) return null
        val clazz = point::class.java
        val x = extractNumber(clazz, point, "getX", "getBlockX", "getDoubleX") ?: extractField(clazz, point, "x")
        val z = extractNumber(clazz, point, "getZ", "getBlockZ", "getDoubleZ") ?: extractField(clazz, point, "z")
        if (x == null || z == null) return null
        return x to z
    }

    private fun extractNumber(clazz: Class<*>, instance: Any, vararg methodNames: String): Double? {
        methodNames.forEach { name ->
            val method = runCatching { clazz.getMethod(name) }.getOrNull() ?: return@forEach
            val value = runCatching { method.invoke(instance) }.getOrNull()
            if (value is Number) return value.toDouble()
        }
        return null
    }

    private fun extractField(clazz: Class<*>, instance: Any, name: String): Double? {
        return runCatching {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            (field.get(instance) as? Number)?.toDouble()
        }.getOrNull()
    }

    private data class CylinderData(
        val center: Location,
        val radiusX: Double,
        val radiusZ: Double,
        val minY: Double,
        val maxY: Double,
    )
}

