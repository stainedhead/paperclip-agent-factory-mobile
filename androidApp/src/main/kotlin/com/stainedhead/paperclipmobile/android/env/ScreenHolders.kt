package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.Composable
import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.EditInstanceState
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoriesState
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesState
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.presentation.SettingsState

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
