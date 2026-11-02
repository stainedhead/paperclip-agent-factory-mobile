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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
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

    internal suspend fun send(request: ApiRequest): DomainResult<ApiResponse> {
        val refusal = refusal(request)
        if (refusal != null) return DomainResult.Failure(refusal)
        val result = execute(request)
        trace?.invoke(traceLine(request, result))
        return result
    }

    private fun refusal(request: ApiRequest): DomainError? =
        when {
            request.bearer?.isWiped == true -> DomainError.Unauthorized
            !isSchemeAllowed(request.baseUri) -> DomainError.CertificateInvalid
            request.pins.isNotEmpty() && !pinsEnforced -> DomainError.CertificateInvalid
            else -> null
        }

    private fun isSchemeAllowed(baseUri: BaseUri): Boolean {
        val text = baseUri.value
        if (text.startsWith(HTTPS_PREFIX)) return true
        val host = text.removePrefix(HTTP_PREFIX).substringBefore('/').substringBeforeLast(':')
        return allowInsecureLocal && text.startsWith(HTTP_PREFIX) && host in INSECURE_LOCAL_HOSTS
    }

    private suspend fun execute(request: ApiRequest): DomainResult<ApiResponse> =
        try {
            val response = http.request(urlOf(request)) { configure(request) }
            interpret(response)
        } catch (cancelled: CancellationException) {
            // A timeout raised inside the engine can arrive as a cancellation; only our own cancellation propagates.
            currentCoroutineContext().ensureActive()
            DomainResult.Failure(ErrorMapper.fromTransport(cancelled))
        } catch (failure: Throwable) {
            DomainResult.Failure(ErrorMapper.fromTransport(failure))
        }

    private fun urlOf(request: ApiRequest): String =
        URLBuilder(request.baseUri.apiBase)
            .apply {
                appendPathSegments(request.segments, encodeSlash = true)
                request.query.forEach { (name, value) -> parameters.append(name, value) }
            }.buildString()

    private fun io.ktor.client.request.HttpRequestBuilder.configure(request: ApiRequest) {
        method = request.method
        accept(ContentType.Application.Json)
        request.bearer?.let { header(HttpHeaders.Authorization, "Bearer ${it.reveal()}") }
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

    private fun ContentType?.isJson(): Boolean =
        this != null && contentType == "application" && (contentSubtype == "json" || contentSubtype.endsWith("+json"))

    private fun traceLine(
        request: ApiRequest,
        result: DomainResult<ApiResponse>,
    ): String {
        val host =
            request.baseUri.value
                .substringAfter("://")
                .substringBefore('/')
        val path =
            URLBuilder(request.baseUri.apiBase)
                .appendPathSegments(request.segments, encodeSlash = true)
                .build()
                .encodedPath
        val outcome =
            when (result) {
                is DomainResult.Success -> result.value.status.toString()
                is DomainResult.Failure -> result.error::class.simpleName.orEmpty()
            }
        return "${request.method.value} $host $path -> $outcome"
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val READ_TIMEOUT_MS = 30_000L
        private const val REQUEST_TIMEOUT_MS = 60_000L
        private const val HTTPS_PREFIX = "https://"
        private const val HTTP_PREFIX = "http://"
        private val SUCCESS = 200..299
        private val INSECURE_LOCAL_HOSTS = setOf("localhost", "10.0.2.2")
    }
}

/** The client for the app: the platform's own engine (Darwin on iOS, OkHttp on Android and the JVM). */
fun createPaperclipHttpClient(
    allowInsecureLocal: Boolean,
    trace: ((String) -> Unit)? = null,
): PaperclipHttpClient = PaperclipHttpClient(platformEngine(), allowInsecureLocal, pinsEnforced = false, trace = trace)

internal expect fun platformEngine(): HttpClientEngine
