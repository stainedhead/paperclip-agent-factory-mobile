package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase

actual fun newTestDriver(createSchema: Boolean): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { if (createSchema) PaperclipDatabase.Schema.create(it) }
