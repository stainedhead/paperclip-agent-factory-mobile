package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** JSON as the server sends it: new fields are ignored, wrong types and missing required fields are errors. */
internal val ApiJson = Json { ignoreUnknownKeys = true }

/**
 * Reads a 2xx reply as [T]. A body that is not JSON (an HTML proxy page), is empty, or does not fit [T] is
 * [DomainError.Malformed]; the body never reaches the error (AD-003).
 */
internal inline fun <reified T> DomainResult<ApiResponse>.decode(): DomainResult<T> =
    when (this) {
        is DomainResult.Failure -> this
        is DomainResult.Success -> value.decodeAs<T>()
    }

internal inline fun <reified T> ApiResponse.decodeAs(): DomainResult<T> {
    if (!isJson) return DomainResult.Failure(DomainError.Malformed)
    return try {
        DomainResult.Success(ApiJson.decodeFromString<T>(body))
    } catch (_: SerializationException) {
        DomainResult.Failure(DomainError.Malformed)
    } catch (_: IllegalArgumentException) {
        DomainResult.Failure(DomainError.Malformed)
    }
}

/** Maps a decoded DTO, turning a value that cannot be mapped into [DomainError.Malformed]. */
internal inline fun <D, T> DomainResult<D>.map(transform: (D) -> T?): DomainResult<T> =
    when (this) {
        is DomainResult.Failure -> {
            this
        }

        is DomainResult.Success -> {
            transform(value)?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.Malformed)
        }
    }

/** An ISO-8601 timestamp as the server writes it, or null when absent or unreadable. */
internal fun parseInstantOrNull(text: String?): Instant? =
    text?.let {
        try {
            Instant.parse(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
