package com.typewritermc.protection.command

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.typewritermc.core.entries.Entry
import com.typewritermc.core.entries.Query
import com.typewritermc.engine.paper.command.dsl.ArgumentBlock
import com.typewritermc.engine.paper.command.dsl.CommandTree
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

inline fun <reified E : Entry> CommandTree.compatEntry(
    name: String,
    noinline filter: (E) -> Boolean = { true },
    noinline block: ArgumentBlock<CommandSourceStack, E> = {},
) = compatEntryWithClass(name, E::class, filter, block)

fun <E : Entry> CommandTree.compatEntryWithClass(
    name: String,
    klass: KClass<E>,
    filter: (E) -> Boolean = { true },
    block: ArgumentBlock<CommandSourceStack, E> = {},
) = argument(name, CompatEntryArgumentType(klass, filter), klass, block)

private class CompatEntryArgumentType<E : Entry>(
    private val klass: KClass<E>,
    private val filter: (E) -> Boolean,
) : CustomArgumentType.Converted<E, String> {
    override fun convert(nativeType: String): E {
        val entry = Query.findById(klass, nativeType)
            ?: Query.findByName(klass, nativeType)
            ?: throw SimpleCommandExceptionType(LiteralMessage("Could not find entry $nativeType")).create()
        if (!filter(entry)) {
            throw SimpleCommandExceptionType(LiteralMessage("Entry '$nativeType' did not pass filter")).create()
        }
        return entry
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val input = builder.remaining
        Query.findWhere(klass) { entry ->
            if (!filter(entry)) return@findWhere false
            entry.name.startsWith(input) || (input.length > 3 && entry.id.startsWith(input))
        }.forEach { entry ->
            builder.suggest(entry.name)
        }
        return builder.buildFuture()
    }
}
