package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SpkiPin
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Where a call goes: an instance and the certificate pins stored for it (empty when it has none). */
internal class Destination(
    val baseUri: BaseUri,
    val pins: List<SpkiPin> = emptyList(),
)

/** One HTTP call to a Paperclip instance. [segments] are appended to `{baseUri}/api` and percent-encoded. */
internal class ApiRequest(
    val destination: Destination,
    val method: HttpMethod,
    val segments: List<String>,
    val query: List<Pair<String, String>> = emptyList(),
    val jsonBody: String? = null,
    val bearer: Secret? = null,
) {
    val baseUri: BaseUri get() = destination.baseUri
    val pins: List<SpkiPin> get() = destination.pins
}

/** A 2xx reply. [isJson] is false for an HTML proxy page or any other non-JSON body. */
internal data class ApiResponse(
    val status: Int,
    val body: String,
    val isJson: Boolean,
)

/** A 2xx reply read as bytes (an attachment slice). [contentRange] is the `Content-Range` header, if any. */
internal class ApiBytes(
    val status: Int,
    val bytes: ByteArray,
    val contentType: String?,
    val contentRange: String?,
)

/**
 * The only door to the network (AD-001). It sends each request exactly once: reads are retried by the use case with
 * `retryingRead`, mutations never are (AD-002). The key is attached per request from the [Secret] the caller hands in,
 * never kept here; redirects are not followed, so a key cannot be replayed to another host.
 *
 * [pinsEnforced] says whether the [engine] checks certificate pins itself. While none does, a request for an instance
 * that has pins fails closed with [DomainError.CertificateInvalid] (AD-005, ADR-040). [trace] is off by default; when
 * set it receives one line per call with the method, host, path and status, and never a query, header or body.
 */
class PaperclipHttpClient(
    engine: HttpClientEngine,
    private val allowInsecureLocal: Boolean,
    private val pinsEnforced: Boolean = false,
    private val trace: ((String) -> Unit)? = null,
) {
    private val http =
        HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = READ_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }

    fun close() = http.close()

    internal suspend fun send(request: ApiRequest): DomainResult<ApiResponse> = call(request, null, ::interpret)

    /**
     * Like [send] but keeps the body as bytes, because a binary reply must not be decoded as text (attachment slices).
     * [range] is the value of the `Range` header, for example `bytes=0-65535`.
     */
    internal suspend fun sendBytes(
        request: ApiRequest,
        range: String,
    ): DomainResult<ApiBytes> = call(request, range, ::interpretBytes)

    private suspend fun <T> call(
        request: ApiRequest,
        range: String?,
        read: suspend (HttpResponse) -> DomainResult<T>,
    ): DomainResult<T> {
        val refusal = refusal(request)
        if (refusal != null) return DomainResult.Failure(refusal)
        val result = execute(request, range, read)
        trace?.invoke(RequestRules.traceLine(request, result))
        return result
    }

    private fun refusal(request: ApiRequest): DomainError? =
        when {
            request.bearer?.isWiped == true -> DomainError.Unauthorized
            !RequestRules.isSchemeAllowed(request.baseUri, allowInsecureLocal) -> DomainError.CertificateInvalid
            request.pins.isNotEmpty() && !pinsEnforced -> DomainError.CertificateInvalid
            else -> null
        }

    private suspend fun <T> execute(
        request: ApiRequest,
        range: String?,
        read: suspend (HttpResponse) -> DomainResult<T>,
    ): DomainResult<T> =
        try {
            read(http.request(RequestRules.urlOf(request)) { configure(request, range) })
        } catch (cancelled: CancellationException) {
            // A timeout raised inside the engine can arrive as a cancellation; only our own cancellation propagates.
            currentCoroutineContext().ensureActive()
            DomainResult.Failure(ErrorMapper.fromTransport(cancelled))
        } catch (failure: Throwable) {
            DomainResult.Failure(ErrorMapper.fromTransport(failure))
        }

    private fun io.ktor.client.request.HttpRequestBuilder.configure(
        request: ApiRequest,
        range: String?,
    ) {
        method = request.method
        accept(ContentType.Application.Json)
        request.bearer?.let { header(HttpHeaders.Authorization, "Bearer ${it.reveal()}") }
        range?.let { header(HttpHeaders.Range, it) }
        request.jsonBody?.let { setBody(TextContent(it, ContentType.Application.Json)) }
    }

    private suspend fun interpret(response: HttpResponse): DomainResult<ApiResponse> {
        val status = response.status.value
        val body = response.bodyAsText()
        return if (status in SUCCESS) {
            DomainResult.Success(ApiResponse(status, body, response.contentType().isJson()))
        } else {
            DomainResult.Failure(ErrorMapper.fromStatus(status, body))
        }
    }

    private suspend fun interpretBytes(response: HttpResponse): DomainResult<ApiBytes> {
        val status = response.status.value
        return if (status in SUCCESS) {
            DomainResult.Success(
                ApiBytes(
                    status,
                    response.readRawBytes(),
                    response.headers[HttpHeaders.ContentType],
                    response.headers[HttpHeaders.ContentRange],
                ),
            )
        } else {
            DomainResult.Failure(ErrorMapper.fromStatus(status, response.bodyAsText()))
        }
    }

    private fun ContentType?.isJson(): Boolean =
        this != null && contentType == "application" && (contentSubtype == "json" || contentSubtype.endsWith("+json"))

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val READ_TIMEOUT_MS = 30_000L
        private const val REQUEST_TIMEOUT_MS = 60_000L
        private val SUCCESS = 200..299
    }
}

/** The client for the app: the platform's own engine (Darwin on iOS, OkHttp on Android and the JVM). */
fun createPaperclipHttpClient(
    allowInsecureLocal: Boolean,
    trace: ((String) -> Unit)? = null,
): PaperclipHttpClient = PaperclipHttpClient(platformEngine(), allowInsecureLocal, pinsEnforced = false, trace = trace)

internal expect fun platformEngine(): HttpClientEngine
