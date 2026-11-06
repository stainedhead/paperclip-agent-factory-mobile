package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class JvmPersistenceTest {
    private val dir = createTempDirectory("pafm-db").toFile().also { it.deleteOnExit() }
    private val file = File(dir, "paperclip.db")
    private val clock = FakeClock()
    private val scope = CompanyScope(InstanceId("a"), TestData.acme)

    @Test
    fun `AD-021 data written to a file is there after closing and opening again`() =
        runTest {
            val first = openJvmPersistence(clock, file)
            first.recreated shouldBe false
            first.instances.add(TestData.instance("a", "Home factory"))
            first.attentionCache.write(scope, AttentionFixtures.feed())
            first.close()

            val second = openJvmPersistence(clock, file)
            second.recreated shouldBe false
            second.instances.get(InstanceId("a"))?.displayName shouldBe "Home factory"
            second.attentionCache.read(scope) shouldBe AttentionFixtures.feed()
            second.close()
        }

    @Test
    fun `AD-021 an in-memory database starts empty and works`() =
        runTest {
            val persistence = openJvmPersistence(clock)
            persistence.instances.observeAll().first() shouldBe emptyList()
            persistence.instances.add(TestData.instance("a"))
            persistence.instances
                .observeAll()
                .first()
                .size shouldBe 1
            persistence.close()
        }

    @Test
    fun `AD-021 parallel writers through the production wiring lose nothing`() =
        runTest {
            val persistence = openJvmPersistence(clock)
            withContext(Dispatchers.Default) {
                (0 until 16)
                    .map { n ->
                        async {
                            persistence.instances.add(TestData.instance("i$n", "I$n", sortOrder = n))
                            persistence.attentionCache.write(
                                CompanyScope(InstanceId("i$n"), TestData.acme),
                                AttentionFixtures.feed(),
                            )
                        }
                    }.awaitAll()
            }
            persistence.instances
                .observeAll()
                .first()
                .size shouldBe 16
            persistence.close()
        }

    @Test
    fun `AD-020 a zero byte file is an empty database and is not a recreation`() =
        runTest {
            file.writeBytes(ByteArray(0))
            val persistence = openJvmPersistence(clock, file)
            persistence.recreated shouldBe false
            persistence.instances.observeAll().first() shouldBe emptyList()
            persistence.close()
        }

    @Test
    fun `AD-020 a corrupted file is replaced by an empty database without crashing`() =
        runTest {
            file.writeText("this is not a sqlite database, it is just some text ".repeat(100))
            val persistence = openJvmPersistence(clock, file)
            persistence.recreated shouldBe true
            persistence.instances.observeAll().first() shouldBe emptyList()
            persistence.instances.add(TestData.instance("a"))
            persistence.close()

            // F-R-1: until the sweep has completed the recreation is still pending, across launches.
            val pending = openJvmPersistence(clock, file)
            pending.recreated shouldBe true
            pending.sweepCompleted()
            pending.close()

            val reopened = openJvmPersistence(clock, file)
            reopened.recreated shouldBe false
            reopened.instances
                .observeAll()
                .first()
                .size shouldBe 1
            reopened.close()
        }

    @Test
    fun `AD-020 a file from a newer app version is replaced by an empty database`() =
        runTest {
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").also {
                PaperclipDatabase.Schema.create(it)
                it.execute(null, "PRAGMA user_version = ${PaperclipDatabase.Schema.version + 1}", 0)
                it.close()
            }
            val persistence = openJvmPersistence(clock, file)
            persistence.recreated shouldBe true
            persistence.close()
        }

    @Test
    fun `AD-020 a file written by schema version 1 is migrated when it is opened`() =
        runTest {
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").also { driver ->
                (VersionOneSchema.statements + VersionOneSchema.rows).forEach { driver.execute(null, it, 0) }
                driver.execute(null, "PRAGMA user_version = 1", 0)
                driver.close()
            }
            val persistence = openJvmPersistence(clock, file)
            persistence.recreated shouldBe false
            persistence.instances.get(InstanceId("inst-1"))?.displayName shouldBe "Home factory"
            persistence.close()
        }

    @Test
    fun `AD-020 the sidecar files of a corrupted database are removed with it`() =
        runTest {
            file.writeText("garbage".repeat(1000))
            val wal = File(dir, "paperclip.db-wal").also { it.writeText("stale") }
            val shm = File(dir, "paperclip.db-shm").also { it.writeText("stale") }
            openJvmPersistence(clock, file).close()
            wal.exists() shouldBe false
            shm.exists() shouldBe false
        }

    @Test
    fun `F-B-1 a version 1 file whose migration throws is kept untouched`() =
        runTest {
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").also { driver ->
                driver.execute(null, "CREATE TABLE precious (note TEXT)", 0)
                driver.execute(null, "INSERT INTO precious VALUES ('factory list')", 0)
                driver.execute(null, "PRAGMA user_version = 1", 0)
                driver.close()
            }
            val before = file.readBytes()

            shouldThrow<StorageUnavailableException> { openJvmPersistence(clock, file) }

            file.readBytes().contentEquals(before) shouldBe true
        }

    @Test
    fun `F-B-1 a database another connection holds locked is not wiped`() =
        runTest {
            openJvmPersistence(clock, file).also {
                runBlocking { it.instances.add(TestData.instance("a", "Home factory")) }
                it.close()
            }
            val holder = DriverManager.getConnection("jdbc:sqlite:${file.path}")
            holder.createStatement().execute("BEGIN EXCLUSIVE")
            try {
                shouldThrow<StorageUnavailableException> { openJvmPersistence(clock, file) }
            } finally {
                holder.createStatement().execute("ROLLBACK")
                holder.close()
            }

            val reopened = openJvmPersistence(clock, file)
            reopened.recreated shouldBe false
            reopened.instances.get(InstanceId("a"))?.displayName shouldBe "Home factory"
            reopened.close()
        }
}
