package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

actual fun newTestDriver(): SqlDriver {
    val driver = NativeSqliteDriver(PaperclipDatabase.Schema, "spike-${NSUUID().UUIDString}.db")
    println("SPIKE tmp=${NSTemporaryDirectory()}")
    return driver
}
