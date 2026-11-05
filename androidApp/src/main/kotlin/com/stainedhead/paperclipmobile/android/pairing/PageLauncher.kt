package com.stainedhead.paperclipmobile.android.pairing

import java.net.URI

/** What the pairing browser tells the app about the page. No case carries the link: it is a secret. */
enum class PairingBrowserEvent {
    Presented,
    Dismissed,
    CouldNotPresent,
}

/**
 * The one thing the pairing browser needs from the system, so tests can stand in for it. Each call returns false when
 * nothing could show the page, and never throws.
 */
interface PageLauncher {
    /** Opens [url] in a Custom Tab; false when there is no Custom Tabs provider. */
    fun openCustomTab(url: String): Boolean

    /** Opens [url] with whatever handles a plain view of a web address; false when nothing does. */
    fun openBrowser(url: String): Boolean
}

private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

/**
 * The address to open, or null when it must not be opened. Only `https` opens, plus plain `http` to a local host in a
 * debug build (the same rule as factory addresses, AGENTS.md "Security Rules").
 */
fun acceptablePage(
    url: String,
    allowsInsecureLocal: Boolean,
): String? {
    val parsed = runCatching { URI(url) }.getOrNull() ?: return null
    val host = parsed.host?.takeIf { it.isNotEmpty() } ?: return null
    val allowed =
        when (parsed.scheme?.lowercase()) {
            "https" -> true
            "http" -> allowsInsecureLocal && host in LOCAL_HOSTS
            else -> false
        }
    return url.takeIf { allowed }
}
