package btc.renaud.protection.listener

import com.typewritermc.engine.paper.scheduler.*
import org.bukkit.Location
import org.bukkit.plugin.Plugin

object SchedulerCompat {
    interface TaskHandle {
        fun cancel()
    }

    fun run(plugin: Plugin, location: Location?, task: () -> Unit) {
        if (location != null) {
            SchedulerModule.getAdapter().runAtLocation(plugin, location, Runnable { task() })
        } else {
            SchedulerModule.getAdapter().executeSync(plugin, Runnable { task() })
        }
    }

    fun runLater(plugin: Plugin, location: Location?, delayTicks: Long, task: () -> Unit): TaskHandle {
        val cancellable = if (location != null) {
            SchedulerModule.getAdapter().runAtLocationLater(plugin, location, Runnable { task() }, delayTicks)
        } else {
            SchedulerModule.getAdapter().executeSyncLater(plugin, Runnable { task() }, delayTicks)
        }
        return taskHandle { cancellable.cancel() }
    }

    fun runTimer(plugin: Plugin, location: Location?, delayTicks: Long, periodTicks: Long, task: () -> Unit): TaskHandle {
        val cancellable = if (location != null) {
            SchedulerModule.getAdapter().runAtLocationTimer(plugin, location, Runnable { task() }, delayTicks, periodTicks)
        } else {
            SchedulerModule.getAdapter().runGlobalTimer(plugin, Runnable { task() }, delayTicks, periodTicks)
        }
        return taskHandle { cancellable.cancel() }
    }

    private fun taskHandle(cancel: () -> Unit): TaskHandle = object : TaskHandle {
        override fun cancel() = cancel()
    }
}

