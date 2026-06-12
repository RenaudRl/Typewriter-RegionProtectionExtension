package btcrenaud.protection.listener

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin

object SchedulerCompat {
    private val folia: Boolean by lazy { detectFolia() }
    private fun detectFolia(): Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); true
    } catch (_: ClassNotFoundException) { false }
      catch (_: Throwable) { false }

    interface TaskHandle {
        fun cancel()
    }

    fun run(plugin: Plugin, location: Location?, task: () -> Unit) {
        if (location != null && folia) {
            Bukkit.getRegionScheduler().execute(plugin, location) { task() }
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin) { task() }
        }
    }

    fun runLater(plugin: Plugin, location: Location?, delayTicks: Long, task: () -> Unit): TaskHandle {
        val cancellable = if (location != null && folia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, { _ -> task() }, delayTicks)
        } else {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { _ -> task() }, delayTicks)
        }
        return object : TaskHandle {
            override fun cancel() { cancellable.cancel() }
        }
    }

    fun runTimer(plugin: Plugin, location: Location?, delayTicks: Long, periodTicks: Long, task: () -> Unit): TaskHandle {
        val cancellable = if (location != null && folia) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, { _ -> task() }, delayTicks.coerceAtLeast(1), periodTicks.coerceAtLeast(1))
        } else {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _ -> task() }, delayTicks.coerceAtLeast(1), periodTicks.coerceAtLeast(1))
        }
        return object : TaskHandle {
            override fun cancel() { cancellable.cancel() }
        }
    }
}
