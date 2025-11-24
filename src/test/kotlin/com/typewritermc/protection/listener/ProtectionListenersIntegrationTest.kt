package com.typewritermc.protection.listener

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.typewritermc.core.utils.point.Position
import com.typewritermc.protection.entry.region.RegionDefinitionEntry
import com.typewritermc.protection.flags.FlagBinding
import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.FlagEvaluationContext
import com.typewritermc.protection.flags.FlagEvaluationService
import com.typewritermc.protection.flags.FlagValue
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.listener.FlagActionExecutor
import com.typewritermc.protection.listener.combat.CombatProtectionListener
import com.typewritermc.protection.listener.interaction.InteractionProtectionListener
import com.typewritermc.protection.listener.movement.EntryDecision
import com.typewritermc.protection.listener.movement.MovementProtectionListener
import com.typewritermc.protection.selection.CuboidShape
import com.typewritermc.protection.service.runtime.ProtectionRuntimeService
import com.typewritermc.protection.service.storage.RegionModel
import com.typewritermc.protection.service.storage.RegionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.slf4j.LoggerFactory

class ProtectionListenersIntegrationTest : FunSpec({
    lateinit var server: ServerMock
    lateinit var evaluationService: FlagEvaluationService
    lateinit var repository: RegionRepository

    beforeTest {
        server = MockBukkit.mock()
        MockBukkit.createMockPlugin("TypeWriter")
        evaluationService = mockk(relaxed = true)
        repository = mockk(relaxed = true)
    }

    afterTest {
        MockBukkit.unmock()
    }

    test("interaction listener denies, emits Adventure feedback and logs debug output") {
        val region = region("spawn", 5, listOf(FlagBinding(RegionFlagKey.INTERACT, FlagValue.Boolean(false))))
        val interactPosition = slot<Position>()
        every { repository.regionsAt(capture(interactPosition)) } returns listOf(region)
        val actionExecutor = FlagActionExecutor(evaluationService)
        val listener = InteractionProtectionListener(repository, actionExecutor)
        val player = server.addPlayer("Builder")
        val event = PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_AIR,
            ItemStack(Material.STONE),
            null,
            BlockFace.SELF,
            EquipmentSlot.HAND,
        )
        coEvery { evaluationService.evaluate(any<FlagEvaluationContext>(), RegionFlagKey.INTERACT) } returns FlagEvaluation.Denied("interaction.blocked")

        val logger = captureLogger("FlagActionExecutor")
        try {
            listener.onInteract(event)
            event.isCancelled shouldBe true
            server.scheduler.performOneTick()

            val message = player.pollNextComponentMessage()
            message.shouldNotBeNull()
            val rendered = PlainTextComponentSerializer.plainText().serialize(message)
            rendered.lowercase() shouldContain "denied action"
            rendered shouldContain "interaction.blocked"
            val interactionLogMatched = logger.events.any { it.formatted().contains("Flag interact denied") }
            interactionLogMatched shouldBe true
        } finally {
            logger.close()
        }
    }

    test("movement listener blocks teleportation and surfaces diagnostics") {
        val region = region("sanctuary", 8, emptyList())
        val runtimeService = mockk<ProtectionRuntimeService>()
        val player = server.addPlayer("Traveler")
        val from = player.location
        val to = from.clone().add(5.0, 0.0, 5.0)
        val event = PlayerTeleportEvent(player, from, to)
        val fromSlot = slot<Position>()
        val toSlot = slot<Position>()
        val eventSlot = slot<PlayerTeleportEvent>()
        every {
            runtimeService.enforceEntry(
                player,
                capture(fromSlot),
                capture(toSlot),
                capture(eventSlot),
            )
        } returns EntryDecision.Blocked(
            region,
            RegionFlagKey.ENTRY,
            FlagEvaluation.Denied("entry.denied"),
        )
        val movementPosition = slot<Position>()
        every { repository.regionsAt(capture(movementPosition)) } returns listOf(region)
        val actionExecutor = FlagActionExecutor(evaluationService)
        val listener = MovementProtectionListener(repository, actionExecutor, runtimeService)

        coEvery { evaluationService.evaluate(any<FlagEvaluationContext>(), RegionFlagKey.ENTRY_ACTION) } returns FlagEvaluation.Pass
        val movementLogger = captureLogger("MovementProtectionListener")
        val executorLogger = captureLogger("FlagActionExecutor")
        try {
            listener.onTeleport(event)
            event.isCancelled shouldBe true
            server.scheduler.performOneTick()

            val component = player.pollNextComponentMessage()
            component.shouldNotBeNull()
            val rendered = PlainTextComponentSerializer.plainText().serialize(component)
            rendered shouldContain "Entry denied"
            rendered.lowercase() shouldContain "sanctuary"
            val movementMatched = movementLogger.events.any { it.formatted().contains("Teleport for Traveler cancelled") }
            movementMatched shouldBe true
            val executorMatched = executorLogger.events.any { it.formatted().contains("Denied PlayerTeleportEvent") }
            executorMatched shouldBe true
        } finally {
            movementLogger.close()
            executorLogger.close()
        }
    }

    test("combat listener applies modifications, notifies attacker and logs changes") {
        val region = region("arena", 12, emptyList())
        val combatPosition = slot<Position>()
        every { repository.regionsAt(capture(combatPosition)) } returns listOf(region)
        val attacker = server.addPlayer("Gladiator")
        val victim = server.addPlayer("Target")
        val event = EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 12.0)
        val pvpContext = slot<FlagEvaluationContext>()
        coEvery { evaluationService.evaluate(capture(pvpContext), RegionFlagKey.PVP) } answers {
            val context = pvpContext.captured
            if (context.flag.player == attacker) {
                FlagEvaluation.Modify(
                    mapOf(
                        "damage.multiplier" to 0.5,
                        "message" to "Damage reduced by arena rules",
                    ),
                )
            } else {
                FlagEvaluation.Pass
            }
        }
        val mobContext = slot<FlagEvaluationContext>()
        coEvery { evaluationService.evaluate(capture(mobContext), RegionFlagKey.MOB_DAMAGE) } returns FlagEvaluation.Pass
        val actionExecutor = FlagActionExecutor(evaluationService)
        val listener = CombatProtectionListener(repository, actionExecutor)

        val logger = captureLogger("FlagActionExecutor")
        try {
            listener.onDamage(event)
            server.scheduler.performOneTick()

            event.damage shouldBe (12.0 * 0.5) plusOrMinus 0.0001
            val message = attacker.pollNextComponentMessage()
            message.shouldNotBeNull()
            PlainTextComponentSerializer.plainText().serialize(message) shouldContain "Damage reduced"
            val combatLogMatched = logger.events.any { it.formatted().contains("modified EntityDamageByEntityEvent") }
            combatLogMatched shouldBe true
        } finally {
            logger.close()
        }
    }
})

private fun region(id: String, priority: Int, bindings: List<FlagBinding>): RegionModel {
    val definition = RegionDefinitionEntry(id = id, flags = bindings)
    return RegionModel(
        regionId = id,
        definition = definition,
        artifact = null,
        shape = CuboidShape(),
        owners = emptySet(),
        members = emptySet(),
        groups = emptySet(),
        priority = priority,
        flags = bindings,
        parentId = null,
        children = emptySet(),
    )
}

private data class CapturedLogger(val logger: Logger, val appender: ListAppender<ILoggingEvent>) {
    val events: List<ILoggingEvent> get() = appender.list

    fun close() {
        logger.detachAppender(appender)
        appender.stop()
        appender.list.clear()
    }
}

private fun captureLogger(name: String): CapturedLogger {
    val logger = LoggerFactory.getLogger(name) as Logger
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    logger.addAppender(appender)
    return CapturedLogger(logger, appender)
}

private fun ILoggingEvent.formatted(): String {
    return runCatching {
        this::class.java.getMethod("getFormattedMessage").invoke(this) as? String
    }.getOrNull()
        ?: runCatching { this::class.java.getMethod("getMessage").invoke(this) as? String }.getOrNull()
        ?: toString()
}

private fun PlayerMock.pollNextComponentMessage(): Component? {
    val direct = runCatching {
        val method = PlayerMock::class.java.getMethod("nextComponentMessage")
        method.invoke(this) as? Component
    }.getOrNull()
    if (direct != null) {
        return direct
    }
    return runCatching {
        val field = PlayerMock::class.java.getDeclaredField("componentMessages")
        field.isAccessible = true
        val queue = field.get(this) as? java.util.Queue<*>
        queue?.poll() as? Component
    }.getOrNull()
}

