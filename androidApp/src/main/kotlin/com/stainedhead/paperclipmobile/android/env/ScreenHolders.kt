package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DecisionState
import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.EditInstanceState
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoriesState
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesState
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.presentation.SettingsState
import com.stainedhead.paperclipmobile.umbrella.DecisionHints

/*
 * One function per screen that owns a shared state holder. The key names the screen, the entity it shows and the graph
 * session, so a replaced graph never reuses a holder (AND-011); the holder is closed with the back stack entry.
 */

internal fun GraphSession.openGraph() = checkNotNull(graph) { "A screen needs an open graph" }

/** The factory and company the person has selected: what a detail screen reads from. */
internal fun GraphSession.selection(): Pair<String, String> {
    val state = checkNotNull(app) { "A detail screen needs the app holder" }.state.value
    return checkNotNull(state.selectedInstanceId) { "A detail screen needs a factory" } to
        checkNotNull(state.selectedCompanyId) { "A detail screen needs a company" }
}

@Composable
fun rememberFactoriesHolder(session: GraphSession): HolderViewModel<FactoriesState, FactoriesIntent> =
    rememberHolderViewModel(
        "factories-${session.id}",
    ) { session.openGraph().createFactoriesStateHolder().screenHolder() }

/** The holder of the add (null) or edit screen of one factory. */
@Composable
fun rememberEditHolder(
    session: GraphSession,
    instanceId: String?,
): HolderViewModel<EditInstanceState, EditInstanceIntent> =
    rememberHolderViewModel("edit-${session.id}-${instanceId ?: "new"}") {
        session.openGraph().createEditInstanceStateHolder(instanceId).screenHolder()
    }

@Composable
fun rememberManageHolder(session: GraphSession): HolderViewModel<ManageFactoriesState, ManageFactoriesIntent> =
    rememberHolderViewModel("manage-${session.id}") {
        session.openGraph().createManageFactoriesStateHolder().screenHolder()
    }

/** One holder per screen that shows settings state: [screen] is "settings" or "devices", so each owns its own. */
@Composable
fun rememberSettingsHolder(
    session: GraphSession,
    screen: String,
): HolderViewModel<SettingsState, SettingsIntent> =
    rememberHolderViewModel("$screen-${session.id}") {
        session.openGraph().createSettingsStateHolder().screenHolder()
    }

/**
 * The holder of one decision of the selected factory and company. [hints] is what the Inbox card knew; without a
 * `dismissalKey` the screen offers no Snooze (ADR-025).
 */
@Composable
fun rememberDecisionHolder(
    session: GraphSession,
    id: String,
    hints: DecisionHints,
): HolderViewModel<DecisionState, DecisionIntent> {
    val (instance, company) = session.selection()
    return rememberHolderViewModel("decision-${session.id}-$instance-$company-$id") {
        session
            .openGraph()
            .attention
            .createDecisionStateHolder(instance, company, id, hints)
            .screenHolder()
    }
}

/** The holder of one approval of the selected factory and company. */
@Composable
fun rememberApprovalHolder(
    session: GraphSession,
    id: String,
): HolderViewModel<ApprovalState, ApprovalIntent> {
    val (instance, company) = session.selection()
    return rememberHolderViewModel("approval-${session.id}-$instance-$company-$id") {
        session
            .openGraph()
            .attention
            .createApprovalStateHolder(instance, company, id)
            .screenHolder()
    }
}
