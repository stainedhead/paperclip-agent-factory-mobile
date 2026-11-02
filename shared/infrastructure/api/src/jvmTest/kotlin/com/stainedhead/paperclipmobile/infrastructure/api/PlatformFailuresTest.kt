package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertEquals

/** AD-003, AD-004: what the JVM TLS and socket stack throws becomes a typed error. */
class PlatformFailuresTest {
    @Test
    fun `AD-004 a TLS handshake or hostname failure is CertificateInvalid`() {
        assertEquals(DomainError.CertificateInvalid, ErrorMapper.fromTransport(SSLHandshakeException("PKIX failed")))
        assertEquals(
            DomainError.CertificateInvalid,
            ErrorMapper.fromTransport(SSLPeerUnverifiedException("Hostname x not verified")),
        )
    }

    @Test
    fun `AD-004 a TLS failure wrapped by the engine is still CertificateInvalid`() {
        val wrapped = RuntimeException("engine", SSLHandshakeException("bad certificate"))
        assertEquals(DomainError.CertificateInvalid, ErrorMapper.fromTransport(wrapped))
    }

    @Test
    fun `AD-003 refused and unresolvable hosts are Unreachable`() {
        assertEquals(DomainError.Unreachable, ErrorMapper.fromTransport(ConnectException("refused")))
        assertEquals(DomainError.Unreachable, ErrorMapper.fromTransport(UnknownHostException("nope.example.com")))
    }

    @Test
    fun `AD-003 a socket read timeout is Timeout`() =
        assertEquals(DomainError.Timeout, ErrorMapper.fromTransport(java.net.SocketTimeoutException("Read timed out")))
}
