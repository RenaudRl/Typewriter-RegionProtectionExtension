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
            Bukkit.getScheduler().runTask(plugin, Runnable(task))
            return
        }
        if (location != null) {
            Bukkit.getRegionScheduler().run(plugin, location) { task() }
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin) { task() }
        }
    }

    fun runLater(plugin: Plugin, location: Location?, delayTicks: Long, task: () -> Unit): TaskHandle {
        if (!isFolia) {
            val scheduled: BukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable(task), delayTicks)
            return object : TaskHandle {
                override fun cancel() {
                    scheduled.cancel()
                }
            }
        }
        return if (location != null) {
            val scheduled: ScheduledTask = Bukkit.getRegionScheduler().runDelayed(plugin, location, { _ -> task() }, delayTicks)
            object : TaskHandle {
                override fun cancel() {
                    scheduled.cancel()
                }
            }
        } else {
            val scheduled: ScheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { _ -> task() }, delayTicks)
            object : TaskHandle {
                override fun cancel() {
                    scheduled.cancel()
                }
            }
        }
    }
}
