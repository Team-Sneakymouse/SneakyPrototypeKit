package net.sneakyprototypekit.util

import com.sneakydb.SneakyDBApi
import java.util.concurrent.CompletableFuture
import kotlin.test.*

class PrototypeKitLogTest {
    private class Database : SneakyDBApi {
        val ready = CompletableFuture<Void>()
        val writes = mutableListOf<Pair<String, List<*>>>()
        var result = CompletableFuture.completedFuture(1)
        var submissionFailure: RuntimeException? = null
        override fun whenReady() = ready.copy()
        override fun isAvailable() = ready.isDone && !ready.isCompletedExceptionally
        override fun query(sql: String, parameters: List<*>) = error("No reads expected")
        override fun execute(sql: String, parameters: List<*>): CompletableFuture<Int> {
            submissionFailure?.let { throw it }
            writes.add(sql to parameters)
            return result
        }
    }

    private val record = PrototypeKitRecord("ITEM", "heal", "Alex", "Kit's name", "STICK", 12, "line 1\nline 2")

    @Test
    fun `waits for readiness then inserts exactly one schema-qualified parameterized log`() {
        val database = Database()
        val logger = PrototypeKitLog(database)
        val saved = logger.log(record)
        assertFalse(saved.isDone)
        assertTrue(database.writes.isEmpty())
        database.ready.complete(null)
        assertEquals(1, saved.join())
        val (sql, parameters) = database.writes.single()
        assertContains(sql, "INSERT INTO lom.lom2_prototype_kit_items")
        assertFalse(sql.contains(record.name))
        assertEquals(listOf(record.type, record.ability, record.creator, record.name, record.material, 12, record.lore), parameters.drop(1))
        assertTrue((parameters.first() as String).matches(Regex("[a-z0-9]{15}")))
        assertEquals(8, sql.count { it == '?' })
        assertEquals(2, Regex("CURRENT_TIMESTAMP").findAll(sql).count())
    }

    @Test
    fun `disable rejects waiting logs and fences later readiness`() {
        val database = Database()
        val logger = PrototypeKitLog(database)
        val saved = logger.log(record)
        logger.close()
        assertTrue(saved.isCompletedExceptionally)
        assertFalse(database.ready.isDone)
        database.ready.complete(null)
        assertTrue(database.writes.isEmpty())
        assertTrue(logger.log(record).isCompletedExceptionally)
    }

    @Test
    fun `readiness failure rejects waiting logs without executing SQL`() {
        val database = Database()
        val saved = PrototypeKitLog(database).log(record)
        database.ready.completeExceptionally(IllegalStateException("initialization failed"))
        assertTrue(saved.isCompletedExceptionally)
        assertTrue(database.writes.isEmpty())
    }

    @Test
    fun `asynchronous insert failure propagates without retry`() {
        val database = Database().apply { ready.complete(null); result = CompletableFuture() }
        val saved = PrototypeKitLog(database).log(record)
        assertFalse(saved.isDone)
        database.result.completeExceptionally(IllegalStateException("uncertain write"))
        assertTrue(saved.isCompletedExceptionally)
        assertEquals(1, database.writes.size)
    }

    @Test
    fun `synchronous submission rejection becomes a failed future`() {
        val database = Database().apply {
            ready.complete(null)
            submissionFailure = IllegalStateException("rejected")
        }
        assertTrue(PrototypeKitLog(database).log(record).isCompletedExceptionally)
    }

    @Test
    fun `optional fields normalize like PocketBase and each log has a fresh compatible ID`() {
        val database = Database().apply { ready.complete(null) }
        val logger = PrototypeKitLog(database)
        listOf("ITEM", "FOOD", "DRINK").forEach { type ->
            assertEquals(1, logger.log(record.copy(type = type, model_data = null, lore = null)).join())
        }
        assertEquals(listOf("ITEM", "FOOD", "DRINK"), database.writes.map { it.second[1] })
        assertEquals(3, database.writes.map { it.second[0] }.toSet().size)
        database.writes.forEach { (_, parameters) ->
            assertTrue((parameters[0] as String).matches(Regex("[a-z0-9]{15}")))
            assertEquals(0, parameters[6])
            assertEquals("", parameters[7])
        }
    }

    @Test
    fun `shutdown does not cancel or retry an already submitted database write`() {
        val database = Database().apply { ready.complete(null); result = CompletableFuture() }
        val logger = PrototypeKitLog(database)
        val saved = logger.log(record)
        logger.close()
        assertTrue(saved.isCompletedExceptionally)
        assertFalse(database.result.isCancelled)
        database.result.complete(1)
        assertTrue(saved.isCompletedExceptionally)
        assertEquals(1, database.writes.size)
    }

    @Test
    fun `fresh enable does not revive a disabled logger`() {
        val database = Database()
        val old = PrototypeKitLog(database)
        val stale = old.log(record)
        old.close()
        val fresh = PrototypeKitLog(database)
        val current = fresh.log(record)
        database.ready.complete(null)
        assertTrue(stale.isCompletedExceptionally)
        assertEquals(1, current.join())
        assertEquals(1, database.writes.size)
    }

    @Test
    fun `pending and in-flight logs are bounded even if callers cancel their futures`() {
        val database = Database().apply { result = CompletableFuture() }
        val logger = PrototypeKitLog(database)
        val waiting = List(64) { logger.log(record) }
        waiting.forEach { it.cancel(false) }
        assertTrue(logger.log(record).isCompletedExceptionally, "Cancellation must not bypass the capacity bound")
        database.ready.complete(null)
        assertEquals(64, database.writes.size)
        assertTrue(logger.log(record).isCompletedExceptionally)
        database.result.complete(1)
        assertEquals(1, logger.log(record).join(), "Completion releases capacity")
    }

    @Test
    fun `failures expose safe distinct categories without driver details`() {
        fun category(future: CompletableFuture<Int>): String {
            val failure = assertFails { future.join() }
            assertFalse(generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("password=secret") })
            return PrototypeKitLog.failureCategory(failure).name
        }
        val pending = Database()
        val waiting = PrototypeKitLog(pending)
        val initial = waiting.log(record)
        pending.ready.completeExceptionally(IllegalStateException("password=secret"))
        assertEquals("READINESS", category(initial))
        waiting.close()
        assertEquals("DISABLED", category(waiting.log(record)))

        val full = PrototypeKitLog(Database())
        repeat(64) { full.log(record) }
        assertEquals("CAPACITY", category(full.log(record)))
        full.close()

        val rejected = Database().apply {
            ready.complete(null)
            result = CompletableFuture.failedFuture(IllegalStateException("password=secret"))
        }
        assertEquals("INSERT", category(PrototypeKitLog(rejected).log(record)))
        rejected.result = CompletableFuture.completedFuture(0)
        assertEquals("ROW_COUNT", category(PrototypeKitLog(rejected).log(record)))
    }

    @Test
    fun `insert must confirm exactly one affected row`() {
        for (count in listOf(0, 2)) {
            val database = Database().apply { ready.complete(null); result = CompletableFuture.completedFuture(count) }
            assertTrue(PrototypeKitLog(database).log(record).isCompletedExceptionally, "Affected rows: $count")
            assertEquals(1, database.writes.size)
        }
    }
}
