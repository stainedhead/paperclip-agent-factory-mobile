package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.AgentDetailStateHolder
import com.stainedhead.paperclipmobile.presentation.AgentsStateHolder
import com.stainedhead.paperclipmobile.presentation.CompanyTarget
import com.stainedhead.paperclipmobile.presentation.FactoryHomeStateHolder
import com.stainedhead.paperclipmobile.presentation.IssueStateHolder
import com.stainedhead.paperclipmobile.presentation.RunViewerStateHolder
import com.stainedhead.paperclipmobile.presentation.WorkListStateHolder
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/** Creates the Work screens of slice 1a-3. Each call returns a holder the screen owns; `close()` it when done. */
class WorkScreens internal constructor(
    private val koin: Koin,
) {
    /** The Work list (screen 10) of one company of one factory. */
    fun createWorkListStateHolder(
        instanceId: String,
        companyId: String,
    ): WorkListStateHolder = koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId)) }

    /** The Issue screen (screen 4), with its thread, plan, runs, files and interaction forms. */
    fun createIssueStateHolder(
        instanceId: String,
        companyId: String,
        issueId: String,
    ): IssueStateHolder = koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId), issueId) }
}

/** Creates the Agents screens of slice 1a-3. */
class AgentScreens internal constructor(
    private val koin: Koin,
) {
    /** The Agents tab (screen 6). */
    fun createAgentsStateHolder(
        instanceId: String,
        companyId: String,
    ): AgentsStateHolder = koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId)) }

    /** Agent detail (screen 11). */
    fun createAgentDetailStateHolder(
        instanceId: String,
        companyId: String,
        agentId: String,
    ): AgentDetailStateHolder =
        koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId), agentId) }
}

/** Creates the Run viewer (screen 7). */
class RunScreens internal constructor(
    private val koin: Koin,
) {
    fun createRunViewerStateHolder(
        instanceId: String,
        companyId: String,
        runId: String,
    ): RunViewerStateHolder = koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId), runId) }
}

/** Creates Factory home (screen 5). */
class FactoryScreens internal constructor(
    private val koin: Koin,
) {
    fun createFactoryHomeStateHolder(
        instanceId: String,
        companyId: String,
    ): FactoryHomeStateHolder = koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId)) }
}
