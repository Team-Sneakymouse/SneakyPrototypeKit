package net.sneakyprototypekit.util

import com.sneakydb.SneakyDBApi
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** Insert-only consumer; the schema and historical migration are managed externally. */
class PrototypeKitLog(private val database: SneakyDBApi) : AutoCloseable {
    enum class FailureCategory { DISABLED, CAPACITY, READINESS, INSERT, ROW_COUNT }

    private class LogFailure(val category: FailureCategory) : IllegalStateException("Item log failure: $category")

    private val ready = database.whenReady().copy()
    private val lock = Any()
    private var stopped = false
    private val pending = HashSet<CompletableFuture<Int>>()

    fun log(record: PrototypeKitRecord): CompletableFuture<Int> {
        val result = CompletableFuture<Int>()
        synchronized(lock) {
            if (stopped) return CompletableFuture.failedFuture(LogFailure(FailureCategory.DISABLED))
            if (pending.size >= 64) {
                return CompletableFuture.failedFuture(LogFailure(FailureCategory.CAPACITY))
            }
            pending.add(result)
        }
        result.whenComplete { _, _ -> synchronized(lock) { pending.remove(result) } }
        ready.handle { _, failure ->
            if (failure != null) throw LogFailure(FailureCategory.READINESS)
        }.thenCompose {
            // Serialize admission with close(); execute only submits work, never waits for JDBC.
            synchronized(lock) {
                if (stopped || result.isDone) {
                    CompletableFuture.failedFuture(LogFailure(FailureCategory.DISABLED))
                } else {
                    database.execute(
                        INSERT,
                        listOf(newId(), record.type, record.ability, record.creator, record.name,
                            record.material, record.model_data ?: 0, record.lore ?: "")
                    )
                }
            }
        }.whenComplete { count, failure ->
            if (failure != null) result.completeExceptionally(LogFailure(failureCategory(failure)))
            else if (count != 1) result.completeExceptionally(LogFailure(FailureCategory.ROW_COUNT))
            else result.complete(count)
        }
        // Caller cancellation/completion cannot release capacity or pretend a write committed.
        return result.copy()
    }

    override fun close() {
        val waiting = synchronized(lock) {
            stopped = true
            pending.toList()
        }
        val failure = LogFailure(FailureCategory.DISABLED)
        waiting.forEach { it.completeExceptionally(failure) }
        // This is our own copy, never the manager's readiness future.
        ready.completeExceptionally(failure)
    }

    companion object {
        /** Only known categories cross the diagnostic boundary; driver messages/causes stay private. */
        @JvmStatic
        fun failureCategory(failure: Throwable): FailureCategory {
            var cause = failure
            while (cause is CompletionException && cause.cause != null) cause = cause.cause!!
            return (cause as? LogFailure)?.category ?: FailureCategory.INSERT
        }

        private val random = SecureRandom()
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val INSERT = """
            INSERT INTO lom.lom2_prototype_kit_items
                (id, type, ability, creator, name, material, model_data, lore, created, updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """.trimIndent()

        private fun newId(): String = buildString(15) {
            repeat(15) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }
}
