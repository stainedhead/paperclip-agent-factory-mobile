package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.QueryResult
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * AD-020, security rule: no secret reaches the database. The persistence ports take no `Secret` or `SecretKey` (a
 * Konsist rule checks the imports); this audit checks what is really stored: the schema, every column name, and every
 * value of a fully populated database.
 */
class SecretsAuditTest {
    /** The whole schema. A new table or column fails this test until someone has decided it holds no secret. */
    private val expectedColumns =
        mapOf(
            "instance_config" to
                listOf(
                    "id",
                    "display_name",
                    "base_uri",
                    "deployment_mode",
                    "exposure",
                    "auth_method",
                    "board_key_id",
                    "key_expires_at",
                    "default_company_id",
                    "last_company_id",
                    "pins",
                    "created_at",
                    "last_connected_at",
                    "sort_order",
                    "status",
                ),
            "app_settings" to
                listOf("id", "device_name", "app_lock_enabled", "idle_period", "selected_instance_id"),
            "attention_cache" to listOf("instance_id", "company_id", "payload", "updated_at", "format"),
        )

    private val secretWords = listOf("pcp_board_", "token", "secret", "password", "bearer", "authorization")

    private suspend fun populated(): TestDatabase {
        val database = TestDatabase()
        val persistence = PaperclipPersistence(database.driver, FakeClock(), recreated = false)
        persistence.instances.add(TestData.instance("a", "Home factory"))
        persistence.instances.add(TestData.instance("b", "Cloud", sortOrder = 1))
        persistence.settings.save(AppSettings("Alex's phone"))
        persistence.attentionCache.write(CompanyScope(InstanceId("a"), TestData.acme), RichFeed.feed())
        return database
    }

    private fun tableNames(database: TestDatabase) =
        database.texts("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")

    private fun columns(
        database: TestDatabase,
        table: String,
    ) = database.texts("SELECT name FROM pragma_table_info('$table')")

    /** Every value of every row of every table, as text, with its table and column. */
    private fun allValues(database: TestDatabase): List<String> =
        tableNames(database).flatMap { table ->
            val names = columns(database, table)
            database.driver
                .executeQuery(null, "SELECT * FROM $table", { cursor ->
                    val values = mutableListOf<String>()
                    while (cursor.next().value) {
                        names.indices.forEach { index -> values += cursor.getString(index).orEmpty() }
                    }
                    QueryResult.Value(values)
                }, 0)
                .value
        }

    @Test
    fun `AD-020 the schema holds exactly the audited tables and columns`() =
        runTest {
            val database = populated()
            tableNames(database) shouldBe expectedColumns.keys.sorted()
            expectedColumns.forEach { (table, expected) -> columns(database, table) shouldBe expected }
        }

    @Test
    fun `AD-020 no column is named like a secret`() =
        runTest {
            val database = populated()
            val offenders =
                tableNames(database).flatMap { table ->
                    columns(database, table).filter { column -> secretWords.any { it in column.lowercase() } }
                }
            offenders shouldBe emptyList()
        }

    @Test
    fun `AD-020 no stored value contains a board token`() =
        runTest {
            val database = populated()
            val values = allValues(database)
            (values.size > expectedColumns.values.sumOf { it.size }) shouldBe true
            values.filter { "pcp_board_" in it || TestData.TOKEN in it } shouldBe emptyList()
        }

    @Test
    fun `AD-020 the audit goes red when a token is stored`() =
        runTest {
            val database = populated()
            database.execute("UPDATE instance_config SET display_name='${TestData.TOKEN}' WHERE id='a'")
            allValues(database).any { "pcp_board_" in it } shouldBe true
        }
}
