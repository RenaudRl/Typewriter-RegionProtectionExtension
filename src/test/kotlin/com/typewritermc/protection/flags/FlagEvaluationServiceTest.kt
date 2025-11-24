package com.typewritermc.protection.flags

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import com.typewritermc.protection.listener.FlagContext as ListenerFlagContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.Event
import org.slf4j.LoggerFactory

class FlagEvaluationServiceTest : FunSpec({
    val repository = mockk<RegionRepository>()
    val registry = RegionFlagRegistry()

    beforeEach {
        registry.registerHandler(RegionFlagKey.INTERACT, FlagHandler { _, _ -> FlagEvaluation.pass() })
        registry.registerHandler(RegionFlagKey.ITEM_DROP, FlagHandler { _, _ -> FlagEvaluation.pass() })
    }

    test("inherits parent bindings and caches resolved graphs") {
        val parentBinding = FlagBinding(RegionFlagKey.BUILD, FlagValue.Boolean(false))
        val parentRegion = region(
            id = "parent",
            definition = RegionDefinitionEntry(id = "parent", flags = listOf(parentBinding)),
            priority = 1,
            children = setOf("child"),
        )
        val childRegion = region(
            id = "child",
            definition = RegionDefinitionEntry(id = "child", flags = emptyList()),
            priority = 10,
            parentId = "parent",
        )

        every { repository.findById("parent") } returns parentRegion
        every { repository.findById("child") } returns childRegion

        val service = FlagEvaluationService(repository, registry)
        val context = evaluationContext(childRegion)

        val first = service.evaluate(context, RegionFlagKey.BUILD)
        first.shouldBeInstanceOf<FlagEvaluation.Denied>()

        val second = service.evaluate(context, RegionFlagKey.BUILD)
        second.shouldBeInstanceOf<FlagEvaluation.Denied>()

        verify(exactly = 1) { repository.findById("parent") }
    }

    test("evaluates bindings by descending region priority") {
        val visited = mutableListOf<Int>()
        registry.registerHandler(RegionFlagKey.INTERACT, FlagHandler { _, binding ->
            visited += binding.priority
            FlagEvaluation.pass()
        })
        val rootBinding = FlagBinding(RegionFlagKey.INTERACT, FlagValue.Boolean(false))
        val midBinding = FlagBinding(RegionFlagKey.INTERACT, FlagValue.Boolean(true))
        val leafBinding = FlagBinding(RegionFlagKey.INTERACT, FlagValue.Boolean(false))
        val root = region(
            id = "root",
            definition = RegionDefinitionEntry(id = "root", flags = listOf(rootBinding)),
            priority = 30,
            children = setOf("mid"),
        )
        val mid = region(
            id = "mid",
            definition = RegionDefinitionEntry(id = "mid", flags = listOf(midBinding)),
            priority = 20,
            parentId = "root",
            children = setOf("leaf"),
        )
        val leaf = region(
            id = "leaf",
            definition = RegionDefinitionEntry(id = "leaf", flags = listOf(leafBinding)),
            priority = 10,
            parentId = "mid",
        )

        every { repository.findById("root") } returns root
        every { repository.findById("mid") } returns mid
        every { repository.findById("leaf") } returns leaf

        val service = FlagEvaluationService(repository, registry)
        service.evaluate(evaluationContext(leaf), RegionFlagKey.INTERACT)

        visited.shouldContainExactly(listOf(30, 20, 10))
    }

    test("skips incompatible binding types while logging diagnostics") {
        val appender = installLogAppender("FlagEvaluationService")
        try {
            registry.registerHandler(RegionFlagKey.ITEM_DROP, FlagHandler<FlagValue.IntValue> { _, _ ->
                FlagEvaluation.deny("int-only")
            })
            val region = region(
                id = "demo",
                definition = RegionDefinitionEntry(
                    id = "demo",
                    flags = listOf(FlagBinding(RegionFlagKey.ITEM_DROP, FlagValue.Boolean(true))),
                ),
                priority = 5,
            )
            every { repository.findById("demo") } returns region

            val service = FlagEvaluationService(repository, registry)
            val result = service.evaluate(evaluationContext(region), RegionFlagKey.ITEM_DROP)

            result shouldBe FlagEvaluation.Pass
            val log = appender.list.singleOrNull { it.level.toString() == "DEBUG" }
            log.shouldNotBeNull()
            val formatted = log.formattedMessage
            formatted shouldContain "Skipping flag item-drop binding from demo"
            formatted shouldContain "Boolean"
        } finally {
            appender.stop()
            appender.list.clear()
        }
    }

    test("invalidate cascades through the hierarchy and emits update events") {
        val parent = region(
            id = "parent",
            definition = RegionDefinitionEntry(id = "parent", flags = emptyList()),
            priority = 1,
            children = setOf("child"),
        )
        val child = region(
            id = "child",
            definition = RegionDefinitionEntry(id = "child", flags = emptyList()),
            priority = 2,
        )
        every { repository.findById("parent") } returns parent
        every { repository.findById("child") } returns child

        val service = FlagEvaluationService(repository, registry)
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            service.updates.take(2).toList()
        }

        service.invalidate("parent")

        val collected = withTimeout(1_000) { events.await() }
        collected shouldBe listOf(
            FlagUpdateEvent.Region("child"),
            FlagUpdateEvent.Region("parent"),
        )
    }
})

private fun evaluationContext(region: RegionModel): FlagEvaluationContext {
    val world = mockk<World>(relaxed = true)
    val location = Location(world, 0.0, 64.0, 0.0)
    val listenerContext = ListenerFlagContext(
        region = region,
        event = mockk<Event>(relaxed = true),
        location = location,
        player = null,
        source = null,
        target = null,
    )
    return FlagEvaluationContext(listenerContext)
}

private fun region(
    id: String,
    definition: RegionDefinitionEntry,
    priority: Int,
    parentId: String? = null,
    children: Set<String> = emptySet(),
): RegionModel {
    return RegionModel(
        regionId = id,
        definition = definition,
        artifact = null,
        shape = CuboidShape(),
        owners = emptySet(),
        members = emptySet(),
        groups = emptySet(),
        priority = priority,
        flags = definition.flags,
        parentId = parentId,
        children = children,
    )
}

private fun installLogAppender(loggerName: String): ListAppender<ILoggingEvent> {
    val logger = LoggerFactory.getLogger(loggerName) as Logger
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    logger.addAppender(appender)
    return appender
}

