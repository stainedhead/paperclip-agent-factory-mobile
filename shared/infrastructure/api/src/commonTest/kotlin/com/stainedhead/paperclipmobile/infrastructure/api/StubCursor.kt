package com.stainedhead.paperclipmobile.infrastructure.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The server's cursor: base64url of `{v:1, sort, id}` (`services/attention.ts:534-547`), bound to the sort mode. */
@OptIn(ExperimentalEncodingApi::class)
internal object StubCursor {
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun encode(
        sort: String,
        id: String,
    ): String =
        codec.encode(
            buildJsonObject {
                put("v", 1)
                put("sort", sort)
                put("id", id)
            }.toString().encodeToByteArray(),
        )

    /** The item id the cursor names, or null when it is not a cursor of this sort mode. */
    fun decode(
        cursor: String,
        sort: String,
    ): String? =
        try {
            val json = Json.parseToJsonElement(codec.decode(cursor).decodeToString()) as JsonObject
            val id = (json["id"] as? JsonPrimitive)?.content
            val matches =
                (json["v"] as? JsonPrimitive)?.content == "1" && (json["sort"] as? JsonPrimitive)?.content == sort
            if (matches && !id.isNullOrEmpty()) id else null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        }
}
