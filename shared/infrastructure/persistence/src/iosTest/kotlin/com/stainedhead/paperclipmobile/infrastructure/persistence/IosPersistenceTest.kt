package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
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
    fun `AD-020 the default data directory is a PaperclipData folder in Application Support`() {
        iosDataDirectory().endsWith("Library/Application Support/PaperclipData") shouldBe true
    }
}
