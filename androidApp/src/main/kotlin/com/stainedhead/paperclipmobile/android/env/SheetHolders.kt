package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentState
import com.stainedhead.paperclipmobile.presentation.BudgetIntent

/**
 * Holders for a sheet. A sheet is not a back stack entry, so a holder made inside it would live as long as the screen
 * under it. This gives the sheet its own store: its holders are created when it opens and released when it leaves the
 * composition, through the same [HolderViewModel] rule as a screen (a mutation in flight is closed only after its
 * outcome is recorded, ADR-117). Holds no rule.
 */
@Composable
fun WithSheetHolders(content: @Composable () -> Unit) {
    val owner =
        remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** The holder of one budget incident of the selected factory and company; call it inside [WithSheetHolders]. */
@Composable
fun rememberBudgetHolder(
    session: GraphSession,
    id: String,
): HolderViewModel<BudgetIncidentState, BudgetIntent> {
    val (instance, company) = session.selection()
    return rememberHolderViewModel("budget-${session.id}-$instance-$company-$id") {
        session
            .openGraph()
            .attention
            .createBudgetIncidentStateHolder(instance, company, id)
            .screenHolder()
    }
}
