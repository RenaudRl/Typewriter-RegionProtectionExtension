package btcrenaud.protection.init

import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import btcrenaud.protection.listener.ProtectionMessageDispatcher
import btcrenaud.protection.listener.building.BuildProtectionListener
import btcrenaud.protection.listener.combat.CombatProtectionListener
import btcrenaud.protection.listener.environment.ExplosionProtectionListener
import btcrenaud.protection.listener.interaction.InteractionProtectionListener
import btcrenaud.protection.listener.mob.BTCMobProtectionListener
import btcrenaud.protection.listener.mob.MobSpawnProtectionListener
import btcrenaud.protection.listener.mob.MythicMobsSpawnProtectionListener
import btcrenaud.protection.listener.movement.MovementProtectionListener
import btcrenaud.protection.listener.environment.PistonProtectionListener
import btcrenaud.protection.listener.environment.EnvironmentPropertiesProtectionListener
import btcrenaud.protection.listener.player.ChatProtectionListener
import btcrenaud.protection.listener.player.PlayerPropertiesProtectionListener
import btcrenaud.protection.listener.biome.RegionBiomeListener
import btcrenaud.protection.selection.SelectionService
import btcrenaud.protection.service.runtime.FlagInspectionService
import btcrenaud.protection.service.storage.RegionRepository
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
object ProtectionInitializer : Initializable {
    private val logger = LoggerFactory.getLogger("ProtectionInitializer")
    private val plugin = Bukkit.getPluginManager().getPlugin("TypeWriter")
        ?: error("TypeWriter plugin is required")
    private val repository: RegionRepository = KoinJavaComponent.get(RegionRepository::class.java)
    private val selectionService: SelectionService = KoinJavaComponent.get(SelectionService::class.java)
    private val listeners: List<Listener> by lazy {
        listOf(
            KoinJavaComponent.get(BuildProtectionListener::class.java),
            KoinJavaComponent.get(InteractionProtectionListener::class.java),
            KoinJavaComponent.get(CombatProtectionListener::class.java),
            KoinJavaComponent.get(MovementProtectionListener::class.java),
            KoinJavaComponent.get(MobSpawnProtectionListener::class.java),
            KoinJavaComponent.get(ExplosionProtectionListener::class.java),
            KoinJavaComponent.get(EnvironmentPropertiesProtectionListener::class.java),
            KoinJavaComponent.get(btcrenaud.protection.listener.mob.EntityPropertiesProtectionListener::class.java),
            KoinJavaComponent.get(btcrenaud.protection.listener.player.PlayerStateProtectionListener::class.java),
            KoinJavaComponent.get(RegionBiomeListener::class.java),
            KoinJavaComponent.get(PistonProtectionListener::class.java),
            KoinJavaComponent.get(ChatProtectionListener::class.java),
            KoinJavaComponent.get(PlayerPropertiesProtectionListener::class.java),
            KoinJavaComponent.get(BTCMobProtectionListener::class.java),
        )
    }
    private val mythicLifecycleListener = object : Listener {
        @EventHandler
        fun onPluginEnable(event: PluginEnableEvent) {
            if (event.plugin.name.equals("MythicMobs", ignoreCase = true)) {
                logger.info("Detected MythicMobs enablement; activating mob spawning integration")
                registerMythicMobsListener()
            }
        }

        @EventHandler
        fun onPluginDisable(event: PluginDisableEvent) {
            if (event.plugin.name.equals("MythicMobs", ignoreCase = true)) {
                logger.info("MythicMobs disabled; suspending mob spawning integration")
                unregisterMythicMobsListener()
            }
        }
    }
    private var mythicMobsListener: Listener? = null
    private val inspectionService: FlagInspectionService = KoinJavaComponent.get(FlagInspectionService::class.java)
    private val quitListener = object : Listener {
        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            inspectionService.handleQuit(event.player.uniqueId)
        }
    }
    private val initialized = AtomicBoolean(false)

    override suspend fun initialize() {
        if (!initialized.compareAndSet(false, true)) {
            logger.debug("Protection runtime already initialized; skipping duplicate call")
            return
        }
        repository.reload()
        ProtectionMessageDispatcher.registerQuitListener(plugin)
        val manager = Bukkit.getPluginManager()
        listeners.forEach { manager.registerEvents(it, plugin) }
        manager.registerEvents(mythicLifecycleListener, plugin)
        manager.registerEvents(quitListener, plugin)
        registerMythicMobsListener()
        logger.info("Protection runtime initialized with ${listeners.size} listener groups")
    }

    override suspend fun shutdown() {
        unregisterMythicMobsListener()
        HandlerList.unregisterAll(mythicLifecycleListener)
        HandlerList.unregisterAll(quitListener)
        listeners.forEach { HandlerList.unregisterAll(it) }
        selectionService.shutdown()
        inspectionService.shutdown()
        logger.info("Protection runtime shutdown")
    }

    private fun registerMythicMobsListener() {
        val manager = Bukkit.getPluginManager()
        if (!manager.isPluginEnabled("MythicMobs")) {
            logger.debug("MythicMobs not enabled; skipping optional mob spawning listener registration")
            return
        }
        if (mythicMobsListener != null) return
        val listener: MythicMobsSpawnProtectionListener = try {
            KoinJavaComponent.get(MythicMobsSpawnProtectionListener::class.java)
        } catch (error: Throwable) {
            logger.warn("Failed to initialize MythicMobs mob spawning integration", error)
            return
        }
        manager.registerEvents(listener, plugin)
        mythicMobsListener = listener
        logger.info("MythicMobs mob spawning integration enabled")
    }

    private fun unregisterMythicMobsListener() {
        val listener = mythicMobsListener ?: return
        HandlerList.unregisterAll(listener)
        mythicMobsListener = null
        logger.info("MythicMobs mob spawning integration disabled")
    }
}
