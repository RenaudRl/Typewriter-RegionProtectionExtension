package btc.renaud.protection.init

import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import btc.renaud.protection.listener.ProtectionMessageDispatcher
import btc.renaud.protection.listener.building.BuildProtectionListener
import btc.renaud.protection.listener.combat.CombatProtectionListener
import btc.renaud.protection.listener.environment.ExplosionProtectionListener
import btc.renaud.protection.listener.environment.FrostWalkerProtectionListener
import btc.renaud.protection.listener.environment.ChunkKeepAliveListener
import btc.renaud.protection.listener.interaction.InteractionProtectionListener
import btc.renaud.protection.listener.mob.BTCMobProtectionListener
import btc.renaud.protection.listener.mob.MobSpawnProtectionListener
import btc.renaud.protection.listener.mob.MythicMobsSpawnProtectionListener
import btc.renaud.protection.listener.movement.MovementProtectionListener
import btc.renaud.protection.listener.environment.PistonProtectionListener
import btc.renaud.protection.listener.environment.EnvironmentPropertiesProtectionListener
import btc.renaud.protection.listener.player.ChatProtectionListener
import btc.renaud.protection.listener.player.ChatPrefixSuffixListener
import btc.renaud.protection.listener.player.PlayerPropertiesProtectionListener
import btc.renaud.protection.listener.player.ItemDurabilityListener
import btc.renaud.protection.listener.player.GlideProtectionListener
import btc.renaud.protection.listener.player.JoinLocationListener
import btc.renaud.protection.listener.player.WorldEditProtectionListener
import btc.renaud.protection.listener.biome.RegionBiomeListener
import btc.renaud.protection.selection.SelectionService
import btc.renaud.protection.service.runtime.FlagInspectionService
import btc.renaud.protection.service.runtime.HealService
import btc.renaud.protection.service.storage.RegionChangeListener
import btc.renaud.protection.service.storage.RegionRepository
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
class ProtectionInitializer : Initializable {
    private val logger = LoggerFactory.getLogger("ProtectionInitializer")
    private val plugin = Bukkit.getPluginManager().getPlugin("TypeWriter")
        ?: error("TypeWriter plugin is required")

    // ─── Koin dependencies (lazy to avoid type inference issues) ───
    private val repository: RegionRepository by lazy { KoinJavaComponent.get(RegionRepository::class.java) }
    private val selectionService: SelectionService by lazy { KoinJavaComponent.get(SelectionService::class.java) }
    private val inspectionService: FlagInspectionService by lazy { KoinJavaComponent.get(FlagInspectionService::class.java) }
    private val healService: HealService by lazy { KoinJavaComponent.get(HealService::class.java) }
    private val chunkKeepAliveListener: ChunkKeepAliveListener by lazy { KoinJavaComponent.get(ChunkKeepAliveListener::class.java) }
    private val blueMapIntegrationService: btc.renaud.protection.bluemap.BlueMapIntegrationService by lazy {
        KoinJavaComponent.get(btc.renaud.protection.bluemap.BlueMapIntegrationService::class.java)
    }
    private val mythicMobsListener: MythicMobsSpawnProtectionListener? by lazy {
        try { KoinJavaComponent.get(MythicMobsSpawnProtectionListener::class.java) } catch (_: Throwable) { null }
    }

    private val listeners: List<Listener> by lazy {
        listOf(
            KoinJavaComponent.get(BuildProtectionListener::class.java),
            KoinJavaComponent.get(InteractionProtectionListener::class.java),
            KoinJavaComponent.get(CombatProtectionListener::class.java),
            KoinJavaComponent.get(MovementProtectionListener::class.java),
            KoinJavaComponent.get(MobSpawnProtectionListener::class.java),
            KoinJavaComponent.get(ExplosionProtectionListener::class.java),
            KoinJavaComponent.get(EnvironmentPropertiesProtectionListener::class.java),
            KoinJavaComponent.get(btc.renaud.protection.listener.mob.EntityPropertiesProtectionListener::class.java),
            KoinJavaComponent.get(btc.renaud.protection.listener.player.PlayerStateProtectionListener::class.java),
            KoinJavaComponent.get(RegionBiomeListener::class.java),
            KoinJavaComponent.get(PistonProtectionListener::class.java),
            KoinJavaComponent.get(ChatProtectionListener::class.java),
            KoinJavaComponent.get(PlayerPropertiesProtectionListener::class.java),
            KoinJavaComponent.get(BTCMobProtectionListener::class.java),
            KoinJavaComponent.get(ChatPrefixSuffixListener::class.java),
            KoinJavaComponent.get(ItemDurabilityListener::class.java),
            KoinJavaComponent.get(GlideProtectionListener::class.java),
            KoinJavaComponent.get(JoinLocationListener::class.java),
            KoinJavaComponent.get(WorldEditProtectionListener::class.java),
            KoinJavaComponent.get(FrostWalkerProtectionListener::class.java),
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

    private var mythicMobsListenerRef: Listener? = null

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
        // Start active services
        healService.start()
        // Initialize BlueMap integration (registers as RegionChangeListener)
        blueMapIntegrationService
        chunkKeepAliveListener.updateForceLoadedChunks()

        // Register chunk keep-alive as a region change listener for dynamic updates
        repository.addChangeListener(object : RegionChangeListener {
            override fun onRegionChanged(regionId: String) {
                chunkKeepAliveListener.updateForceLoadedChunks()
            }
            override fun onRegionReload() {
                chunkKeepAliveListener.updateForceLoadedChunks()
            }
        })
        logger.info("Protection runtime initialized with {} listener groups", listeners.size)
    }

    override suspend fun shutdown() {
        initialized.set(false)
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
        if (mythicMobsListenerRef != null) return
        val listener = mythicMobsListener ?: run {
            logger.debug("MythicMobs listener not available from Koin")
            return
        }
        manager.registerEvents(listener, plugin)
        mythicMobsListenerRef = listener
        logger.info("MythicMobs mob spawning integration enabled")
    }

    private fun unregisterMythicMobsListener() {
        val listener = mythicMobsListenerRef ?: return
        HandlerList.unregisterAll(listener)
        mythicMobsListenerRef = null
        logger.info("MythicMobs mob spawning integration disabled")
    }
}
