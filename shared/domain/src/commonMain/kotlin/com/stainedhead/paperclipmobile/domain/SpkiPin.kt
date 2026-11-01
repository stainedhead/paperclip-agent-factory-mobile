package com.stainedhead.paperclipmobile.domain

/** A certificate pin: the base64 SHA-256 digest of a server public key (PRD FR-016). */
class SpkiPin private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is SpkiPin && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "SpkiPin($value)"

    companion object {
        private val BASE64_SHA256 = Regex("[A-Za-z0-9+/]{43}=")

        /** Returns the pin, or null when [text] is not a base64 SHA-256 digest. */
        fun parse(text: String): SpkiPin? = text.trim().takeIf { BASE64_SHA256.matches(it) }?.let { SpkiPin(it) }
    }
}
