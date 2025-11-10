package com.typewritermc.protection.flags

import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.listener.FlagContext as ListenerFlagContext
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.service.storage.RegionModel
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.bukkit.event.Event

class RegionFlagRegistryTest {
    private val registry = RegionFlagRegistry()

    @Test
    fun definitionExposesMetadata() {
        val definition = registry.definition(RegionFlagKey.BUILD)
        assertNotNull(definition)
        assertEquals(FlagEvaluationPriority.HIGH, definition.evaluationPriority)
        assertEquals(FlagInheritance.ALWAYS, definition.inheritance)
        assertEquals(PaperCompatibility.FOLIA_SAFE, definition.paperCompatibility)
        assertEquals(FlagValueKind.BOOLEAN, definition.valueKind)
        assertEquals(FlagValue.Boolean::class, definition.valueType)
    }

    @Test
    fun handlerEntryValidatesType() = runTest {
        val entry = registry.handlerEntry(RegionFlagKey.TELEPORT_ON_ENTRY)
        assertNotNull(entry)

        val region = regionModel("spawn")
        val context = FlagEvaluationContext(
            ListenerFlagContext(
                region = region,
                event = mockk<Event>(relaxed = true),
            )
        )
        val definition = requireNotNull(registry.definition(RegionFlagKey.TELEPORT_ON_ENTRY))

        val locationBinding = ResolvedFlagBinding(
            regionId = region.id,
            priority = region.priority,
            binding = FlagBinding(RegionFlagKey.TELEPORT_ON_ENTRY, FlagValue.LocationValue()),
        )
        val booleanBinding = ResolvedFlagBinding(
            regionId = region.id,
            priority = region.priority,
            binding = FlagBinding(RegionFlagKey.TELEPORT_ON_ENTRY, FlagValue.Boolean()),
        )
        assertTrue(booleanBinding.binding.value is FlagValue.LocationValue)

        assertNotNull(
            entry.evaluate(context, RegionFlagKey.TELEPORT_ON_ENTRY, locationBinding, definition)
        )
        assertNotNull(entry.evaluate(context, RegionFlagKey.TELEPORT_ON_ENTRY, booleanBinding, definition))
    }
}

private fun regionModel(id: String): RegionModel {
    val definition = RegionDefinitionEntry(id = id)
    return RegionModel(
        regionId = id,
        definition = definition,
        artifact = null,
        shape = CuboidShape(),
        owners = emptySet(),
        members = emptySet(),
        groups = emptySet(),
        priority = 1,
        flags = emptyList(),
        parentId = null,
        children = emptySet(),
    )
}
