package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoriesState

/*
 * One function per screen that owns a shared state holder. The key names the screen, the entity it shows and the graph
 * session, so a replaced graph never reuses a holder (AND-011); the holder is closed with the back stack entry.
 */

private fun GraphSession.openGraph() = checkNotNull(graph) { "A screen needs an open graph" }

@Composable
fun rememberFactoriesHolder(session: GraphSession): HolderViewModel<FactoriesState, FactoriesIntent> =
    rememberHolderViewModel(
        "factories-${session.id}",
    ) { session.openGraph().createFactoriesStateHolder().screenHolder() }
