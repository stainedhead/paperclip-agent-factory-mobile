package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SQL = "c3000000-0000-4000-8000-000000000001"
private const val PNG = "c3000000-0000-4000-8000-000000000002"
private const val OTHER = "c3000000-0000-4000-8000-000000000003"
private val acme = ServerFixtures.COMPANY_ACME
private val root = IssueId(PAP_1182)
private val sqlBytes = "SELECT 1;\n-- héllo ✓\n".encodeToByteArray()
private val pngBytes =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0xFF.toByte(), 0xFE.toByte(), 0x00, 0x7F, 0x01, 0x02)
private val notFound = failed(DomainError.NotFound)

/** AD-013: the attachments of an issue and their content, read with HTTP Range. */
class IssueAttachmentsAdapterTest {
    private val world =
        WorkWorld().also { w ->
            seedIssues(w)
            w.home.thread.attachments += StubAttachment(SQL, acme, PAP_1182, "migration.sql", "text/plain", sqlBytes)
            val png = StubAttachment(PNG, acme, PAP_1182, null, "image/png", pngBytes)
            png.createdAt = "2026-11-01T10:00:00.000Z"
            w.home.thread.attachments += png
            w.home.thread.attachments += StubAttachment(OTHER, acme, PAP_1190, "other.txt", "text/plain", sqlBytes)
        }

    private val sent get() = world.server.requests

    @Test
    fun `AD-013 attachments are listed newest first with the file name type size and content path`() =
        runTest {
            val list = world.issues.attachments(world.acme, root).value()
            assertEquals(listOf(PNG, SQL), list.map { it.id })
            val file = list.first { it.id == SQL }
            assertEquals("migration.sql", file.fileName)
            assertEquals("text/plain", file.contentType)
            assertEquals(sqlBytes.size.toLong(), file.byteSize)
            assertEquals("/api/attachments/$SQL/content", file.contentPath)
            assertEquals(Instant.parse("2026-11-01T09:00:00.000Z"), file.createdAt)
            assertEquals(null, list.first { it.id == PNG }.fileName)
            assertEquals("/api/issues/$PAP_1182/attachments", sent.single().path)
        }

    @Test
    fun `AD-013 an empty list is empty and a viewer reads attachments`() =
        runTest {
            assertEquals(emptyList(), world.issues.attachments(world.acme, IssueId(PAP_1191)).value())
            val g = StubIssue("c0000008-0000-4000-8000-000000000008", "company-globex", "GLB-2", "Viewer's")
            world.home.issueStore.issues += g
            world.issues.attachments(world.globex, IssueId(g.id)).value()
        }

    @Test
    fun `AD-014 attachments of an issue of another company are not trusted`() =
        runTest {
            val globexIssue = StubIssue("c0000009-0000-4000-8000-000000000009", "company-globex", "GLB-1", "Other")
            world.home.issueStore.issues += globexIssue
            val other = "c3000000-0000-4000-8000-000000000004"
            val foreign = StubAttachment(other, "company-globex", globexIssue.id, "g", "text/plain", sqlBytes)
            world.home.thread.attachments += foreign
            assertEquals(failed(DomainError.NotFound), world.issues.attachments(world.acme, IssueId(globexIssue.id)))
        }

    @Test
    fun `AD-003 attachment lists that are not lists of attachments are Malformed`() =
        runTest {
            val noSize =
                """[{"id":"$SQL","companyId":"$acme","contentType":"text/plain","createdAt":"2026-11-01T09:00:00Z"}]"""
            val gateway = IssueGatewayAdapter(answeringClient("{}"), world.instances, world.secrets)
            (NOT_THE_EXPECTED_SHAPE + "{}" + "[1]" + noSize).forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                val client = answeringClient(body, contentType = type)
                val adapter = IssueGatewayAdapter(client, world.instances, world.secrets)
                assertEquals(failed(DomainError.Malformed), adapter.attachments(world.acme, root), body)
            }
            assertEquals(failed(DomainError.Malformed), gateway.attachments(world.acme, root))
        }

    @Test
    fun `AD-013 a slice is read with a Range header after the list proves the attachment is the issues`() =
        runTest {
            val slice = world.issues.attachmentContent(world.acme, root, SQL, 0, 3).value()
            assertContentEquals(sqlBytes.copyOfRange(0, 4), slice.bytes)
            assertEquals(0L, slice.start)
            assertEquals(sqlBytes.size.toLong(), slice.total)
            assertEquals("text/plain", slice.contentType)
            val paths = listOf("/api/issues/$PAP_1182/attachments", "/api/attachments/$SQL/content")
            assertEquals(paths, sent.map { it.path })
            assertEquals("bytes=0-3", sent[1].range)
            assertEquals("Bearer ${TestData.TOKEN}", sent[1].authorization)
            assertEquals("", sent[1].query)
        }

    @Test
    fun `AD-013 an open ended range reads to the end and binary bytes arrive unchanged`() =
        runTest {
            val slice = world.issues.attachmentContent(world.acme, root, PNG, 4, null).value()
            assertEquals("bytes=4-", sent.last().range)
            assertContentEquals(pngBytes.copyOfRange(4, pngBytes.size), slice.bytes)
            assertEquals(4L, slice.start)
            val whole = world.issues.attachmentContent(world.acme, root, PNG, 0, (pngBytes.size - 1).toLong()).value()
            assertContentEquals(pngBytes, whole.bytes)
            assertEquals("image/png", whole.contentType)
        }

    @Test
    fun `AD-013 a range past the end is 416 and arrives as a reason and an end before the start is refused here`() =
        runTest {
            assertEquals(
                failed(DomainError.Rejected("The range is outside the file")),
                world.issues.attachmentContent(world.acme, root, PNG, 100, null),
            )
            val before = sent.size
            val reversed = world.issues.attachmentContent(world.acme, root, PNG, 5, 2) as DomainResult.Failure
            val negative = world.issues.attachmentContent(world.acme, root, PNG, -1, 2) as DomainResult.Failure
            assertIs<DomainError.Rejected>(reversed.error)
            assertIs<DomainError.Rejected>(negative.error)
            assertEquals(before, sent.size)
        }

    @Test
    fun `AD-014 an attachment of another issue or a bad id is NotFound and no content is requested`() =
        runTest {
            assertEquals(notFound, world.issues.attachmentContent(world.acme, root, OTHER, 0, 1))
            assertEquals(notFound, world.issues.attachmentContent(world.acme, root, "att-1", 0, 1))
            assertEquals(notFound, world.issues.attachmentContent(world.acme, IssueId("bad"), SQL, 0, 1))
            assertTrue(sent.none { it.path.endsWith("/content") })
        }

    @Test
    fun `AD-013 a server that ignores Range and sends the whole file is read from byte zero`() =
        runTest {
            val file = StubAttachment(SQL, acme, PAP_1182, "migration.sql", "text/plain", sqlBytes)
            val listing = IssueRowFixtures.attachment(file)
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/attachments")) {
                        respond("[$listing]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(sqlBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
                    }
                }
            val adapter = IssueGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            val slice = adapter.attachmentContent(world.acme, root, SQL, 5, 9).value()
            assertEquals(0L, slice.start)
            assertContentEquals(sqlBytes, slice.bytes)
            assertEquals(sqlBytes.size.toLong(), slice.total)
        }
}
