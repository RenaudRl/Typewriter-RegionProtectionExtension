package com.typewritermc.protection.service.runtime

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.RegionFlagRegistry
import com.typewritermc.protection.flags.displayName
import com.typewritermc.protection.flags.formatFlagValue
import com.typewritermc.protection.flags.resolveFlagResolutions
import com.typewritermc.protection.selection.toTWPosition
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import com.typewritermc.protection.settings.ProtectionMessageTemplates
import com.typewritermc.protection.settings.ProtectionMessages
import com.typewritermc.protection.settings.ProtectionSettingsRepository
import com.typewritermc.protection.settings.ProtectionSettingsSnapshot
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@Singleton
class FlagInspectionService {
    private val logger = LoggerFactory.getLogger("FlagInspectionService")
    private val plugin = Bukkit.getPluginManager().getPlugin("TypeWriter")
        ?: error("TypeWriter plugin is required")
    private val repository: RegionRepository = KoinJavaComponent.get(RegionRepository::class.java)
    private val registry: RegionFlagRegistry = KoinJavaComponent.get(RegionFlagRegistry::class.java)
    private val sessions = ConcurrentHashMap<UUID, InspectionSession>()

    fun toggle(player: Player): Boolean {
        val uuid = player.uniqueId
        val existing = sessions.remove(uuid)
        if (existing != null) {
            logger.debug("Stopping flag inspection for {}", player.name)
            val snapshot = existing.snapshot
            existing.stop()
            return false
        }

        val initialSnapshot = ProtectionSettingsRepository.snapshot(player)
        val session = InspectionSession(player, initialSnapshot)
        sessions[uuid] = session
        logger.debug("Starting flag inspection for {}", player.name)
        session.tick(initialSnapshot)
        return true
    }

    fun shutdown() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
    }

    private inner class InspectionSession(
        private val player: Player,
        snapshot: ProtectionSettingsSnapshot,
    ) {
        private var task: BukkitTask? = null
        private var refreshTicks: Long = snapshot.refreshTicks.coerceAtLeast(1)
        private var active = true
        private val bossBars = LinkedHashMap<String, BossBar>()
        private var barColor = snapshot.bossBarColor
        private var barOverlay = snapshot.bossBarOverlay
        private var currentSnapshot: ProtectionSettingsSnapshot = snapshot
        private var lastRegionIds: Set<String> = emptySet()

        val snapshot: ProtectionSettingsSnapshot
            get() = currentSnapshot

        init {
            val initial = BossBar.bossBar(
                snapshot.templates.inspectionInitializingBossBar(),
                1.0f,
                barColor,
                barOverlay
            )
            bossBars[INITIAL_BAR_KEY] = initial
            player.showBossBar(initial)
        }

        fun tick(initialSnapshot: ProtectionSettingsSnapshot? = null) {
            if (!active) return
            if (!player.isOnline) {
                stop()
                return
            }

            val snapshot = initialSnapshot ?: ProtectionSettingsRepository.snapshot(player)
            currentSnapshot = snapshot
            applySnapshot(snapshot)
            refreshTicks = snapshot.refreshTicks.coerceAtLeast(1)
            render(snapshot)
            scheduleNext()
        }

        private fun applySnapshot(snapshot: ProtectionSettingsSnapshot) {
            val updatedColor = snapshot.bossBarColor
            val updatedOverlay = snapshot.bossBarOverlay
            if (barColor == updatedColor && barOverlay == updatedOverlay) return

            barColor = updatedColor
            barOverlay = updatedOverlay
            val replacements = bossBars.mapValues { (_, bar) ->
                player.hideBossBar(bar)
                BossBar.bossBar(bar.name(), bar.progress(), updatedColor, updatedOverlay)
            }
            bossBars.clear()
            replacements.forEach { (key, bar) ->
                bossBars[key] = bar
                player.showBossBar(bar)
            }
        }

        private fun render(snapshot: ProtectionSettingsSnapshot) {
            val regions = repository.regionsAt(player.location.toTWPosition())
            val templates = snapshot.templates
            if (regions.isEmpty()) {
                lastRegionIds = emptySet()
                val bar = ensureBossBar(NO_REGION_BAR_KEY)
                bar.name(templates.inspectionNoRegionBossBar())
                bar.progress(1.0f)
                cleanupBars(setOf(NO_REGION_BAR_KEY))
                return
            }

            notifyRegionEntries(snapshot.messages, regions)

            val activeKeys = LinkedHashSet<String>()
            val cache = mutableMapOf<String, List<com.typewritermc.protection.flags.FlagResolution>>()

            regions.forEachIndexed { index, region ->
                val key = region.regionId
                val bar = ensureBossBar(key)
                val resolutions = resolutionsFor(region, cache)
                bar.name(buildBossText(templates, region, resolutions, index, regions.size))
                bar.progress(1.0f)
                activeKeys += key
            }

            cleanupBars(activeKeys)
        }

        private fun notifyRegionEntries(messages: ProtectionMessages, regions: List<RegionModel>) {
            val currentIds = regions.map { it.regionId }.toSet()
            val newRegions = regions.filter { it.regionId !in lastRegionIds }
            if (newRegions.isNotEmpty()) {
                newRegions.forEach { region ->
                    RegionInfoFormatter.buildRegionInfoLines(region, messages).forEach(player::sendMessage)
                }
            }
            lastRegionIds = currentIds
        }

        private fun resolutionsFor(
            region: RegionModel,
            cache: MutableMap<String, List<com.typewritermc.protection.flags.FlagResolution>>,
        ): List<com.typewritermc.protection.flags.FlagResolution> = cache.getOrPut(region.regionId) {
            resolveFlagResolutions(region, repository, registry)
        }

        private fun buildBossText(
            templates: ProtectionMessageTemplates,
            region: RegionModel,
            resolutions: List<com.typewritermc.protection.flags.FlagResolution>,
            index: Int,
            total: Int,
        ): Component {
            val display = displayName(region)
            val summary = buildBossSummary(templates, resolutions)
            val flags = buildBossFlags(templates, resolutions)
            return templates.inspectionBossBarTitle(index + 1, total, region.priority, display, summary, flags)
        }

        private fun buildBossSummary(
            templates: ProtectionMessageTemplates,
            resolutions: List<com.typewritermc.protection.flags.FlagResolution>,
        ): Component {
            val effective = resolutions.filter { it.isEffective }
            val activeCount = effective.size
            val localCount = effective.count { it.isLocal }
            val inheritedCount = effective.count { it.isInherited }
            val overrideCount = effective.count { it.overridesParent }

            val components = mutableListOf<Component>()
            components += templates.inspectionBossBarSummaryBase(activeCount, localCount)
            if (inheritedCount > 0) {
                components += templates.inspectionBossBarSummaryInherited(inheritedCount)
            }
            if (overrideCount > 0) {
                components += templates.inspectionBossBarSummaryOverrides(overrideCount)
            }

            var summary = Component.empty().append(templates.inspectionBossBarDetailPrefix())
            components.forEach { component ->
                summary = summary.append(component)
            }
            return summary
        }

        private fun buildBossFlags(
            templates: ProtectionMessageTemplates,
            resolutions: List<com.typewritermc.protection.flags.FlagResolution>,
        ): Component {
            val prefix = templates.inspectionBossBarDetailPrefix()
            val effective = resolutions.filter { it.isEffective }
            if (effective.isEmpty()) {
                return Component.empty()
                    .append(prefix)
                    .append(templates.inspectionBossBarNoFlags())
            }

            val separator = templates.inspectionBossBarDetailSeparator()
            var details = Component.empty().append(prefix)
            val entries = effective.mapNotNull { resolution ->
                val effectiveSource = resolution.effective ?: return@mapNotNull null
                templates.inspectionBossBarDetail(
                    resolution.key.id,
                    formatFlagValue(effectiveSource.binding.value),
                    resolution.isInherited,
                    resolution.overridesParent
                )
            }

            entries.take(FLAG_PREVIEW_LIMIT).forEachIndexed { index, detail ->
                if (index > 0) {
                    details = details.append(separator)
                }
                details = details.append(detail)
            }

            if (entries.size > FLAG_PREVIEW_LIMIT) {
                details = details
                    .append(separator)
                    .append(templates.inspectionBossBarDetailMore())
            }

            return details
        }

        private fun ensureBossBar(key: String): BossBar {
            val existing = bossBars[key]
            if (existing != null) {
                return existing
            }
            val bar = BossBar.bossBar(Component.empty(), 1.0f, barColor, barOverlay)
            bossBars[key] = bar
            player.showBossBar(bar)
            return bar
        }

        private fun cleanupBars(activeKeys: Set<String>) {
            val iterator = bossBars.entries.iterator()
            while (iterator.hasNext()) {
                val (key, bar) = iterator.next()
                if (key !in activeKeys) {
                    player.hideBossBar(bar)
                    iterator.remove()
                }
            }
        }

        private fun scheduleNext() {
            task?.cancel()
            val delay = min(refreshTicks, 6000L)
            task = Bukkit.getScheduler().runTaskLater(plugin, Runnable { tick() }, delay)
        }

        fun stop() {
            active = false
            task?.cancel()
            task = null
            if (player.isOnline) {
                bossBars.values.forEach(player::hideBossBar)
            }
            bossBars.clear()
        }
    }

    private companion object {
        private const val INITIAL_BAR_KEY = "__initial__"
        private const val NO_REGION_BAR_KEY = "__no_region__"
        private const val FLAG_PREVIEW_LIMIT = 4
    }
}

