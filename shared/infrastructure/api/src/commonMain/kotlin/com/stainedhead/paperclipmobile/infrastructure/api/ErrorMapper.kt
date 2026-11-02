package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The one place where an HTTP status, an error body or a transport failure becomes a [DomainError] (AD-003).
 *
 * Neither function ever copies an exception message or a request URL into its result: Ktor puts the URL in several
 * messages and a pairing poll carries its challenge token in the query (research Q3).
 */
internal object ErrorMapper {
    const val MAX_REASON_LENGTH = 200

    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val REQUEST_TIMEOUT = 408
    private const val CONFLICT = 409
    private const val TOO_MANY_REQUESTS = 429
    private val CLIENT_ERRORS = 400..499
    private val SERVER_ERRORS = 500..599
    private const val MAX_CAUSE_DEPTH = 8

    private val lenientJson = Json { isLenient = true }

    /** A non-2xx reply. The status decides; the body only supplies the reason of a rejection. */
    fun fromStatus(
        status: Int,
        body: String,
    ): DomainError =
        when (status) {
            UNAUTHORIZED -> DomainError.Unauthorized
            FORBIDDEN -> DomainError.Forbidden
            NOT_FOUND -> DomainError.NotFound
            REQUEST_TIMEOUT -> DomainError.Timeout
            CONFLICT -> DomainError.Conflict
            TOO_MANY_REQUESTS -> DomainError.RateLimited
            in CLIENT_ERRORS -> DomainError.Rejected(reasonOf(status, body))
            in SERVER_ERRORS -> DomainError.ServerError
            else -> DomainError.Malformed
        }

    /** A request that never produced a reply. Looks through wrapped causes for the first one it recognises. */
    fun fromTransport(cause: Throwable): DomainError {
        var current: Throwable? = cause
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            classify(current)?.let { return it }
            current = current.cause
            depth += 1
        }
        return DomainError.Unreachable
    }

    private fun classify(cause: Throwable): DomainError? =
        when (cause) {
            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> DomainError.Timeout
            is SerializationException -> DomainError.Malformed
            else -> classifyPlatformFailure(cause)
        }

    private fun reasonOf(
        status: Int,
        body: String,
    ): String {
        val message =
            try {
                ((lenientJson.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.contentOrNull
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        return message?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_REASON_LENGTH) ?: "http_$status"
    }
}

/**
 * Names the failures only the platform's own engine can raise: a TLS or hostname failure is
 * [DomainError.CertificateInvalid], a platform timeout is [DomainError.Timeout], a refused or unroutable connection is
 * [DomainError.Unreachable]. Null means "not recognised"; the caller then looks at the next cause.
 */
internal expect fun classifyPlatformFailure(cause: Throwable): DomainError?
