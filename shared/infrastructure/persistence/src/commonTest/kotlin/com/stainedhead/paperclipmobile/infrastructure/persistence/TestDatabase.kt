package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase

/** A fresh, empty database at the current schema version on this platform: JDBC in memory, or a native temp file. */
expect fun newTestDriver(createSchema: Boolean = true): SqlDriver

/** The database plus raw SQL access, so tests can look at what is really stored. */
class TestDatabase(
    val driver: SqlDriver = newTestDriver(),
) {
    val db = PaperclipDatabase(driver)

    fun scalar(sql: String): Long =
        driver
            .executeQuery(null, sql, { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: error("no value"))
            }, 0)
            .value

    fun texts(sql: String): List<String> =
        driver
            .executeQuery(null, sql, { cursor ->
                val rows = mutableListOf<String>()
                while (cursor.next().value) rows += cursor.getString(0).orEmpty()
                QueryResult.Value(rows)
            }, 0)
            .value

    fun execute(sql: String) {
        driver.execute(null, sql, 0)
    }
}
