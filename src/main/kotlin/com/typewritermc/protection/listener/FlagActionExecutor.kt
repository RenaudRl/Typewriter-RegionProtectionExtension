package com.typewritermc.protection.listener

import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.FlagEvaluationService
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.settings.ProtectionSettingsRepository
import com.typewritermc.protection.settings.ProtectionSettingsSnapshot
import com.typewritermc.protection.selection.toBukkitLocation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import kotlinx.coroutines.runBlocking

@Singleton
class FlagActionExecutor(
    private val flagEvaluationService: FlagEvaluationService,
) {
    private val logger = LoggerFactory.getLogger("FlagActionExecutor")
    private val plugin: Plugin = Bukkit.getPluginManager().getPlugin("TypeWriter")
        ?: error("TypeWriter plugin is required")

    fun evaluate(context: FlagContext, key: RegionFlagKey): FlagEvaluation {
        if (context.canBypass()) {
            logger.debug(
                "Bypassing flag {} for {} in region {} (ownership override)",
                key.id,
                context.player?.name ?: "system",
                context.region.id
            )
            return FlagEvaluation.Allow
        }
        val evaluation = runBlocking { flagEvaluationService.evaluate(context.toEvaluationContext(), key) }
        when (evaluation) {
            is FlagEvaluation.Denied -> logger.debug(
                "Flag {} denied {} in region {} (reason={})",
                key.id,
                context.event::class.simpleName,
                context.region.id,
                evaluation.reason ?: "unspecified"
            )
            is FlagEvaluation.Modify -> logger.debug(
                "Flag {} modified {} in region {} with metadata keys {}",
                key.id,
                context.event::class.simpleName,
                context.region.id,
                evaluation.metadata.keys
            )
            FlagEvaluation.Allow -> logger.debug(
                "Flag {} explicitly allowed {} in region {}",
                key.id,
                context.event::class.simpleName,
                context.region.id
            )
            FlagEvaluation.Pass -> logger.debug(
                "Flag {} passed for region {} (no binding)",
                key.id,
                context.region.id
            )
        }
        return evaluation
    }

    fun handleDenied(
        context: FlagContext,
        key: RegionFlagKey,
        evaluation: FlagEvaluation.Denied,
        customMessage: Component? = null,
        snapshotOverride: ProtectionSettingsSnapshot? = null,
    ) {
        val player = context.player
        if (player != null) {
            val snapshot = snapshotOverride ?: ProtectionSettingsRepository.snapshot(player)
            if (snapshot.showDeniedMessages) {
                val message = customMessage ?: snapshot.deniedAction(evaluation.reason)
                ProtectionMessageDispatcher.send(plugin, player, message, snapshot.deniedMessageChannels, snapshot)
            }
        }
        logger.debug(
            "Denied {} for {} in region {} via flag {}",
            context.event::class.simpleName,
            context.player?.name ?: "unknown",
            context.region.id,
            key.id
        )
    }

    fun applyModifications(context: FlagContext, evaluation: FlagEvaluation.Modify) {
        applyTeleport(context)
        applyMessage(context, evaluation)
        applyCommands(context, evaluation)
        applySounds(context, evaluation)
    }

    fun evaluateMessage(context: FlagContext, key: RegionFlagKey): Component? {
        val evaluation = evaluate(context, key)
        return (evaluation as? FlagEvaluation.Modify)?.let { messageFromMetadata(it) }
    }

    private fun applyTeleport(context: FlagContext) {
        val player = context.player ?: return
        val target = context.runtimeData["teleport.target"] as? com.typewritermc.core.utils.point.Position ?: return
        val location = target.toBukkitLocation()
        SchedulerCompat.run(plugin, location) {
            if (context.event !is org.bukkit.event.player.PlayerTeleportEvent) {
                player.teleport(location)
            }
        }
        logger.debug(
            "Teleporting {} due to teleport metadata in region {}",
            player.name,
            context.region.id
        )
    }

    private fun applyMessage(context: FlagContext, evaluation: FlagEvaluation.Modify) {
        val player = context.player ?: return
        val message = messageFromMetadata(evaluation) ?: return
        val snapshot = ProtectionSettingsRepository.snapshot(player)
        ProtectionMessageDispatcher.send(plugin, player, message, snapshot.customMessageChannels, snapshot)
        logger.debug(
            "Sent custom message to {} due to flag modifications in region {}",
            player.name,
            context.region.id
        )
    }

    private fun applyCommands(context: FlagContext, evaluation: FlagEvaluation.Modify) {
        val consoleCommands = evaluation.metadata["commands.console"] as? Collection<*>
        if (!consoleCommands.isNullOrEmpty()) {
            SchedulerCompat.run(plugin, context.location) {
                val sender = Bukkit.getConsoleSender()
                consoleCommands.filterIsInstance<String>().forEach { command ->
                    Bukkit.dispatchCommand(sender, command)
                }
            }
            logger.debug(
                "Executed {} console command(s) for region {}",
                consoleCommands.size,
                context.region.id
            )
        }
        val playerCommands = evaluation.metadata["commands.player"] as? Collection<*>
        val player = context.player
        if (player != null && !playerCommands.isNullOrEmpty()) {
            SchedulerCompat.run(plugin, player.location) {
                playerCommands.filterIsInstance<String>().forEach { command ->
                    player.performCommand(command)
                }
            }
            logger.debug(
                "Executed {} player command(s) for {} in region {}",
                playerCommands.size,
                player.name,
                context.region.id
            )
        }
    }

    private fun applySounds(context: FlagContext, evaluation: FlagEvaluation.Modify) {
        val player = context.player ?: return
        val sounds = evaluation.metadata["sounds"] as? Collection<*>
        if (sounds.isNullOrEmpty()) return
        SchedulerCompat.run(plugin, player.location) {
            sounds.forEach { payload ->
                when (payload) {
                    is org.bukkit.Sound -> player.playSound(player.location, payload, 1.0f, 1.0f)
                    is com.typewritermc.protection.flags.FlagValue.SoundValue -> player.playSound(
                        player.location,
                        payload.sound,
                        payload.volume,
                        payload.pitch
                    )
                    is Map<*, *> -> {
                        val key = payload["sound"] as? String ?: return@forEach
                        val volume = (payload["volume"] as? Number)?.toFloat() ?: 1.0f
                        val pitch = (payload["pitch"] as? Number)?.toFloat() ?: 1.0f
                        player.playSound(player.location, key, volume, pitch)
                    }
                }
            }
        }
        logger.debug(
            "Played {} sound payload(s) for {} in region {}",
            sounds.size,
            player.name,
            context.region.id
        )
    }

    private fun messageFromMetadata(evaluation: FlagEvaluation.Modify): Component? {
        val component = evaluation.metadata["message.component"] as? Component
        val text = evaluation.metadata["message"] as? String
        return component ?: text?.let { Component.text(it, NamedTextColor.GOLD) }
    }
}
