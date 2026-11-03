package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.infrastructure.platform.NwConnectivity
import com.stainedhead.paperclipmobile.infrastructure.platform.ReasonTexts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Review1b F-B-4: a platform whose database cannot be opened must not leave a network monitor running. */
class IosPlatformFailureTest {
    @Test
    fun `F-B-4 when the database cannot be opened no network monitor has been started`() {
        var monitorsStarted = 0

        shouldThrow<IllegalStateException> {
            IosLivePlatform(
                keychainService = "pafm.test.platform-failure",
                dataDirectory = "/dev/null/pafm-cannot-exist",
                reasons = ReasonTexts.default,
                monitorFactory = {
                    monitorsStarted += 1
                    NwConnectivity()
                },
            )
        }

        monitorsStarted shouldBe 0
    }
}
