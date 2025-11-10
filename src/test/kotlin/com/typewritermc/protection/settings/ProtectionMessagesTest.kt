package com.typewritermc.protection.settings

import com.typewritermc.protection.selection.SelectionMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class ProtectionMessagesTest : FunSpec({
    val plain = PlainTextComponentSerializer.plainText()

    test("selection mode display names use configured templates") {
        val messages = ProtectionMessages.ModeMessages(
            cuboid = ProtectionMessages.ModeMessages.ModeEntry(
                name = "<gold>Custom Cuboid</gold>",
                description = "<gray>Custom description</gray>",
            ),
            polygon = ProtectionMessages.ModeMessages.ModeEntry(
                name = "<aqua>Polygon</aqua>",
                description = "<gray>Polygon description</gray>",
            )
        )

        plain.serialize(SelectionMode.CUBOID.displayName(messages)) shouldBe "Custom Cuboid"
        plain.serialize(SelectionMode.CUBOID.description(messages)) shouldBe "Custom description"
    }

    test("message renderer replaces placeholders with values") {
        val listMessages = ProtectionMessages().commands.list
        val component = ProtectionMessageRenderer.render(listMessages.header, mapOf("count" to 3))
        plain.serialize(component!!) shouldBe "Protected regions (3):"
    }

    test("message renderer accepts component placeholders") {
        val flagsMessages = ProtectionMessages().commands.flags
        val rendered = ProtectionMessageRenderer.render(
            flagsMessages.resolvedEntry,
            mapOf(
                "flag" to "build",
                "value" to Component.text("true"),
                "source" to "spawn",
                "inherited_marker" to "",
                "override_marker" to ""
            )
        )
        plain.serialize(rendered!!) shouldBe "• build = true (spawn)"
    }
})
