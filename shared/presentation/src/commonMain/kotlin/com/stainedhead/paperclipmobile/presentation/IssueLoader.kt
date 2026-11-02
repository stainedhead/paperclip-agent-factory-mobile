package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the issue, its interactions and the lazy tabs (PRD FR-061). */
internal class IssueLoader(
    private val session: IssueSession,
) {
    private val model get() = session.model

    fun load() {
        if (!session.online()) {
            model.update { it.copy(loading = false) }
            return
        }
        if (!model.claim({ it.loading }) { it.copy(loading = true, error = null) }) return
        val started = model.value.version
        session.scope.launch {
            val allowed = session.context.get()
            if (allowed is DomainResult.Failure) {
                model.update { it.copy(error = allowed.error.toErrorUi()) }
            } else {
                read(started)
            }
            model.update { it.copy(loading = false) }
        }
    }

    private suspend fun read(started: Int) {
        when (val issue = session.useCases.load(session.companyScope, session.id)) {
            is DomainResult.Failure -> {
                model.update { it.copy(error = issue.error.toErrorUi()) }
            }

            is DomainResult.Success -> {
                val interactions = session.useCases.interactions.load(session.companyScope, session.id)
                val instance = session.useCases.getInstance(session.companyScope.instanceId)
                val identifier = issue.value.detail.issue.identifier
                model.update {
                    if (it.version != started) return@update it
                    it.copy(
                        view = issue.value,
                        interactions = (interactions as? DomainResult.Success)?.value ?: it.interactions,
                        interactionsUnavailable = interactions is DomainResult.Failure,
                        plan = issue.value.detail.plan,
                        role = session.context.role,
                        userId = session.context.userId,
                        issueUrl =
                            instance
                                ?.baseUri
                                ?.value
                                ?.trimEnd('/')
                                ?.plus("/issues/$identifier"),
                        loadedAtMillis = session.nowMillis(),
                    )
                }
            }
        }
    }

    /** Switches tab and reads what that tab needs: runs and files once, the plan every time. */
    fun openTab(tab: IssueTab) {
        model.update { it.copy(tab = tab, tabError = null) }
        if (!session.online()) return
        val current = model.value
        session.scope.launch {
            when {
                tab == IssueTab.Plan -> readPlan()
                tab == IssueTab.Runs && current.runs == null -> readRuns()
                tab == IssueTab.Files && current.files == null -> readFiles()
            }
        }
    }

    private suspend fun readPlan() {
        when (val plan = session.useCases.plan(session.companyScope, session.id)) {
            is DomainResult.Failure -> model.update { it.copy(tabError = plan.error.toErrorUi()) }
            is DomainResult.Success -> model.update { it.copy(plan = plan.value) }
        }
    }

    private suspend fun readRuns() {
        when (val runs = session.useCases.runs(session.companyScope, session.id)) {
            is DomainResult.Failure -> model.update { it.copy(tabError = runs.error.toErrorUi()) }
            is DomainResult.Success -> model.update { it.copy(runs = runs.value) }
        }
    }

    private suspend fun readFiles() {
        when (val files = session.useCases.files(session.companyScope, session.id)) {
            is DomainResult.Failure -> model.update { it.copy(tabError = files.error.toErrorUi()) }
            is DomainResult.Success -> model.update { it.copy(files = files.value) }
        }
    }
}
