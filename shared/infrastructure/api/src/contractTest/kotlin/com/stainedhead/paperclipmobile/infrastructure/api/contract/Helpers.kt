package com.stainedhead.paperclipmobile.infrastructure.api.contract

/** Reads a bootstrap invite token from what the operator has: the bare token or the invite url. Never logs it. */
internal object InviteToken {
    private val TOKEN = Regex("pcp_bootstrap_[0-9a-f]+")

    fun parse(text: String): String? = TOKEN.find(text)?.value
}

/** Keeps the cookies of one session (name to value). A cookie set to an empty value or `Max-Age=0` is dropped. */
internal class CookieJar {
    private val cookies = linkedMapOf<String, String>()

    fun store(setCookieHeaders: List<String>) {
        setCookieHeaders.forEach { line ->
            val parts = line.split(';').map { it.trim() }
            val pair = parts.first()
            val name = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "")
            val expired = parts.drop(1).any { it.equals("Max-Age=0", ignoreCase = true) }
            when {
                name.isEmpty() -> Unit
                value.isEmpty() || expired -> cookies.remove(name)
                else -> cookies[name] = value
            }
        }
    }

    fun header(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    val isEmpty: Boolean get() = cookies.isEmpty()
}

/** Where the live server is. Plain http is allowed for `localhost` only, so a numeric loopback is rewritten. */
internal object ContractTarget {
    private const val NUMERIC_LOOPBACK = "127.0.0.1"
    private const val LOCALHOST = "localhost"

    fun adapterBase(configured: String): String =
        configured.trim().trimEnd('/').replace("//$NUMERIC_LOOPBACK", "//$LOCALHOST")
}
