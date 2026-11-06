package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.AppState
import com.stainedhead.paperclipmobile.presentation.AppStateHolder
import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.ApprovalStateHolder
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentState
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentStateHolder
import com.stainedhead.paperclipmobile.presentation.BudgetIntent
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DecisionState
import com.stainedhead.paperclipmobile.presentation.DecisionStateHolder
import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.EditInstanceState
import com.stainedhead.paperclipmobile.presentation.EditInstanceStateHolder
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoriesState
import com.stainedhead.paperclipmobile.presentation.FactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunState
import com.stainedhead.paperclipmobile.presentation.FirstRunStateHolder
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.InboxStateHolder
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesState
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.presentation.SettingsState
import com.stainedhead.paperclipmobile.presentation.SettingsStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What every shared state holder looks like from the app: a `StateFlow` of immutable states, one intent entry point and
 * `close()`. The holders share no interface (they are plain classes in `shared/presentation`), so each is wrapped once
 * below. It holds no rules: it forwards intents and releases the holder, exactly once (the Android counterpart of the
 * iPhone app's `HolderModel`).
 *
 * [isWorking] says whether a state has a mutation in flight; a screen left in that moment is closed only after the
 * outcome is recorded (ADR-117).
 */
class ScreenHolder<S, I>(
    val state: StateFlow<S>,
    private val deliver: (I) -> Unit,
    private val release: () -> Unit,
    /** What the refresh policy needs from the screen, for a screen that refreshes while visible (null for others). */
    val refreshTarget: RefreshTarget? = null,
    private val inFlight: (S) -> Boolean = { false },
) {
    private val closed = AtomicBoolean(false)
    private val closedFlow = MutableStateFlow(false)

    val isClosed: Boolean get() = closed.get()

    /** True while the holder has a mutation in flight. */
    val isWorking: Boolean get() = inFlight(state.value)

    fun isWorking(state: S): Boolean = inFlight(state)

    /**
     * Returns when the holder has no mutation in flight, or at once when it is closed: a closed holder's work was
     * cancelled with it and will never report an outcome, so a waiter must not outlive it (R-007).
     */
    suspend fun awaitIdle() {
        combine(state, closedFlow) { current, isClosedNow -> isClosedNow || !inFlight(current) }.first { it }
    }

    /** Forwards the intent; once closed the holder is gone and the intent is dropped. */
    fun send(intent: I) {
        if (!closed.get()) deliver(intent)
    }

    /** Releases the Kotlin holder. Only the owner calls this; safe to call more than once. */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            release()
            closedFlow.value = true
        }
    }
}

fun AppStateHolder.screenHolder() = ScreenHolder<AppState, AppIntent>(state, ::onIntent, ::close)

fun FirstRunStateHolder.screenHolder() = ScreenHolder<FirstRunState, FirstRunIntent>(state, ::onIntent, ::close)

fun InboxStateHolder.screenHolder() =
    ScreenHolder<InboxState, InboxIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }

fun FactoriesStateHolder.screenHolder() = ScreenHolder<FactoriesState, FactoriesIntent>(state, ::onIntent, ::close)

fun ManageFactoriesStateHolder.screenHolder() =
    ScreenHolder<ManageFactoriesState, ManageFactoriesIntent>(state, ::onIntent, ::close)

fun SettingsStateHolder.screenHolder() =
    ScreenHolder<SettingsState, SettingsIntent>(state, ::onIntent, ::close) { it.isWorking }

fun EditInstanceStateHolder.screenHolder() =
    ScreenHolder<EditInstanceState, EditInstanceIntent>(state, ::onIntent, ::close) { it.isWorking }

fun DecisionStateHolder.screenHolder() =
    ScreenHolder<DecisionState, DecisionIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }

fun ApprovalStateHolder.screenHolder() =
    ScreenHolder<ApprovalState, ApprovalIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }

fun BudgetIncidentStateHolder.screenHolder() =
    ScreenHolder<BudgetIncidentState, BudgetIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }
