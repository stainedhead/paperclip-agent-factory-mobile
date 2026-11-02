package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
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

        is ConnectException, is NoRouteToHostException, is UnknownHostException, is SocketException -> {
            DomainError.Unreachable
        }

        else -> {
            null
        }
    }
