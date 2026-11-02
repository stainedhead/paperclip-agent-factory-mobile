package com.stainedhead.paperclipmobile.domain

/** Path rules of [BaseUri.parse] (review F-A-10): the path prefix ends up inside every request URL. */
internal object PathRules {
    private const val LAST_CONTROL = 0x1f
    private const val DELETE = 0x7f
    private const val HEX_ESCAPE_LENGTH = 2
    private const val HEX_RADIX = 16
    private val ENCODED_SEPARATORS = setOf("2f", "5c")
    private val DOT_SEGMENTS = setOf(".", "..")

    /** [path] is everything after the authority: empty, `/`, or `/seg/seg` with an optional trailing slash. */
    fun isValid(path: String): Boolean {
        if (path.isEmpty() || path == "/") return true
        if (path.any(::isForbiddenChar)) return false
        return path.removePrefix("/").removeSuffix("/").split('/').all { segment ->
            val decoded = if (segment.isEmpty()) null else decode(segment)
            decoded != null && decoded !in DOT_SEGMENTS
        }
    }

    private fun isForbiddenChar(c: Char): Boolean = c.code <= LAST_CONTROL || c.code == DELETE || c == ' ' || c == '\\'

    /** The segment with escapes decoded, or null when an escape is malformed or hides a control char, `/` or `\\`. */
    private fun decode(segment: String): String? {
        val out = StringBuilder()
        var index = 0
        while (index < segment.length) {
            if (segment[index] != '%') {
                out.append(segment[index])
                index++
            } else {
                val hex = segment.drop(index + 1).take(HEX_ESCAPE_LENGTH)
                val code = hex.takeIf { it.length == HEX_ESCAPE_LENGTH }?.toIntOrNull(HEX_RADIX) ?: return null
                if (code <= LAST_CONTROL || code == DELETE || hex.lowercase() in ENCODED_SEPARATORS) return null
                out.append(code.toChar())
                index += 1 + HEX_ESCAPE_LENGTH
            }
        }
        return out.toString()
    }
}
