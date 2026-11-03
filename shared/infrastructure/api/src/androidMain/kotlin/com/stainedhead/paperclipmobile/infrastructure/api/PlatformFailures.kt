package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal actual fun classifyPlatformFailure(cause: Throwable): DomainError? =
    when (cause) {
        is SSLException -> {
            DomainError.CertificateInvalid
        }

        is java.net.SocketTimeoutException -> {
            DomainError.Timeout
        }

        // Refused, unroutable and unresolvable were never sent. Any other socket failure (reset, broken pipe) or an
        // early end of stream happened after the request may have been written (review1b F-A-5).
        is ConnectException, is NoRouteToHostException, is UnknownHostException -> {
            DomainError.Unreachable
        }

        is SocketException, is EOFException -> {
            DomainError.ConnectionLost
        }

        else -> {
            null
        }
    }
