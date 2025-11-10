package com.typewritermc.protection.flags

import java.lang.ThreadLocal
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

/**
 * Immutable snapshot describing the actor and positions involved in a flag evaluation.
 */
data class FlagContext(
    val player: Player?,
    val sourceEntity: Entity?,
    val targetEntity: Entity?,
    val origin: Location?,
    val destination: Location?,
    val action: FlagAction,
)

/** Identifies the high level action being evaluated (e.g. "protection:block-break"). */
@JvmInline
value class FlagAction(val id: String) {
    init {
        require(id.isNotBlank()) { "Flag action identifier must not be blank." }
        require(':' in id) { "Flag action identifier must contain a namespace (source:action)." }
    }

    override fun toString(): String = id

    companion object {
        val UNKNOWN = FlagAction("internal:unknown")
    }
}

/**
 * Pool of mutable contexts allowing listeners to reuse instances for frequent events.
 */
object FlagContextPool {
    private const val MAX_POOL_SIZE = 64
    private val localPool = ThreadLocal.withInitial { ArrayDeque<PooledFlagContext>(MAX_POOL_SIZE) }

    fun borrow(): PooledFlagContext {
        val pool = localPool.get()
        val context = if (pool.isEmpty()) PooledFlagContext() else pool.removeLast()
        context.prepare()
        return context
    }

    internal fun recycle(context: PooledFlagContext) {
        val pool = localPool.get()
        if (pool.size < MAX_POOL_SIZE) {
            pool.addLast(context)
        }
    }

    class PooledFlagContext internal constructor() : AutoCloseable {
        var player: Player? = null
        var sourceEntity: Entity? = null
        var targetEntity: Entity? = null
        var origin: Location? = null
        var destination: Location? = null
        var action: FlagAction = FlagAction.UNKNOWN

        private var closed = false

        internal fun prepare() {
            closed = false
        }

        fun toFlagContext(): FlagContext {
            return FlagContext(player, sourceEntity, targetEntity, origin, destination, action)
        }

        override fun close() {
            if (closed) return
            closed = true
            reset()
            recycle()
        }

        private fun reset() {
            player = null
            sourceEntity = null
            targetEntity = null
            origin = null
            destination = null
            action = FlagAction.UNKNOWN
        }

        private fun recycle() {
            FlagContextPool.recycle(this)
        }
    }
}
