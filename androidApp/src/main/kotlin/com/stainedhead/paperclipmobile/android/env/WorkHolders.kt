package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssueState
import com.stainedhead.paperclipmobile.presentation.IssueStateHolder
import com.stainedhead.paperclipmobile.presentation.WorkListIntent
import com.stainedhead.paperclipmobile.presentation.WorkListState
import com.stainedhead.paperclipmobile.presentation.WorkListStateHolder

// The holders of the Work list and the Issue screen, wrapped for the app.

fun WorkListStateHolder.screenHolder() =
    ScreenHolder<WorkListState, WorkListIntent>(state, ::onIntent, ::close, refreshTarget) { it.isCreating }

fun IssueStateHolder.screenHolder() =
    ScreenHolder<IssueState, IssueIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }

/** The holder of the Work tab for [selection]; a new factory or company is a new holder, so filters do not leak. */
@Composable
fun rememberWorkHolder(
    session: GraphSession,
    selection: Pair<String, String>,
): HolderViewModel<WorkListState, WorkListIntent> =
    rememberHolderViewModel("work-${session.id}-${selection.first}-${selection.second}") {
        session
            .openGraph()
            .work
            .createWorkListStateHolder(selection.first, selection.second)
            .screenHolder()
    }

/** The holder of one issue of the selected factory and company. */
@Composable
fun rememberIssueHolder(
    session: GraphSession,
    id: String,
): HolderViewModel<IssueState, IssueIntent> {
    val (instance, company) = session.selection()
    return rememberHolderViewModel("issue-${session.id}-$instance-$company-$id") {
        session
            .openGraph()
            .work
            .createIssueStateHolder(instance, company, id)
            .screenHolder()
    }
}
