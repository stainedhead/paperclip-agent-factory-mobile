package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.domain.Instance

private const val SCHEME_PREFIX = "paperclipmobile://add?"
private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private const val HEX = "0123456789ABCDEF"
private const val NIBBLE_BITS = 4
private const val BYTE_MASK = 0xFF
private const val NIBBLE_MASK = 0xF
private const val HEX_RADIX = 16
private const val PERCENT_ESCAPE_LENGTH = 3

/**
 * `paperclipmobile://add?uri=...&name=...` for setting up a second device (PRD FR-004, FR-008). It never carries
 * credentials.
 */
class BuildInstanceLink {
    operator fun invoke(instance: Instance): String =
        "${SCHEME_PREFIX}uri=${encode(instance.baseUri.value)}&name=${encode(instance.displayName)}"

    private fun encode(text: String): String =
        buildString {
            text.encodeToByteArray().forEach { byte ->
                val value = byte.toInt() and BYTE_MASK
                if (value.toChar() in UNRESERVED && value < BYTE_MASK) {
                    append(value.toChar())
                } else {
                    append('%').append(HEX[value shr NIBBLE_BITS]).append(HEX[value and NIBBLE_MASK])
                }
            }
        }
}

sealed interface AddLinkResult {
    /** The link only pre-fills the form; the person must confirm (PRD FR-004). */
    data class Parsed(
        val uri: String,
        val name: String?,
    ) : AddLinkResult

    data object Invalid : AddLinkResult
}

/** Reads an add-factory deep link. Only `uri` and `name` are accepted; anything else makes the link invalid. */
class ParseAddLink {
    operator fun invoke(link: String): AddLinkResult {
        if (!link.startsWith(SCHEME_PREFIX, ignoreCase = true)) return AddLinkResult.Invalid
        val params = mutableMapOf<String, String>()
        for (pair in link.substring(SCHEME_PREFIX.length).split('&')) {
            val key = pair.substringBefore('=')
            val value = decode(pair.substringAfter('=', "")) ?: return AddLinkResult.Invalid
            if ((key != "uri" && key != "name") || key in params) return AddLinkResult.Invalid
            params[key] = value
        }
        val uri = params["uri"]?.takeIf { it.isNotBlank() } ?: return AddLinkResult.Invalid
        return AddLinkResult.Parsed(uri, params["name"]?.takeIf { it.isNotBlank() })
    }

    private fun decode(text: String): String? {
        val bytes = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '%') {
                val value =
                    text
                        .substring(
                            index + 1,
                            minOf(index + PERCENT_ESCAPE_LENGTH, text.length),
                        ).takeIf { it.length == 2 }
                        ?.toIntOrNull(HEX_RADIX) ?: return null
                bytes.add(value.toByte())
                index += PERCENT_ESCAPE_LENGTH
            } else {
                char.toString().encodeToByteArray().forEach { bytes.add(it) }
                index += 1
            }
        }
        return runCatching { bytes.toByteArray().decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    }
}
