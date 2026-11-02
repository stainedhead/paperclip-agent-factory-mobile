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
        shouldThrow<IllegalStateException> { recover { error("disk full") } }
        wipes shouldBe 1
    }
}
