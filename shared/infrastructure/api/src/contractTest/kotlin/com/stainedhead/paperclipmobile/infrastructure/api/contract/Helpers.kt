package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure

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

/**
 * Where the live server is, and whether this run may touch it at all (F-C-8). The contract test signs up a fixed
 * account, accepts an invite, pairs devices, creates companies and agents and cancels runs, so it must never reach
 * a real Paperclip. It runs only with the explicit opt-in set by contract.yml and scripts, against a loopback host
 * on the test server's port, and a server that reports authenticated and private.
 */
internal object ContractTarget {
    const val OPT_IN_VARIABLE = "PAPERCLIP_CONTRACT_I_KNOW_THIS_MUTATES"
    const val DEFAULT_TEST_PORT = 3199
    private const val DEVELOPER_PORT = 3100
    private const val NUMERIC_LOOPBACK = "127.0.0.1"
    private const val LOCALHOST = "localhost"
    private val LOOPBACK_HOSTS = setOf(LOCALHOST, NUMERIC_LOOPBACK)

    fun adapterBase(configured: String): String =
        configured.trim().trimEnd('/').replace("//$NUMERIC_LOOPBACK", "//$LOCALHOST")

    /** Why [configured] must not be used, or null. [testPort] is the `PAPERCLIP_TEST_PORT` of the test compose file. */
    fun refusal(
        configured: String,
        optIn: String?,
        testPort: String?,
    ): String? {
        if (optIn != "true") {
            return "Refusing to run: the contract test mutates the server it talks to. Set $OPT_IN_VARIABLE=true " +
                "(contract.yml and the documented run line do) only for the throwaway server of " +
                "scripts/paperclip-test-server.sh."
        }
        val uri = runCatching { java.net.URI(configured.trim()) }.getOrNull()
        val port = testPort?.toIntOrNull() ?: DEFAULT_TEST_PORT
        return when {
            uri == null || uri.host == null -> "Refusing to run: the base url is not a url."
            uri.scheme != "http" -> "Refusing to run: the test server speaks plain http on loopback only."
            uri.host !in LOOPBACK_HOSTS -> "Refusing to run: ${uri.host} is not a loopback host."
            uri.userInfo != null -> "Refusing to run: the base url carries credentials."
            uri.port == DEVELOPER_PORT -> "Refusing to run: port $DEVELOPER_PORT is the developer's own Paperclip."
            uri.port != port -> "Refusing to run: the test server listens on port $port, not ${uri.port}."
            else -> null
        }
    }

    /** The anonymous health fields the throwaway server shows: authenticated and private. Else it is not ours. */
    fun markerRefusal(
        mode: DeploymentMode,
        exposure: Exposure,
    ): String? =
        if (mode == DeploymentMode.Authenticated && exposure == Exposure.Private) {
            null
        } else {
            "Refusing to run: the server reports $mode / $exposure, the test server is authenticated / private."
        }
}
