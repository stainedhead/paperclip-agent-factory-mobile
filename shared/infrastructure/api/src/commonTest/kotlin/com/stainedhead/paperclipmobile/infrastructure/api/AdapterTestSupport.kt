package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/** The value of a call that must have succeeded. */
internal fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

/** A failed result, to compare with. */
internal fun failed(error: DomainError) = DomainResult.Failure(error)

/** A client whose server answers every request with the same canned reply (the shapes a proxy or a bug can send). */
internal fun answeringClient(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    contentType: String = "application/json",
): PaperclipHttpClient =
    PaperclipHttpClient(
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) },
        allowInsecureLocal = false,
    )

/** Bodies a gateway must call Malformed whatever it asked for: html, empty, null and a wrong top-level type. */
internal val NOT_THE_EXPECTED_SHAPE =
    listOf("", "null", "<html><body>login</body></html>", "\"text\"", "12")
