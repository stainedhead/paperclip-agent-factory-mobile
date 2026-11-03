package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** AD-001: the real engine of each platform (OkHttp, Darwin) is wired and its failures are mapped. */
class PlatformEngineTest {
    @Test
    fun `AD-003 the platform engine reports a closed local port as Unreachable`() =
        runTest {
            val client = createPaperclipHttpClient(allowInsecureLocal = false)
            try {
                // Port 1 on the loopback address has no listener, so the connection is refused at once.
                val request =
                    ApiRequest(Destination(TestData.uri("https://localhost:1")), HttpMethod.Get, listOf("health"))
                assertEquals(DomainResult.Failure(DomainError.Unreachable), client.send(request))
            } finally {
                client.close()
            }
        }
}
