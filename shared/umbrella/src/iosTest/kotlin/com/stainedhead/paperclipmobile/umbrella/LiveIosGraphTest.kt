package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import kotlin.test.Test

/** AD-040, AD-041: the iOS live graph on the simulator: a database in the directory given, kept out of backups. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class LiveIosGraphTest {
    private val directory = NSTemporaryDirectory() + "pafm-live-${NSUUID().UUIDString}"

    private fun excludedFromBackup(path: String): Boolean? {
        val values = NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
        return (values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue
    }

    @Test
    fun `the iOS live graph opens its database in the given directory out of backup and starts at first run`() {
        val graph =
            LiveIosGraph.createLiveGraph(
                allowInsecureLocal = false,
                FakePairingBrowser(),
                IosLiveSettings("pafm.live.test.${NSUUID().UUIDString}", directory, "Unlock", "Confirm"),
            )

        graph
            .createFirstRunStateHolder()
            .state.value.step shouldBe FirstRunStep.Address
        NSFileManager.defaultManager.fileExistsAtPath("$directory/paperclip.db") shouldBe true
        excludedFromBackup(directory) shouldBe true
        excludedFromBackup("$directory/paperclip.db") shouldBe true
        graph.close()
        graph.close()
    }
}
