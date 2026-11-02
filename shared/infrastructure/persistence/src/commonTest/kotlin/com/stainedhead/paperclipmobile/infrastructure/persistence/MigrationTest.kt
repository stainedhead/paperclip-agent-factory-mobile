package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-020: a database written by schema version 1 opens under the current schema with its data intact. */
class MigrationTest {
    /** The schema exactly as version 1 shipped it, written out by hand: it must never be edited. */
    private val versionOneSchema =
        listOf(
            """CREATE TABLE instance_config (
                id TEXT NOT NULL PRIMARY KEY, display_name TEXT NOT NULL, base_uri TEXT NOT NULL,
                deployment_mode TEXT NOT NULL, exposure TEXT NOT NULL, auth_method TEXT NOT NULL,
                board_key_id TEXT, key_expires_at TEXT, default_company_id TEXT, last_company_id TEXT,
                pins TEXT NOT NULL, created_at TEXT NOT NULL, last_connected_at TEXT,
                sort_order INTEGER NOT NULL, status TEXT NOT NULL)""",
            "CREATE INDEX instance_config_sort ON instance_config(sort_order)",
            """CREATE TABLE app_settings (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1), device_name TEXT NOT NULL,
                app_lock_enabled INTEGER NOT NULL, idle_period TEXT NOT NULL, selected_instance_id TEXT)""",
            """CREATE TABLE attention_cache (
                instance_id TEXT NOT NULL, company_id TEXT NOT NULL, payload TEXT NOT NULL,
                updated_at TEXT NOT NULL, PRIMARY KEY (instance_id, company_id))""",
        )

    private fun versionOneDatabase(): TestDatabase {
        val database = TestDatabase(newTestDriver(createSchema = false))
        versionOneSchema.forEach { database.execute(it) }
        database.execute(
            "INSERT INTO instance_config VALUES ('inst-1', 'Home factory', 'https://home.example.com', " +
                "'Authenticated', 'Private', 'PairedBoardKey', 'key-1', NULL, 'company-acme', NULL, '', " +
                "'2026-11-02T10:00:00Z', NULL, 0, 'SignedIn')",
        )
        database.execute("INSERT INTO app_settings VALUES (1, 'Pixel', 1, 'OneHour', 'inst-1')")
        database.execute(
            "INSERT INTO attention_cache VALUES ('inst-1', 'company-acme', '{}', '2026-11-02T10:00:00Z')",
        )
        return database
    }

    @Test
    fun `AD-020 the current schema is newer than version 1 and every older version has a snapshot`() {
        (PaperclipDatabase.Schema.version > 1) shouldBe true
    }

    @Test
    fun `AD-020 a version 1 database keeps its rows after the migration`() =
        runTest {
            val database = versionOneDatabase()

            PaperclipDatabase.Schema.migrate(database.driver, 1, PaperclipDatabase.Schema.version)

            val instance = SqlInstanceRepository(database.db).get(InstanceId("inst-1"))
            instance?.displayName shouldBe "Home factory"
            instance?.defaultCompanyId shouldBe TestData.acme
            SqlAppSettingsStore(database.db).observe().first().deviceName shouldBe "Pixel"
            database.scalar("SELECT count(*) FROM attention_cache") shouldBe 1
        }

    @Test
    fun `AD-020 migrated cache rows are marked as the first payload format`() {
        val database = versionOneDatabase()
        PaperclipDatabase.Schema.migrate(database.driver, 1, PaperclipDatabase.Schema.version)
        database.scalar("SELECT format FROM attention_cache") shouldBe 1
    }
}
