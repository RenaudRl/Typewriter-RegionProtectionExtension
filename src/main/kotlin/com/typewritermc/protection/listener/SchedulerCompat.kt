package com.typewritermc.protection.listener

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

object SchedulerCompat {
    private val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
    }.isSuccess

    interface TaskHandle {
        fun cancel()
    }

    fun run(plugin: Plugin, location: Location?, task: () -> Unit) {
        if (!isFolia) {
            Bukkit.getScheduler().runTask(plugin, Runnable { task() })
            return
        }
        location?.let { target ->
            Bukkit.getRegionScheduler().run(plugin, target) { task() }
        } ?: Bukkit.getGlobalRegionScheduler().run(plugin) { task() }
    }

    fun runLater(plugin: Plugin, location: Location?, delayTicks: Long, task: () -> Unit): TaskHandle {
        if (!isFolia) {
            val scheduled: BukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, delayTicks)
            return taskHandle { scheduled.cancel() }
        }

        val scheduled: ScheduledTask = location?.let { target ->
            Bukkit.getRegionScheduler().runDelayed(plugin, target, { _ -> task() }, delayTicks)
        } ?: Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { _ -> task() }, delayTicks)

        return taskHandle { scheduled.cancel() }
    }

    private fun taskHandle(cancel: () -> Unit): TaskHandle = object : TaskHandle {
        override fun cancel() = cancel()
    }
}

