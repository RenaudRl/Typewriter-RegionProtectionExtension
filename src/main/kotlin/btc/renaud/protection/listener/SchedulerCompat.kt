package btc.renaud.protection.listener

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin

/**
 * Folia-safe scheduling helper built on the Paper region scheduler API.
 *
 * The public extension targets the official Typewriter engine, which does not ship
 * the BTC-CORE `SchedulerModule`. Paper exposes the region/global schedulers directly
 * (they transparently run on the main thread on non-Folia servers), so we use them to
 * keep the exact same call surface the rest of the extension relies on.
 */
object SchedulerCompat {
    interface TaskHandle {
        fun cancel()
    }

    fun run(plugin: Plugin, location: Location?, task: () -> Unit) {
        if (location != null) {
            Bukkit.getRegionScheduler().run(plugin, location) { task() }
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin) { task() }
        }
    }

    fun runLater(plugin: Plugin, location: Location?, delayTicks: Long, task: () -> Unit): TaskHandle {
        val delay = delayTicks.coerceAtLeast(1)
        val scheduled = if (location != null) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, { task() }, delay)
        } else {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task() }, delay)
        }
        return taskHandle { scheduled.cancel() }
    }

    fun runTimer(plugin: Plugin, location: Location?, delayTicks: Long, periodTicks: Long, task: () -> Unit): TaskHandle {
        val delay = delayTicks.coerceAtLeast(1)
        val period = periodTicks.coerceAtLeast(1)
        val scheduled = if (location != null) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, { task() }, delay, period)
        } else {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task() }, delay, period)
        }
        return taskHandle { scheduled.cancel() }
    }

    private fun taskHandle(cancel: () -> Unit): TaskHandle = object : TaskHandle {
        override fun cancel() = cancel()
    }
}
