package com.stainedhead.paperclipmobile.android.env

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ForegroundObserverTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `the process starting reports foreground and stopping reports background`() {
        val reports = mutableListOf<Boolean>()
        val owner = Owner()
        owner.registry.addObserver(ForegroundObserver { reports += it })

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        reports shouldBe listOf(true, false, true)
    }
}
