package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A `ViewModel` that owns one shared state holder (AND-011). The holder survives a configuration change because the
 * view model does; it is closed when the screen's back stack entry is gone (`onCleared`), never earlier. A screen left
 * while its mutation is in flight is closed only after the outcome is recorded (ADR-117), so the request and the read
 * that reconciles it finish with the holder.
 *
 * It holds no rules: [state] is the holder's flow and [onIntent] forwards.
 */
class HolderViewModel<S, I>(
    val holder: ScreenHolder<S, I>,
    private val idleScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    val state: StateFlow<S> get() = holder.state

    fun onIntent(intent: I) = holder.send(intent)

    override fun onCleared() {
        if (!holder.isWorking) {
            release()
        } else {
            idleScope.launch {
                holder.awaitIdle()
                release()
            }
        }
    }

    private fun release() {
        holder.close()
        idleScope.cancel()
    }
}

/**
 * The view model of the current back stack entry for [key], created with [create] on first use. Use a key that names
 * the screen and the entity it shows (route and ids), and the graph session, so a replaced graph never reuses a holder.
 */
@Composable
fun <S, I> rememberHolderViewModel(
    key: String,
    create: () -> ScreenHolder<S, I>,
): HolderViewModel<S, I> =
    viewModel<HolderViewModel<S, I>>(
        key = key,
        factory = viewModelFactory { initializer { HolderViewModel(create()) } },
    )
