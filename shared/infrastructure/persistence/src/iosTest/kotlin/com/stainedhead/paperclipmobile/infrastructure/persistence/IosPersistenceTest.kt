package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPersistenceTest {
    private val directory = NSTemporaryDirectory() + "pafm-${NSUUID().UUIDString}"
    private val databasePath = "$directory/paperclip.db"
    private val clock = FakeClock()
    private val scope = CompanyScope(InstanceId("a"), TestData.acme)

    private fun excludedFromBackup(path: String): Boolean? {
        val values = NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
        return (values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue
    }

    private fun exists(path: String) = NSFileManager.defaultManager.fileExistsAtPath(path)

    @Test
    fun `AD-021 data written through the native driver is there after closing and opening again`() =
        runTest {
            val first = openIosPersistence(clock, directory)
            first.recreated shouldBe false
            first.instances.add(TestData.instance("a", "Home factory"))
            first.attentionCache.write(scope, AttentionFixtures.feed())
            first.close()

            val second = openIosPersistence(clock, directory)
            second.instances.get(InstanceId("a"))?.displayName shouldBe "Home factory"
            second.attentionCache.read(scope) shouldBe AttentionFixtures.feed()
            second.close()
        }

    @Test
    fun `AD-020 the data directory and the database file are excluded from backup`() {
        openIosPersistence(clock, directory).close()
        exists(databasePath) shouldBe true
        excludedFromBackup(directory) shouldBe true
        excludedFromBackup(databasePath) shouldBe true
    }

    @Test
    fun `AD-020 the backup exclusion is applied again to a database recreated after corruption`() =
        runTest {
            openIosPersistence(clock, directory).close()
            NSFileManager.defaultManager.removeItemAtPath(databasePath, null)
            NSString.create(string = "garbage".repeat(2000)).writeToFile(databasePath, true, NSUTF8StringEncoding, null)

            val persistence = openIosPersistence(clock, directory)

            persistence.recreated shouldBe true
            persistence.instances.observeAll().first() shouldBe emptyList()
            excludedFromBackup(databasePath) shouldBe true
            persistence.instances.add(TestData.instance("later"))
            persistence.close()
            excludedFromBackup(databasePath) shouldBe true
            excludedFromBackup(directory) shouldBe true
        }

    @Test
    fun `AD-020 a file written by a newer schema version is replaced by an empty database`() =
        runTest {
            val first = openIosPersistence(clock, directory)
            first.instances.add(TestData.instance("a"))
            first.close()
            // What a newer app version would leave behind: the same file, user_version one higher. The driver opens
            // lazily, so the first statement is what writes the version.
            val newer =
                object : SqlSchema<QueryResult.Value<Unit>> {
                    override val version = PaperclipDatabase.Schema.version + 1

                    override fun create(driver: SqlDriver) = QueryResult.Unit

                    override fun migrate(
                        driver: SqlDriver,
                        oldVersion: Long,
                        newVersion: Long,
                        vararg callbacks: AfterVersion,
                    ) = QueryResult.Unit
                }
            NativeSqliteDriver(
                newer,
                "paperclip.db",
                onConfiguration = { it.copy(extendedConfig = DatabaseConfiguration.Extended(basePath = directory)) },
            ).also { it.scalarLong("PRAGMA user_version") shouldBe PaperclipDatabase.Schema.version + 1 }.close()

            val persistence = openIosPersistence(clock, directory)

            persistence.recreated shouldBe true
            persistence.instances.observeAll().first() shouldBe emptyList()
            persistence.close()
        }

    @Test
    fun `AD-020 the default data directory is a PaperclipData folder in Application Support`() {
        iosDataDirectory().endsWith("Library/Application Support/PaperclipData") shouldBe true
    }
}
