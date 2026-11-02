package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorDomain
import kotlin.test.Test
import kotlin.test.assertEquals

/** AD-003, AD-004: what NSURLSession reports (through Ktor's Darwin engine) becomes a typed error. */
class PlatformFailuresTest {
    private fun darwin(code: Long) = DarwinHttpRequestException(NSError.errorWithDomain(NSURLErrorDomain, code, null))

    @Test
    fun `AD-004 every secure connection error code is CertificateInvalid`() {
        // -1200 SecureConnectionFailed .. -1206 ClientCertificateRequired, -1022 ATS requires a secure connection.
        (listOf(-1022L) + (-1206L..-1200L).toList()).forEach {
            assertEquals(DomainError.CertificateInvalid, ErrorMapper.fromTransport(darwin(it)), "code $it")
        }
    }

    @Test
    fun `AD-003 the NSURL timed out code is Timeout`() =
        assertEquals(DomainError.Timeout, ErrorMapper.fromTransport(darwin(-1001L)))

    @Test
    fun `AD-003 cannot connect or find host and offline are Unreachable`() {
        listOf(-1004L, -1003L, -1009L, -1005L).forEach {
            assertEquals(DomainError.Unreachable, ErrorMapper.fromTransport(darwin(it)), "code $it")
        }
    }

    @Test
    fun `AD-003 an NSError from another domain is never read as a TLS failure`() {
        val other = NSError.errorWithDomain("NSPOSIXErrorDomain", -1202L, null)
        assertEquals(DomainError.Unreachable, ErrorMapper.fromTransport(DarwinHttpRequestException(other)))
    }
}
