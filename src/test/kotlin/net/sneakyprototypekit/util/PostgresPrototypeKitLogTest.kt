package net.sneakyprototypekit.util

import com.sneakydb.SneakyDBApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.DriverManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Applies every jar [db/migrations] script to an empty database (with schema `lom`), then exercises [PrototypeKitLog].
 * Run with `./gradlew postgresTest` and `SNEAKYPROTOTYPEKIT_TEST_PG_URL`.
 */
@Tag("postgres")
class PostgresPrototypeKitLogTest {
    private companion object {
        val MIGRATION = Regex("V0*(\\d+)__[A-Za-z0-9_-]+\\.sql")
    }

    private val url = System.getenv("SNEAKYPROTOTYPEKIT_TEST_PG_URL")
        ?: error("SNEAKYPROTOTYPEKIT_TEST_PG_URL not set")
    private lateinit var itemLog: PrototypeKitLog
    private val workers = Executors.newFixedThreadPool(4)
    private val record = PrototypeKitRecord(
        "ITEM",
        "heal",
        "Alex",
        "Kit's name",
        "STICK",
        "lom:items/test_stick",
        "line one",
    )

    private class Api(private val jdbc: String, private val pool: java.util.concurrent.Executor) : SneakyDBApi {
        override fun whenReady(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        override fun migrate(plugin: String, anchor: Class<*>): CompletableFuture<Int> = CompletableFuture.completedFuture(1)
        override fun isAvailable(): Boolean = true
        override fun query(sql: String, parameters: List<*>): CompletableFuture<List<Map<String, Any?>>> =
            error("No reads expected")
        override fun execute(sql: String, parameters: List<*>): CompletableFuture<Int> =
            CompletableFuture.supplyAsync({
                DriverManager.getConnection(jdbc).use { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                        statement.executeUpdate()
                    }
                }
            }, pool)
    }

    @BeforeEach
    fun applyMigrations() {
        val folder = File(javaClass.getResource("/db/migrations")!!.toURI())
        val scripts = folder.listFiles()!!.sortedBy { MIGRATION.matchEntire(it.name)!!.groupValues[1].toInt() }
        assertEquals(
            (1..scripts.size).toList(),
            scripts.map { MIGRATION.matchEntire(it.name)!!.groupValues[1].toInt() },
            "numbered from 1 without gaps",
        )
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS lom.lom2_prototype_kit_items")
                statement.execute("CREATE SCHEMA IF NOT EXISTS lom")
            }
            connection.autoCommit = false
            for (script in scripts) {
                connection.createStatement().use { it.execute(script.readText()) }
                connection.commit()
            }
        }
        val api = Api(url, workers)
        itemLog = PrototypeKitLog(api, CompletableFuture.completedFuture(1))
    }

    @AfterEach
    fun tearDown() {
        itemLog.close()
    }

    @Test
    fun insertRoundTrip() {
        assertEquals(1, itemLog.log(record).join())
        DriverManager.getConnection(url).use { connection ->
            connection.prepareStatement(
                """
                SELECT type, ability, creator, name, material, model_data, item_model, lore
                FROM lom.lom2_prototype_kit_items
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(record.type, result.getString("type"))
                    assertEquals(record.ability, result.getString("ability"))
                    assertEquals(record.creator, result.getString("creator"))
                    assertEquals(record.name, result.getString("name"))
                    assertEquals(record.material, result.getString("material"))
                    assertEquals(record.item_model, result.getString("item_model"))
                    assertEquals(record.lore, result.getString("lore"))
                    assertEquals(0.toBigDecimal().compareTo(result.getBigDecimal("model_data")), 0)
                    assertTrue(!result.next())
                }
            }
        }
    }
}