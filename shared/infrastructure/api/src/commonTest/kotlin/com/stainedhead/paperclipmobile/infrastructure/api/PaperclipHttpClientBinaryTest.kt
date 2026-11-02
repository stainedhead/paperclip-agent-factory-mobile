package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val home = TestData.uri()

/** AD-013: the binary path of the shared client, used by the attachment content call. */
class PaperclipHttpClientBinaryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val trace = mutableListOf<String>()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0xFF.toByte(), 0xFE.toByte(), 0x00, 0x7F)

    private fun client(
        status: HttpStatusCode = HttpStatusCode.PartialContent,
        body: ByteArray = png,
        headers: io.ktor.http.Headers =
            headersOf(
                HttpHeaders.ContentType to listOf("image/png"),
                HttpHeaders.ContentRange to listOf("bytes 0-7/20"),
            ),
    ) = PaperclipHttpClient(
        MockEngine { request ->
            requests += request
            respond(body, status, headers)
        },
        allowInsecureLocal = false,
    ) { trace += it }

    private fun ranged() =
        ApiRequest(
            Destination(home),
            HttpMethod.Get,
            listOf("attachments", "att-1", "content"),
            bearer = Secret.of(TestData.TOKEN),
        )

    private suspend fun PaperclipHttpClient.sendBytes(request: ApiRequest) = sendBytes(request, "bytes=0-7")

    @Test
    fun `AD-013 a range request sends the header and keeps every byte of the reply`() =
        runTest {
            val reply = assertIs<DomainResult.Success<ApiBytes>>(client().sendBytes(ranged()))
            assertEquals("bytes=0-7", requests.single().headers[HttpHeaders.Range])
            assertEquals("Bearer ${TestData.TOKEN}", requests.single().headers[HttpHeaders.Authorization])
            assertEquals("https://home.example.com/api/attachments/att-1/content", requests.single().url.toString())
            assertContentEquals(png, reply.value.bytes)
            assertEquals(206, reply.value.status)
            assertEquals("image/png", reply.value.contentType)
            assertEquals("bytes 0-7/20", reply.value.contentRange)
        }

    @Test
    fun `AD-013 the request line in the trace names no query and no key`() =
        runTest {
            client().sendBytes(ranged())
            assertEquals(listOf("GET home.example.com /api/attachments/att-1/content -> 206"), trace)
            assertTrue(trace.none { TestData.TOKEN in it })
        }

    @Test
    fun `AD-013 a range the server cannot satisfy is rejected with a reason and 404 and 401 keep their meaning`() =
        runTest {
            val none = headersOf(HttpHeaders.ContentRange, "bytes */20")
            val unsatisfiable =
                client(
                    HttpStatusCode.RequestedRangeNotSatisfiable,
                    ByteArray(0),
                    none,
                ).sendBytes(ranged())
            assertEquals(DomainResult.Failure(DomainError.Rejected("The range is outside the file")), unsatisfiable)
            assertEquals(
                DomainResult.Failure(DomainError.NotFound),
                client(
                    HttpStatusCode.NotFound,
                    """{"error":"Attachment not found"}""".encodeToByteArray(),
                ).sendBytes(ranged()),
            )
            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                client(HttpStatusCode.Unauthorized, "{}".encodeToByteArray()).sendBytes(ranged()),
            )
        }

    @Test
    fun `AD-001 the binary path keeps the rules of the text path`() =
        runTest {
            val local = (BaseUri.parse("http://localhost:3100", allowInsecureLocal = true) as BaseUriResult.Valid).uri
            val plain = ApiRequest(Destination(local), HttpMethod.Get, listOf("x"))
            assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), client().sendBytes(plain))
            val wiped = Secret.of(TestData.TOKEN).also { it.wipe() }
            val request = ApiRequest(Destination(home), HttpMethod.Get, listOf("x"), bearer = wiped)
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), client().sendBytes(request))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `AD-013 a failed connection is a transport error and not a crash`() =
        runTest {
            val engine = MockEngine { throw kotlinx.io.IOException("connection reset https://home.example.com/x") }
            val failing = PaperclipHttpClient(engine, allowInsecureLocal = false)
            val result = failing.sendBytes(ranged())
            assertEquals(DomainResult.Failure(DomainError.Unreachable), result)
        }
}
