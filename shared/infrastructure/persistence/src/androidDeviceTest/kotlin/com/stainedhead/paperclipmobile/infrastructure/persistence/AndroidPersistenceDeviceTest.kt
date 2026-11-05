package com.stainedhead.paperclipmobile.infrastructure.persistence

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `openAndroidPersistence` on a real device: Android's SQLite, `noBackupFilesDir`, recreation and the sweep marker. */
@RunWith(AndroidJUnit4::class)
class AndroidPersistenceDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val clock = FakeClock()
    private val database get() = File(context.noBackupFilesDir, "paperclip.db")
    private val marker get() = File(context.noBackupFilesDir, "paperclip.db.sweep-pending")

    @Before
    @After
    fun removeTheFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(context.noBackupFilesDir, "paperclip.db$it").delete() }
        marker.delete()
        context.deleteDatabase("paperclip.db")
    }

    @Test
    fun theDatabaseLivesInNoBackupFilesDirAndNotInTheBackedUpDatabasesFolder() {
        val persistence = openAndroidPersistence(context, clock)
        runBlocking { persistence.instances.add(TestData.instance("a", "Home factory")) }
        persistence.close()
        assertTrue(database.isFile, "the file must be in noBackupFilesDir")
        assertFalse(
            context.getDatabasePath("paperclip.db").exists(),
            "nothing may be in the backed-up databases folder",
        )
        assertFalse(persistence.recreated)
    }

    @Test
    fun whatWasWrittenIsThereAfterClosingAndOpeningAgain() {
        val first = openAndroidPersistence(context, clock)
        runBlocking { first.instances.add(TestData.instance("a", "Home factory")) }
        first.close()
        val second = openAndroidPersistence(context, clock)
        assertEquals("Home factory", runBlocking { second.instances.get(InstanceId("a")) }?.displayName)
        assertFalse(second.recreated)
        second.close()
    }

    @Test
    fun aFileThatIsNotADatabaseIsReplacedByAnEmptyOneAndTheSweepIsStillPendingAfterARestart() {
        database.parentFile?.mkdirs()
        database.writeText("this is not a sqlite database, it is just some text ".repeat(TEXT_REPEATS))
        File(context.noBackupFilesDir, "paperclip.db-wal").writeText("stale")

        val recreated = openAndroidPersistence(context, clock)
        assertTrue(recreated.recreated, "a corrupted file is recreated")
        assertTrue(marker.exists(), "the sweep marker is written before the sweep")
        assertFalse(File(context.noBackupFilesDir, "paperclip.db-wal").exists(), "sidecar files go with the database")
        runBlocking { recreated.instances.add(TestData.instance("a", "After")) }
        recreated.close()

        val pending = openAndroidPersistence(context, clock)
        assertTrue(pending.recreated, "until the sweep completed the recreation is still pending")
        pending.sweepCompleted()
        assertFalse(marker.exists(), "completing the sweep removes the marker")
        pending.close()

        val settled = openAndroidPersistence(context, clock)
        assertFalse(settled.recreated)
        assertEquals("After", runBlocking { settled.instances.get(InstanceId("a")) }?.displayName)
        settled.close()
    }

    @Test
    fun aFileFromANewerAppVersionIsReplacedWithoutCrashing() {
        val opened = openAndroidPersistence(context, clock)
        opened.close()
        val raw =
            android.database.sqlite.SQLiteDatabase
                .openDatabase(database.path, null, 0)
        raw.version = NEWER_VERSION
        raw.close()
        val reopened = openAndroidPersistence(context, clock)
        assertTrue(reopened.recreated)
        reopened.close()
    }

    private companion object {
        const val TEXT_REPEATS = 100
        const val NEWER_VERSION = 1_000
    }
}
