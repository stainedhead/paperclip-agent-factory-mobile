package com.stainedhead.paperclipmobile.infrastructure.platform

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

internal class ScriptedPathSource : PathSource {
    var onChange: ((Boolean) -> Unit)? = null
    var starts = 0
    var stops = 0

    override fun start(onChange: (Boolean) -> Unit): AutoCloseable {
        starts += 1
        this.onChange = onChange
        return AutoCloseable { stops += 1 }
    }

    fun report(online: Boolean) = onChange!!.invoke(online)
}

class NwConnectivityTest {
    private val source = ScriptedPathSource()

    @Test
    fun `AD-032 the monitor starts once and the phone is assumed online until it says otherwise`() {
        val connectivity = NwConnectivity(source)
        source.starts shouldBe 1
        connectivity.isOnline.value shouldBe true
    }

    @Test
    fun `AD-032 path changes flow to the state`() {
        val connectivity = NwConnectivity(source)
        source.report(false)
        connectivity.isOnline.value shouldBe false
        source.report(true)
        connectivity.isOnline.value shouldBe true
    }

    @Test
    fun `AD-032 closing stops the monitor once and later reports change nothing`() {
        val connectivity = NwConnectivity(source)
        connectivity.close()
        connectivity.close()
        source.stops shouldBe 1
        source.report(false)
        connectivity.isOnline.value shouldBe true
    }

    @Test
    fun `AD-032 the real path monitor reports its first path on its own queue and stops on close`() =
        runTest {
            val connectivity = NwConnectivity()
            try {
                withContext(Dispatchers.Default) { withTimeout(FIRST_PATH_MILLIS) { connectivity.firstPath.await() } }
                (connectivity.updates >= 1) shouldBe true
            } finally {
                connectivity.close()
            }
        }

    private companion object {
        const val FIRST_PATH_MILLIS = 5_000L
    }
}
