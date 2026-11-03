package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** AD-020: a database that cannot be used is recreated empty, never a crash and never a half-read file. */
class DatabaseRecoveryTest {
    private var wipes = 0
    private var opens = 0

    private fun recover(open: () -> app.cash.sqldelight.db.SqlDriver) =
        openRecovering(open = {
            opens += 1
            open()
        }, wipe = { wipes += 1 })

    @Test
    fun `AD-020 a healthy database is kept as it is`() {
        val opened = recover { newTestDriver() }
        opened.recreated shouldBe false
        wipes shouldBe 0
        opens shouldBe 1
    }

    @Test
    fun `AD-020 a database that cannot be opened is wiped and opened again`() {
        var first = true
        val opened =
            recover {
                if (first) {
                    first = false
                    error("file is not a database")
                }
                newTestDriver()
            }
        opened.recreated shouldBe true
        wipes shouldBe 1
        opens shouldBe 2
    }

    @Test
    fun `AD-020 a database written by a newer app version is wiped`() {
        var first = true
        val opened =
            recover {
                newTestDriver().also {
                    if (first) it.execute(null, "PRAGMA user_version = ${PaperclipDatabase.Schema.version + 1}", 0)
                    first = false
                }
            }
        opened.recreated shouldBe true
        wipes shouldBe 1
    }

    @Test
    fun `AD-020 a database with a missing table is wiped`() {
        var first = true
        val opened =
            recover {
                newTestDriver().also {
                    if (first) it.execute(null, "DROP TABLE attention_cache", 0)
                    first = false
                }
            }
        opened.recreated shouldBe true
        wipes shouldBe 1
    }

    @Test
    fun `AD-020 a database that cannot be recreated either fails loudly`() {
        var first = true
        shouldThrow<StorageUnavailableException> {
            recover {
                if (first) {
                    first = false
                    error("file is not a database")
                }
                error("disk full")
            }
        }
        wipes shouldBe 1
    }

    private val transientFailures =
        listOf(
            "database is locked",
            "[SQLITE_BUSY] The database file is locked",
            "disk I/O error",
            "database or disk is full",
            "unable to open database file",
            "authorization denied: file is protected until first unlock",
            "migration from 1 to 2 failed: no such column",
        )

    @Test
    fun `F-B-1 a transient failure while opening never wipes anything and says storage is unavailable`() {
        transientFailures.forEach { message ->
            wipes = 0
            val failure = shouldThrow<StorageUnavailableException> { recover { error(message) } }
            wipes shouldBe 0
            failure.cause?.message shouldBe message
        }
    }

    @Test
    fun `F-B-1 a failing migration never wipes the data it was meant to keep`() {
        shouldThrow<StorageUnavailableException> { recover { throw UnsupportedOperationException("migrate 1 to 2") } }
        wipes shouldBe 0
    }

    @Test
    fun `F-B-1 a transient failure while checking the opened database never wipes and closes the driver`() {
        transientFailures.forEach { message ->
            wipes = 0
            val driver = FailingProbeDriver(newTestDriver(), message)
            shouldThrow<StorageUnavailableException> { recover { driver } }
            wipes shouldBe 0
            driver.closed shouldBe true
        }
    }

    @Test
    fun `F-B-1 proven corruption while checking the opened database still wipes`() {
        var first = true
        val opened =
            recover {
                if (first) {
                    first = false
                    FailingProbeDriver(newTestDriver(), "database disk image is malformed")
                } else {
                    newTestDriver()
                }
            }
        opened.recreated shouldBe true
        wipes shouldBe 1
    }

    @Test
    fun `F-B-1 a downgrade refused by the platform driver counts as a newer schema and wipes`() {
        var first = true
        val opened =
            recover {
                if (first) {
                    first = false
                    error("Can't downgrade database from version 3 to 2")
                }
                newTestDriver()
            }
        opened.recreated shouldBe true
        wipes shouldBe 1
    }
}

/** A driver whose first query fails with [message], as a busy, full or locked database does. */
private class FailingProbeDriver(
    private val real: app.cash.sqldelight.db.SqlDriver,
    private val message: String,
) : app.cash.sqldelight.db.SqlDriver by real {
    var closed = false
        private set

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (app.cash.sqldelight.db.SqlCursor) -> app.cash.sqldelight.db.QueryResult<R>,
        parameters: Int,
        binders: (app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit)?,
    ): app.cash.sqldelight.db.QueryResult<R> = error(message)

    override fun close() {
        closed = true
        real.close()
    }
}

/** F-R-1: the recreation survives a failed reopen and a kill until the sweep has completed. */
class SweepMarkerRecoveryTest {
    private val marker = InMemoryMarker()
    private var wipes = 0

    private fun corruptThen(second: () -> app.cash.sqldelight.db.SqlDriver): () -> app.cash.sqldelight.db.SqlDriver {
        var first = true
        return {
            if (first) {
                first = false
                error("file is not a database")
            }
            second()
        }
    }

    private fun open(opener: () -> app.cash.sqldelight.db.SqlDriver) =
        openRecovering(open = opener, wipe = { wipes += 1 }, marker = marker)

    @Test
    fun `F-R-1 the marker is set before the file is wiped`() {
        var setBeforeWipe = false
        openRecovering(
            open = corruptThen { newTestDriver() },
            wipe = { setBeforeWipe = marker.isSet() },
            marker = marker,
        )
        setBeforeWipe shouldBe true
    }

    @Test
    fun `F-R-1 a wipe followed by a failed reopen is still reported by the next healthy open`() {
        shouldThrow<StorageUnavailableException> { open(corruptThen { error("disk full") }) }
        marker.isSet() shouldBe true

        open { newTestDriver() }.recreated shouldBe true
    }

    @Test
    fun `F-R-1 a process killed after the wipe is still reported by the next open`() {
        open(corruptThen { newTestDriver() }).recreated shouldBe true
        // The process died here: the marker was never cleared.
        open { newTestDriver() }.recreated shouldBe true
    }

    @Test
    fun `F-R-1 a cleared marker means a normal launch is silent`() {
        open(corruptThen { newTestDriver() })
        marker.clear()
        open { newTestDriver() }.recreated shouldBe false
    }

    @Test
    fun `F-R-1 a marker that cannot be set stops the wipe`() {
        val broken =
            object : SweepMarker by marker {
                override fun set() = error("read-only folder")
            }
        shouldThrow<StorageUnavailableException> {
            openRecovering(open = corruptThen { newTestDriver() }, wipe = { wipes += 1 }, marker = broken)
        }
        wipes shouldBe 0
    }
}
