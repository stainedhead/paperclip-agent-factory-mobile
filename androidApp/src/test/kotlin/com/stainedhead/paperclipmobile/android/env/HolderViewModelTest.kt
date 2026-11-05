package com.stainedhead.paperclipmobile.android.env

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stainedhead.paperclipmobile.umbrella.PaperclipCore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.TimeUnit

/** AND-011: leaving the back stack closes a holder exactly once; nothing else does. */
class HolderViewModelTest {
    private val graph = PaperclipCore.createDemoGraph()

    @AfterEach
    fun tearDown() = graph.close()

    /** A store with a view model that owns [holder], the way a screen's `ViewModelStoreOwner` would hold it. */
    private class Owned<S, I>(
        val store: ViewModelStore,
        val model: HolderViewModel<S, I>,
    )

    private fun <S, I> own(
        holder: ScreenHolder<S, I>,
        idleScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ): Owned<S, I> {
        val store = ViewModelStore()
        var created: HolderViewModel<S, I>? = null
        val factory = viewModelFactory { initializer { HolderViewModel(holder, idleScope).also { created = it } } }
        ViewModelProvider.create(store, factory)[HolderViewModel::class.java]
        return Owned(store, checkNotNull(created))
    }

    private fun <S, I> closedAfterClearing(holder: ScreenHolder<S, I>): Boolean {
        val owned = own(holder)
        owned.model.holder.isClosed shouldBe false
        owned.store.clear()
        return holder.isClosed
    }

    @Test
    fun `clearing the store closes the holder once`() {
        var closes = 0
        val owned = own(ScreenHolder(MutableStateFlow(0), { _: Int -> }, { closes++ }))

        closes shouldBe 0
        owned.store.clear()
        owned.store.clear()

        closes shouldBe 1
        owned.model.holder.isClosed shouldBe true
    }

    @Test
    fun `intents reach the holder until it is closed`() {
        val received = mutableListOf<Int>()
        val owned = own(ScreenHolder(MutableStateFlow(0), { i: Int -> received += i }, {}))

        owned.model.onIntent(1)
        owned.store.clear()
        owned.model.onIntent(2)

        received shouldBe listOf(1)
    }

    @Test
    fun `a holder with a mutation in flight is closed after the outcome is recorded`() {
        var closes = 0
        val working = MutableStateFlow(true)
        val owned = own(ScreenHolder(working, { _: Boolean -> }, { closes++ }) { it })

        owned.store.clear()
        closes shouldBe 0
        working.value = false
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (closes == 0 && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)

        closes shouldBe 1
    }

    @Test
    fun `a working holder that is closed elsewhere ends the idle waiter`() {
        val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = ScreenHolder(MutableStateFlow(true), { _: Boolean -> }, {}) { it }
        own(holder, idleScope).store.clear()
        idleScope.coroutineContext[Job]!!.children.count { it.isActive } shouldBe 1

        holder.close()

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (idleScope.coroutineContext[Job]!!.children.any { it.isActive } && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS)
        }
        idleScope.coroutineContext[Job]!!.children.count { it.isActive } shouldBe 0
    }

    @TestFactory
    fun `every holder type of the graph is closed when its view model is cleared`() =
        listOf<Pair<String, () -> Boolean>>(
            "app" to { closedAfterClearing(graph.createAppStateHolder().screenHolder()) },
            "firstRun" to { closedAfterClearing(graph.createFirstRunStateHolder().screenHolder()) },
            "factories" to { closedAfterClearing(graph.createFactoriesStateHolder().screenHolder()) },
            "manageFactories" to { closedAfterClearing(graph.createManageFactoriesStateHolder().screenHolder()) },
            "settings" to { closedAfterClearing(graph.createSettingsStateHolder().screenHolder()) },
            "editFactory" to { closedAfterClearing(graph.createEditInstanceStateHolder(null).screenHolder()) },
            "inbox" to { closedAfterClearing(graph.attention.createInboxStateHolder("i", "c").screenHolder()) },
        ).map { (name, check) -> dynamicTest(name) { check() shouldBe true } }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val POLL_MILLIS = 10L
    }
}
