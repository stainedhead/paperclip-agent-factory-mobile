package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** JSON as the server sends it: new fields are ignored, wrong types and missing required fields are errors. */
internal val ApiJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

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

/**
 * Maps a decoded DTO. A transform that returns null, or that a domain type refuses (a blank id), is
 * [DomainError.Malformed]: the server sent something this app cannot represent.
 */
internal inline fun <D, T> DomainResult<D>.map(transform: (D) -> T?): DomainResult<T> {
    if (this is DomainResult.Failure) return this
    val mapped =
        try {
            transform((this as DomainResult.Success).value)
        } catch (_: IllegalArgumentException) {
            null
        }
    return if (mapped != null) DomainResult.Success(mapped) else DomainResult.Failure(DomainError.Malformed)
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

private const val NO_CONTENT = 204
private const val RESET_CONTENT = 205

/**
 * A 2xx reply whose body the caller does not need. It is a success for a 204 or 205, or for a JSON body. Any other 2xx
 * (an HTML 200 from a proxy's login or fallback page, an empty 200 with no content type) did not come from Paperclip's
 * API and is [DomainError.Malformed] (review1b F-A-4).
 */
internal fun DomainResult<ApiResponse>.ack(): DomainResult<Unit> =
    when (this) {
        is DomainResult.Failure -> {
            this
        }

        is DomainResult.Success -> {
            if (value.status == NO_CONTENT || value.status == RESET_CONTENT || value.isJson) {
                DomainResult.Success(Unit)
            } else {
                DomainResult.Failure(DomainError.Malformed)
            }
        }
    }
