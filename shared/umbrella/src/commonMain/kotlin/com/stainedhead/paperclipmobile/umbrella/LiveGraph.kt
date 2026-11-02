package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.infrastructure.api.PaperclipHttpClient
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
    val client = platform.httpClient(allowInsecureLocal)
    val application =
        koinApplication {
            modules(
                livePortsModule(platform, client, pairingBrowser),
                useCaseModule(allowInsecureLocal),
                presentationModule(),
            )
        }
    return PaperclipGraph(application.koin, LiveResources(client, platform))
}

/** What a live graph owns and lets go of when it is closed, in order: the HTTP client, the database, the platform. */
internal class LiveResources(
    private val client: PaperclipHttpClient,
    private val platform: LivePlatform,
) : AutoCloseable {
    override fun close() {
        try {
            client.close()
        } finally {
            try {
                platform.persistence.close()
            } finally {
                platform.release()
            }
        }
    }
}
