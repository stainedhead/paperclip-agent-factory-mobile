package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.presentation.AgentDetailIntent
import com.stainedhead.paperclipmobile.presentation.AgentDetailState
import com.stainedhead.paperclipmobile.presentation.AgentDetailStateHolder
import com.stainedhead.paperclipmobile.presentation.AgentsIntent
import com.stainedhead.paperclipmobile.presentation.AgentsState
import com.stainedhead.paperclipmobile.presentation.AgentsStateHolder
import com.stainedhead.paperclipmobile.presentation.FactoryHomeIntent
import com.stainedhead.paperclipmobile.presentation.FactoryHomeState
import com.stainedhead.paperclipmobile.presentation.FactoryHomeStateHolder
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerState
import com.stainedhead.paperclipmobile.presentation.RunViewerStateHolder

// The holders of the operations screens (Factory home, Agents, Agent detail, Run viewer), wrapped for the app.

fun FactoryHomeStateHolder.screenHolder() =
    ScreenHolder<FactoryHomeState, FactoryHomeIntent>(state, ::onIntent, ::close, refreshTarget)

fun AgentsStateHolder.screenHolder() =
    ScreenHolder<AgentsState, AgentsIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }

fun AgentDetailStateHolder.screenHolder() =
    ScreenHolder<AgentDetailState, AgentDetailIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }

fun RunViewerStateHolder.screenHolder() =
    ScreenHolder<RunViewerState, RunViewerIntent>(state, ::onIntent, ::close, refreshTarget) { it.isWorking }
