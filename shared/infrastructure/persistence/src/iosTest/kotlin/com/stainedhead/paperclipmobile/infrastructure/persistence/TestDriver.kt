package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** A schema that creates nothing: lets a test build an older schema by hand on a real native database file. */
private object NoSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: app.cash.sqldelight.db.SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit

    override fun migrate(
        driver: app.cash.sqldelight.db.SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: app.cash.sqldelight.db.AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
}

actual fun newTestDriver(createSchema: Boolean): SqlDriver =
    NativeSqliteDriver(
        if (createSchema) PaperclipDatabase.Schema else NoSchema,
        "test-${NSUUID().UUIDString}.db",
        onConfiguration = {
            it.copy(
                extendedConfig = DatabaseConfiguration.Extended(basePath = NSTemporaryDirectory()),
            )
        },
    )
