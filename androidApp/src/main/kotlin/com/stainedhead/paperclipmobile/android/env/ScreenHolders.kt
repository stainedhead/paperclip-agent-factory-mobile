package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.presentation.AgentDetailIntent
import com.stainedhead.paperclipmobile.presentation.AgentDetailState
import com.stainedhead.paperclipmobile.presentation.AgentsIntent
import com.stainedhead.paperclipmobile.presentation.AgentsState
import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DecisionState
import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.EditInstanceState
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoriesState
import com.stainedhead.paperclipmobile.presentation.FactoryHomeIntent
import com.stainedhead.paperclipmobile.presentation.FactoryHomeState
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesState
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerState
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

/**
 * The factory and company a tab root shows, or null while the app holder has none yet (a tab root can be drawn before
 * the selection settles, so it shows a spinner then instead of failing).
 */
@Composable
fun rememberSelection(session: GraphSession): Pair<String, String>? {
    val state by checkNotNull(session.app) { "A tab root needs the app holder" }.state.collectAsStateWithLifecycle()
    val instance = state.selectedInstanceId
    val company = state.selectedCompanyId
    return if (instance != null && company != null) instance to company else null
}

/** The holder of Factory home for [selection]; a new factory or company is a new holder. */
@Composable
fun rememberFactoryHomeHolder(
    session: GraphSession,
    selection: Pair<String, String>,
): HolderViewModel<FactoryHomeState, FactoryHomeIntent> =
    rememberHolderViewModel("factory-home-${session.id}-${selection.first}-${selection.second}") {
        session
            .openGraph()
            .factory
            .createFactoryHomeStateHolder(selection.first, selection.second)
            .screenHolder()
    }

/** The holder of the Agents tab for [selection]. */
@Composable
fun rememberAgentsHolder(
    session: GraphSession,
    selection: Pair<String, String>,
): HolderViewModel<AgentsState, AgentsIntent> =
    rememberHolderViewModel("agents-${session.id}-${selection.first}-${selection.second}") {
        session
            .openGraph()
            .agents
            .createAgentsStateHolder(selection.first, selection.second)
            .screenHolder()
    }

/** The holder of one agent of the selected factory and company. */
@Composable
fun rememberAgentDetailHolder(
    session: GraphSession,
    id: String,
): HolderViewModel<AgentDetailState, AgentDetailIntent> {
    val (instance, company) = session.selection()
    return rememberHolderViewModel("agent-${session.id}-$instance-$company-$id") {
        session
            .openGraph()
            .agents
            .createAgentDetailStateHolder(instance, company, id)
            .screenHolder()
    }
}

/** The holder of one run of the selected factory and company. */
@Composable
fun rememberRunHolder(
    session: GraphSession,
    id: String,
): HolderViewModel<RunViewerState, RunViewerIntent> {
    val (instance, company) = session.selection()
    return rememberHolderViewModel("run-${session.id}-$instance-$company-$id") {
        session
            .openGraph()
            .runs
            .createRunViewerStateHolder(instance, company, id)
            .screenHolder()
    }
}
