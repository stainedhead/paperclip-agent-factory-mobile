package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.instance.SweepOrphanSecrets
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.infrastructure.api.PaperclipHttpClient
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication

/**
 * The graph on the real adapters (AD-040): the same surface as the demo graph, with a database and a server behind
 * it. An empty database means no factories and the first-run flow, never demo data (AD-041).
 *
 * The explicit [platform] is the test seam and the way each platform app supplies its device services.
 */
internal fun PaperclipCore.createLiveGraph(
    allowInsecureLocal: Boolean,
    pairingBrowser: PairingBrowser,
    platform: LivePlatform,
): PaperclipGraph {
    var client: PaperclipHttpClient? = null
    return runCatching {
        val http = platform.httpClient(allowInsecureLocal).also { client = it }
        val application =
            koinApplication {
                modules(
                    livePortsModule(platform, http, pairingBrowser),
                    useCaseModule(allowInsecureLocal),
                    presentationModule(),
                )
            }
        PaperclipGraph(application.koin, LiveResources(http, platform), startupNoticeOf(platform))
    }.getOrElse { failure ->
        // A graph that cannot be built must not leave the database file open or the network monitor running.
        LiveResources(client, platform).close()
        throw failure
    }
}

/**
 * A recreated database means the factory list is gone while the secret store still holds the keys of those factories
 * (an iOS Keychain item survives even a reinstall): they are removed here, once, and the person is told (F-B-2).
 */
private fun startupNoticeOf(platform: LivePlatform): StartupNotice {
    if (!platform.persistence.recreated) return StartupNotice()
    val swept =
        runCatching {
            runBlocking { SweepOrphanSecrets(platform.persistence.instances, platform.secretStore)() }
        }.getOrNull()
    // The marker outlives this launch until a sweep completed with nothing left behind (F-R-1).
    if (swept?.complete == true) platform.persistence.sweepCompleted()
    return StartupNotice(databaseRecreated = true, orphanSecretsRemoved = swept?.removed ?: 0)
}

/** What a live graph owns and lets go of when it is closed, in order: the HTTP client, the database, the platform. */
internal class LiveResources(
    private val client: PaperclipHttpClient?,
    private val platform: LivePlatform,
) : AutoCloseable {
    override fun close() {
        try {
            client?.close()
        } finally {
            try {
                platform.persistence.close()
            } finally {
                platform.release()
            }
        }
    }
}
