package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DomainResult
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

/** What every request shares: where it goes, whether the scheme is allowed, and its trace line. */
internal object RequestRules {
    private const val HTTPS_PREFIX = "https://"
    private const val HTTP_PREFIX = "http://"
    private val INSECURE_LOCAL_HOSTS = setOf("localhost", "10.0.2.2")

    fun isSchemeAllowed(
        baseUri: BaseUri,
        allowInsecureLocal: Boolean,
    ): Boolean {
        val text = baseUri.value
        if (text.startsWith(HTTPS_PREFIX)) return true
        val host = text.removePrefix(HTTP_PREFIX).substringBefore('/').substringBeforeLast(':')
        return allowInsecureLocal && text.startsWith(HTTP_PREFIX) && host in INSECURE_LOCAL_HOSTS
    }

    fun urlOf(request: ApiRequest): String =
        URLBuilder(request.baseUri.apiBase)
            .apply {
                appendPathSegments(request.segments, encodeSlash = true)
                request.query.forEach { (name, value) -> parameters.append(name, value) }
            }.buildString()

    /** Method, host, path and outcome; never a query, a header or a body. */
    fun traceLine(
        request: ApiRequest,
        result: DomainResult<*>,
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
                is DomainResult.Success -> (result.value as? ApiResponse)?.status ?: (result.value as? ApiBytes)?.status
                is DomainResult.Failure -> result.error::class.simpleName.orEmpty()
            }
        return "${request.method.value} $host $path -> $outcome"
    }
}
