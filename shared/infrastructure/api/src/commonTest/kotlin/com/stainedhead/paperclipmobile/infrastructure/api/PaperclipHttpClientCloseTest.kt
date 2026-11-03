package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AD-040: closing the client closes the engine it was given, or a graph that is discarded leaks its sockets. */
class PaperclipHttpClientCloseTest {
    @Test
    fun `closing the client closes the engine it was built on`() =
        runTest {
            val engine = MockEngine { respond("{}") }
            val client = PaperclipHttpClient(engine, allowInsecureLocal = false)
            val job = engine.coroutineContext[Job]!!
            assertTrue(job.isActive)

            client.close()
            job.join()

            assertFalse(job.isActive)
        }

    @Test
    fun `closing twice is harmless`() {
        val client = PaperclipHttpClient(MockEngine { respond("{}") }, allowInsecureLocal = false)

        client.close()
        client.close()
    }
}
