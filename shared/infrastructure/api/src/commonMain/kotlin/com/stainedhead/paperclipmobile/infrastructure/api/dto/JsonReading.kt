package com.stainedhead.paperclipmobile.infrastructure.api.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// Readers for the open-ended parts of a reply (payloads, metadata, effects, details). A field of the wrong type is
// null, never an error: the server redacts payloads and a newer server may reshape them (ADR-055).

/** A non-blank string field, as sent. */
internal fun JsonObject.text(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content

/** A number field as whole cents, rounded up exactly; a numeric string is accepted, anything else is null. */
internal fun JsonObject.cents(name: String): Long? =
    (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let(::ceilToCents)

internal fun JsonObject.whole(name: String): Int? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

internal fun JsonObject.flag(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

/** The non-blank strings of an array field; other elements are skipped. */
internal fun JsonObject.texts(name: String): List<String> =
    (this[name] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString && p.content.isNotBlank() }?.content }
        .orEmpty()

internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

internal fun JsonObject.objects(name: String): List<JsonObject> =
    (this[name] as? JsonArray)
        ?.mapNotNull {
            it as? JsonObject
        }.orEmpty()

internal fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

/** The integer part of a number field (a fractional percent is rounded down), without floating point. */
internal fun JsonObject.wholePart(name: String): Int? =
    (this[name] as? JsonPrimitive)
        ?.takeIf { it !is JsonNull }
        ?.content
        ?.substringBefore('.')
        ?.toIntOrNull()
