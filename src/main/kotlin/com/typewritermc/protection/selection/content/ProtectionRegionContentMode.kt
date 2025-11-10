package com.typewritermc.protection.selection.content

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.utils.failure
import com.typewritermc.core.utils.ok
import com.typewritermc.engine.paper.content.ContentComponent
import com.typewritermc.engine.paper.content.ContentContext
import com.typewritermc.engine.paper.content.ContentMode
import com.typewritermc.engine.paper.content.components.IntractableItem
import com.typewritermc.engine.paper.content.components.ItemsComponent
import com.typewritermc.engine.paper.content.components.ItemInteractionType
import com.typewritermc.engine.paper.content.components.onInteract
import com.typewritermc.engine.paper.content.entryId
import com.typewritermc.protection.entry.artifact.RegionArtifactEntry
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.selection.SelectionService
import com.typewritermc.protection.settings.ProtectionMessageRenderer
import com.typewritermc.protection.settings.ProtectionMessages
import com.typewritermc.protection.settings.ProtectionSettingsRepository
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.java.KoinJavaComponent
import java.util.UUID

class ProtectionRegionContentMode(context: ContentContext, player: Player) : ContentMode(context, player) {
    private val selectionService: SelectionService = KoinJavaComponent.get(SelectionService::class.java)
    private var sessionId: UUID? = null

    override suspend fun setup(): Result<Unit> {
        val messages = ProtectionSettingsRepository.snapshot(player).messages
        sessionId = null
        val definitionId = context.data["definitionId"] as? String
        if (definitionId != null) {
            val definitionRef = Ref(definitionId, RegionDefinitionEntry::class)
            val definition = definitionRef.get() ?: return failure(
                ProtectionMessageRenderer.renderPlain(
                    messages.commands.general.definitionNotFound,
                    mapOf("definition" to definitionId)
                ) ?: "Could not find region definition '$definitionId'"
            )
            selectionService.startSession(player, definition)
            sessionId = selectionService.currentSessionId(player)
        } else {
            val entryId = context.entryId ?: return failure("No artifact id provided for protection region capture")
            val artifactRef = Ref(entryId, RegionArtifactEntry::class)
            val artifact = artifactRef.get() ?: return failure(
                ProtectionMessageRenderer.renderPlain(
                    messages.commands.general.artifactNotFound,
                    mapOf("artifact" to entryId)
                ) ?: "Could not find artifact '$entryId'"
            )
            selectionService.startArtifactSession(player, artifact, artifact.name.ifBlank { artifact.id })
            sessionId = selectionService.currentSessionId(player)
        }

        +RegionSelectionToolbarComponent(
            selectionService = selectionService,
            onApply = {
                selectionService.complete(player)
                sessionId = selectionService.currentSessionId(player)
            },
            onCancel = {
                selectionService.cancel(player)
                sessionId = null
            },
            messages = messages
        )

        return ok(Unit)
    }

    override suspend fun dispose() {
        sessionId?.let { selectionService.cancel(player, it) }
        sessionId = null
        super.dispose()
    }
}

private class RegionSelectionToolbarComponent(
    private val selectionService: SelectionService,
    private val onApply: () -> Unit,
    private val onCancel: () -> Unit,
    private val messages: ProtectionMessages,
) : ContentComponent, ItemsComponent {
    override suspend fun initialize(player: Player) {}
    override suspend fun tick(player: Player) {}
    override suspend fun dispose(player: Player) {}

    override fun items(player: Player): Map<Int, IntractableItem> {
        val snapshot = selectionService.snapshot(player) ?: return emptyMap()
        val controls = mutableMapOf<Int, IntractableItem>()
        val content = messages.content

        controls[1] = ItemStack(Material.BLAZE_ROD).withDisplay(
            render(content.addPointName) ?: Component.text("Add point", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
            *renderLore(content.addPointLore, listOf(Component.text("Click to capture the targeted block", NamedTextColor.GRAY)))
        ) onInteract { interaction ->
            if (interaction.type.isClick) selectionService.appendPointFromTarget(player)
        }

        controls[2] = ItemStack(Material.REDSTONE).withDisplay(
            render(content.removeLastName) ?: Component.text("Remove last", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
            *renderLore(content.removeLastLore, listOf(Component.text("Click to remove the last point", NamedTextColor.GRAY)))
        ) onInteract { interaction ->
            if (interaction.type.isClick) selectionService.removeLastPoint(player)
        }

        controls[3] = ItemStack(Material.COMPASS).withDisplay(
            snapshot.mode.displayName(messages.modes).decoration(TextDecoration.BOLD, true),
            snapshot.mode.description(messages.modes),
            render(content.modeHint) ?: Component.text("Click to change the selection mode", NamedTextColor.GRAY)
        ) onInteract { interaction ->
            if (interaction.type.isClick) selectionService.cycleMode(player)
        }

        if (snapshot.canApply) {
            controls[7] = ItemStack(Material.LIME_DYE).withDisplay(
                render(content.applyName) ?: Component.text("Apply", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                *renderLore(
                    content.applyLore,
                    listOf(Component.text("Click to save the current selection", NamedTextColor.GRAY))
                )
            ) onInteract { interaction ->
                if (interaction.type.isClick) onApply()
            }
        }

        controls[8] = ItemStack(Material.BARRIER).withDisplay(
            render(content.cancelName) ?: Component.text("Cancel", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
            *renderLore(content.cancelLore, listOf(Component.text("Click to cancel the capture", NamedTextColor.GRAY)))
        ) onInteract { interaction ->
            if (interaction.type == ItemInteractionType.INVENTORY_CLICK || interaction.type.isClick) onCancel()
        }

        return controls
    }

    private fun render(template: String?): Component? = ProtectionMessageRenderer.render(template)

    private fun renderLore(lines: List<String>, fallback: List<Component>): Array<Component> {
        val rendered = lines.mapNotNull { ProtectionMessageRenderer.render(it) }
        return (if (rendered.isEmpty()) fallback else rendered).toTypedArray()
    }
}

private fun ItemStack.withDisplay(name: Component, vararg lore: Component): ItemStack {
    itemMeta = itemMeta.apply {
        displayName(name)
        if (lore.isNotEmpty()) lore(lore.toList())
    }
    return this
}
