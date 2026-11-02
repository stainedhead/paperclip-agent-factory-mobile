package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError

internal actual fun classifyPlatformFailure(cause: Throwable): DomainError? =
    (cause as? DarwinHttpRequestException)?.let { classifyUrlError(it.origin.domain, it.origin.code) }

/** NSURLErrorDomain codes that matter: TLS and ATS (-1200 to -1206, -1022), timed out (-1001), everything else. */
internal fun classifyUrlError(
    domain: String?,
    code: Long,
): DomainError =
    when {
        domain != URL_ERROR_DOMAIN -> DomainError.Unreachable
        code in TLS_CODES -> DomainError.CertificateInvalid
        code == TIMED_OUT -> DomainError.Timeout
        else -> DomainError.Unreachable
    }

internal fun classifyNsError(error: NSError): DomainError = classifyUrlError(error.domain, error.code)

private const val URL_ERROR_DOMAIN = "NSURLErrorDomain"
private const val TIMED_OUT = -1001L
private const val ATS_REQUIRES_SECURE = -1022L
private val TLS_CODES: Set<Long> = (-1206L..-1200L).toSet() + ATS_REQUIRES_SECURE
