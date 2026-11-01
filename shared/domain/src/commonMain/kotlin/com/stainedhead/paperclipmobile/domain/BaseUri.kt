package com.stainedhead.paperclipmobile.domain

/** Why an address typed or pasted by the user cannot be used as a Paperclip instance address. */
enum class BaseUriError {
    Empty,
    MissingScheme,
    UnsupportedScheme,
    InsecureHttp,
    MissingHost,
    InvalidHost,
    InvalidPort,
    UserInfoNotAllowed,
    QueryOrFragmentNotAllowed,
}

sealed interface BaseUriResult {
    data class Valid(
        val uri: BaseUri,
    ) : BaseUriResult

    data class Invalid(
        val error: BaseUriError,
    ) : BaseUriResult
}

/**
 * The normalised address of one Paperclip instance, for example `https://paperclip.example.com/paperclip`.
 *
 * Rules (PRD FR-003): scheme must be `https`; `http` is accepted only for `localhost` and `10.0.2.2` and only when the
 * caller says insecure local addresses are allowed (debug builds); a host is required; user info, query strings and
 * fragments are rejected; an explicit port and a path prefix are kept; scheme and host are lower-cased and a trailing
 * slash is removed.
 */
class BaseUri private constructor(
    val value: String,
) {
    /** The REST API root, `{baseUri}/api`. */
    val apiBase: String get() = "$value/api"

    override fun equals(other: Any?): Boolean = other is BaseUri && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private const val SCHEME_SEPARATOR = "://"
        private const val MAX_PORT = 65_535
        private const val MAX_PORT_DIGITS = 5
        private val INSECURE_LOCAL_HOSTS = setOf("localhost", "10.0.2.2")
        private val HOST_NAME = Regex("[a-z0-9]([a-z0-9.-]*[a-z0-9])?")
        private val IPV6_HOST = Regex("\\[[0-9a-f:.]+]")

        fun parse(
            input: String,
            allowInsecureLocal: Boolean = false,
        ): BaseUriResult {
            val text = input.trim()
            val error = validate(text, allowInsecureLocal)
            return if (error != null) BaseUriResult.Invalid(error) else BaseUriResult.Valid(BaseUri(normalise(text)))
        }

        private fun validate(
            text: String,
            allowInsecureLocal: Boolean,
        ): BaseUriError? {
            if (text.isEmpty()) return BaseUriError.Empty
            val schemeEnd = text.indexOf(SCHEME_SEPARATOR)
            if (schemeEnd < 0) return BaseUriError.MissingScheme
            val scheme = text.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return BaseUriError.UnsupportedScheme
            val rest = text.substring(schemeEnd + SCHEME_SEPARATOR.length)
            if (rest.contains('?') || rest.contains('#')) return BaseUriError.QueryOrFragmentNotAllowed
            return validateAuthority(scheme, rest.substringBefore('/'), allowInsecureLocal)
        }

        private fun validateAuthority(
            scheme: String,
            authority: String,
            allowInsecureLocal: Boolean,
        ): BaseUriError? {
            if (authority.isEmpty()) return BaseUriError.MissingHost
            if (authority.contains('@')) return BaseUriError.UserInfoNotAllowed
            val (host, port) = splitHostAndPort(authority)
            val lowerHost = host.lowercase()
            return when {
                port != null && !isValidPort(port) -> {
                    BaseUriError.InvalidPort
                }

                !isValidHost(lowerHost) -> {
                    BaseUriError.InvalidHost
                }

                scheme == "http" && !(allowInsecureLocal && lowerHost in INSECURE_LOCAL_HOSTS) -> {
                    BaseUriError.InsecureHttp
                }

                else -> {
                    null
                }
            }
        }

        private fun splitHostAndPort(authority: String): Pair<String, String?> {
            val portSeparator =
                if (authority.startsWith('[')) {
                    authority.indexOf(':', authority.indexOf(']').coerceAtLeast(0))
                } else {
                    authority.lastIndexOf(':')
                }
            return if (portSeparator < 0) {
                authority to null
            } else {
                authority.substring(0, portSeparator) to authority.substring(portSeparator + 1)
            }
        }

        private fun isValidPort(port: String): Boolean =
            port.isNotEmpty() && port.all { it in '0'..'9' } &&
                port.length <= MAX_PORT_DIGITS && port.toInt() in 1..MAX_PORT

        private fun isValidHost(host: String): Boolean = HOST_NAME.matches(host) || IPV6_HOST.matches(host)

        private fun normalise(text: String): String {
            val schemeEnd = text.indexOf(SCHEME_SEPARATOR)
            val scheme = text.substring(0, schemeEnd).lowercase()
            val rest = text.substring(schemeEnd + SCHEME_SEPARATOR.length)
            val authority = rest.substringBefore('/').lowercase()
            val path = rest.removePrefix(rest.substringBefore('/')).trimEnd('/')
            return "$scheme://$authority$path"
        }
    }
}
