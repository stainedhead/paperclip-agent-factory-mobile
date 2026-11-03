package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.turbine.test
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.contract.AppSettingsStoreContract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SqlAppSettingsStoreContractTest : AppSettingsStoreContract() {
    override fun create() = SqlAppSettingsStore(TestDatabase().db)

    @Test
    fun `AD-021 the SQL app settings store passes the app settings store contract`() = runTest { runAll() }
}

class SqlAppSettingsStoreTest {
    @Test
    fun `AD-021 an empty database emits the defaults at once`() =
        runTest {
            val store = SqlAppSettingsStore(TestDatabase().db)
            store.observe().first() shouldBe AppSettings(deviceName = AppSettings.DEFAULT_DEVICE_NAME)
        }

    @Test
    fun `AD-020 an unset selected instance and every idle period survive a round trip`() =
        runTest {
            val store = SqlAppSettingsStore(TestDatabase().db)
            for (period in IdlePeriod.entries) {
                val settings = AppSettings("Pixel $period", appLockEnabled = false, period, selectedInstanceId = null)
                store.save(settings)
                store.observe().first() shouldBe settings
            }
        }

    @Test
    fun `AD-021 observe emits again after every save and keeps one row`() =
        runTest {
            val database = TestDatabase()
            val store = SqlAppSettingsStore(database.db)
            store.observe().test {
                awaitItem().deviceName shouldBe AppSettings.DEFAULT_DEVICE_NAME
                store.save(AppSettings("One", selectedInstanceId = InstanceId("inst-1")))
                awaitItem().deviceName shouldBe "One"
                store.save(AppSettings("Two"))
                awaitItem().deviceName shouldBe "Two"
                cancelAndIgnoreRemainingEvents()
            }
            database.scalar("SELECT count(*) FROM app_settings") shouldBe 1
        }

    @Test
    fun `AD-020 an idle period this version does not know reads as the default`() =
        runTest {
            val database = TestDatabase()
            val store = SqlAppSettingsStore(database.db)
            store.save(AppSettings("Pixel"))
            database.execute("UPDATE app_settings SET idle_period='Future'")
            store.observe().first().idlePeriod shouldBe AppSettings("Pixel").idlePeriod
        }
}
