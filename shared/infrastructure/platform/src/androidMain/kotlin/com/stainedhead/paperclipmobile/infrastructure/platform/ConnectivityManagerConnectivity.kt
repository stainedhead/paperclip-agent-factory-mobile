package com.stainedhead.paperclipmobile.infrastructure.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** Where network changes come from. A fake drives it in tests; [SystemNetworkMonitor] is `ConnectivityManager`. */
internal interface NetworkMonitor {
    /** Whether the default network is usable right now: it declares internet access, validated or not (DEC-2). */
    fun currentlyOnline(): Boolean

    /** Reports every change of the default network from now on; closing the result stops it. Throws if refused. */
    fun register(onChange: (Boolean) -> Unit): AutoCloseable
}

/** `ConnectivityManager.registerDefaultNetworkCallback`; "usable" means it declares internet access (DEC-2). */
internal class SystemNetworkMonitor(
    context: Context,
) : NetworkMonitor {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun currentlyOnline(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork).isUsable()

    override fun register(onChange: (Boolean) -> Unit): AutoCloseable {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) = onChange(networkCapabilities.isUsable())

                override fun onLost(network: Network) = onChange(false)

                override fun onUnavailable() = onChange(false)
            }
        manager.registerDefaultNetworkCallback(callback)
        return AutoCloseable { manager.unregisterNetworkCallback(callback) }
    }
}

private fun NetworkCapabilities?.isUsable(): Boolean =
    this?.let { capabilities -> isUsable(capabilities::hasCapability) } ?: false

/**
 * Whether a default network with the capabilities [has] answers for counts as online: it declares internet access,
 * validated or not (DEC-2, R-012). A home LAN, split DNS, a captive portal or a VPN whose probe fails is "online,
 * unknown": the request is attempted and the real success or typed failure shows. [has] is `hasCapability`.
 */
internal fun isUsable(has: (Int) -> Boolean): Boolean = has(NetworkCapabilities.NET_CAPABILITY_INTERNET)

/**
 * [Connectivity] from the system's default network (AND-003). The first value is read from the current default network
 * before the callback fires; after that the value follows the callback, and a network counts as online when it declares
 * internet access, validated or not (DEC-2). A callback the system refuses to register leaves the value at
 * offline (fail closed for mutations; reads use the cache). Call [close] when the graph is discarded.
 */
class ConnectivityManagerConnectivity internal constructor(
    monitor: NetworkMonitor,
) : Connectivity,
    AutoCloseable {
    constructor(context: Context) : this(SystemNetworkMonitor(context))

    private val online = MutableStateFlow(monitor.currentlyOnline())
    private val closed = AtomicBoolean(false)

    override val isOnline: StateFlow<Boolean> = online

    private val subscription: AutoCloseable? =
        try {
            monitor.register { usable -> if (!closed.get()) online.value = usable }
        } catch (_: IllegalStateException) {
            online.value = false
            null
        } catch (_: SecurityException) {
            online.value = false
            null
        }

    override fun close() {
        if (closed.compareAndSet(false, true)) subscription?.close()
    }
}
