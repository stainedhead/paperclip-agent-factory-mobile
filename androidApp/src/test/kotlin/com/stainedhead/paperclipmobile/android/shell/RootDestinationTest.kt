package com.stainedhead.paperclipmobile.android.shell

import com.stainedhead.paperclipmobile.presentation.AppState
import com.stainedhead.paperclipmobile.presentation.LockState
import com.stainedhead.paperclipmobile.presentation.StartupNotice
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** What the app shows: the notice, loading, the lock, first run (also while its flow is on), or the tab shell. */
class RootDestinationTest {
    private val ready = AppState(isLoaded = true, lock = LockState.Unlocked, hasFactories = true)

    @Test
    fun `a startup notice beats everything`() {
        rootDestination(StartupNotice.LiveGraphUnavailable, ready, firstRunInProgress = false) shouldBe
            RootDestination.StartupNotice
    }

    @Test
    fun `nothing shows before the first settings read`() {
        rootDestination(null, AppState(isLoaded = false), firstRunInProgress = false) shouldBe RootDestination.Loading
    }

    @Test
    fun `a locked app shows the lock before anything else`() {
        rootDestination(null, ready.copy(lock = LockState.Locked), firstRunInProgress = true) shouldBe
            RootDestination.Lock
    }

    @Test
    fun `no factory means first run`() {
        rootDestination(null, ready.copy(hasFactories = false), firstRunInProgress = false) shouldBe
            RootDestination.FirstRun
    }

    @Test
    fun `first run keeps the screen while its flow is on even after the factory is saved`() {
        rootDestination(null, ready, firstRunInProgress = true) shouldBe RootDestination.FirstRun
    }

    @Test
    fun `a factory and no flow in progress shows the tabs`() {
        rootDestination(null, ready, firstRunInProgress = false) shouldBe RootDestination.Tabs
    }

    @Test
    fun `pairing and the lock hide the window from screenshots`() {
        screenIsSecure(RootDestination.Lock, pairing = false) shouldBe true
        screenIsSecure(RootDestination.FirstRun, pairing = true) shouldBe true
        screenIsSecure(RootDestination.FirstRun, pairing = false) shouldBe false
        screenIsSecure(RootDestination.Tabs, pairing = false) shouldBe false
    }
}
