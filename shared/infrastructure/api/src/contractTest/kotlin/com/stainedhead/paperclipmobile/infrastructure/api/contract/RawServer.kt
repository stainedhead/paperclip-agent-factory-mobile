package com.stainedhead.paperclipmobile.infrastructure.api.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A reply of the raw client. [body] is the text as sent; it is never printed without [describe]. */
internal class RawResponse(
    val status: Int,
    val body: String,
    val setCookies: List<String>,
) {
    val json: JsonElement? get() = runCatching { Json.parseToJsonElement(body) }.getOrNull()

    val obj: JsonObject? get() = json as? JsonObject

    fun string(name: String): String? = (obj?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    fun array(): JsonArray? = json as? JsonArray

    /** One line for an assertion message: the status and a short body. Bodies of auth routes may hold tokens. */
    fun describe(): String = "HTTP $status ${body.take(MAX_BODY_IN_MESSAGE)}"

    private companion object {
        const val MAX_BODY_IN_MESSAGE = 300
    }
}

/**
 * The seeding client: plain JDK HTTP with a cookie header or a bearer key, the way the web app (session) and the
 * paired app (key) reach the server. It exists only for what the adapters cannot do (sign-up, approving a pairing,
 * seeding data, probing a malformed id); everything the app does goes through the production adapters.
 */
internal class RawServer(
    private val baseUrl: String,
) {
    private val http =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(CONNECT_SECONDS))
            .build()

    fun call(
        method: String,
        path: String,
        body: String? = null,
        bearer: String? = null,
        cookie: String? = null,
    ): RawResponse {
        val publisher =
            if (body ==
                null
            ) {
                HttpRequest.BodyPublishers.noBody()
            } else {
                HttpRequest.BodyPublishers.ofString(body)
            }
        val builder =
            HttpRequest
                .newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(REQUEST_SECONDS))
                .header("Accept", "application/json")
                // The server accepts a session mutation only from its own origin (board mutation guard, Better Auth).
                .header("Origin", baseUrl)
                .method(method, publisher)
        if (body != null) builder.header("Content-Type", "application/json")
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        cookie?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return RawResponse(response.statusCode(), response.body(), response.headers().allValues("set-cookie"))
    }

    private companion object {
        const val CONNECT_SECONDS = 10L
        const val REQUEST_SECONDS = 60L
    }
}
