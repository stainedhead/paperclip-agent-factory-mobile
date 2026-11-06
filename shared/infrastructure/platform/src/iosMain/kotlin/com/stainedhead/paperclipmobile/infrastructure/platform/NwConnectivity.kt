package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/** Where network path changes come from. [start] reports `true` for a usable path; closing the result stops it. */
internal interface PathSource {
    fun start(onChange: (Boolean) -> Unit): AutoCloseable
}

/** `NWPathMonitor` on its own serial dispatch queue; the handler runs on that queue, never on the main thread. */
@OptIn(ExperimentalForeignApi::class)
internal class NwPathSource : PathSource {
    override fun start(onChange: (Boolean) -> Unit): AutoCloseable {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.stainedhead.paperclipmobile.connectivity", null))
        nw_path_monitor_set_update_handler(monitor) { path ->
            onChange(path != null && nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_start(monitor)
        return AutoCloseable { nw_path_monitor_cancel(monitor) }
    }
}

/**
 * [Connectivity] from the system network path monitor (AD-032). It is assumed online until the first path arrives (a
 * moment after start), so a request is never blocked on a monitor that has not spoken yet; a request that cannot get
 * through fails on its own with a typed error. Call [close] when the graph is discarded.
 */
@OptIn(ExperimentalAtomicApi::class)
class NwConnectivity internal constructor(
    source: PathSource,
) : Connectivity,
    AutoCloseable {
    constructor() : this(NwPathSource())

    private val online = MutableStateFlow(true)
    private val closed = AtomicInt(0)
    private val updateCount = AtomicInt(0)
    internal val firstPath = CompletableDeferred<Unit>()

    override val isOnline: StateFlow<Boolean> = online

    internal val updates: Int get() = updateCount.load()

    private val subscription =
        source.start { usable ->
            if (closed.load() == 0) {
                online.value = usable
                updateCount.incrementAndFetch()
                firstPath.complete(Unit)
            }
        }

    override fun close() {
        if (closed.compareAndSet(0, 1)) subscription.close()
    }
}
