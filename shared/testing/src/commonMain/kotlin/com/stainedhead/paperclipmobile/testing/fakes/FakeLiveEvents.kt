package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live events that a test (or the demo app) sends with [emit]. Nothing is replayed to a late collector. */
class FakeLiveEvents : LiveEvents {
    private val shared = MutableSharedFlow<LiveEvent>(extraBufferCapacity = BUFFER)

    override val events: Flow<LiveEvent> = shared

    /** How many collectors are subscribed right now: tests wait for it to rise and prove teardown when it falls. */
    val subscriberCount: StateFlow<Int> get() = shared.subscriptionCount

    suspend fun emit(event: LiveEvent) {
        shared.emit(event)
    }

    private companion object {
        const val BUFFER = 64
    }
}

/** An app that starts in the foreground and changes with [setForeground]. */
class FakeAppLifecycle(
    foreground: Boolean = true,
) : AppLifecycle {
    private val state = MutableStateFlow(foreground)

    override val isForeground: StateFlow<Boolean> = state

    fun setForeground(value: Boolean) {
        state.value = value
    }
}
