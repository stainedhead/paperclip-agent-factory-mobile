package com.stainedhead.paperclipmobile.android.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.stainedhead.paperclipmobile.android.R
import kotlinx.serialization.Serializable

/** The five tabs, in display order. */
enum class AppTab(
    @get:StringRes val title: Int,
    @get:DrawableRes val icon: Int,
) {
    Inbox(R.string.tab_inbox, R.drawable.ic_tab_inbox),
    Factory(R.string.tab_factory, R.drawable.ic_tab_factory),
    Work(R.string.tab_work, R.drawable.ic_tab_work),
    Agents(R.string.tab_agents, R.drawable.ic_tab_agents),
    Factories(R.string.tab_factories, R.drawable.ic_tab_factories),
    ;

    /** Only the Inbox carries a badge: the server's count of what needs the person (`deskBadgeCount`). */
    fun badge(inboxBadgeCount: Int): Int? = if (this == Inbox && inboxBadgeCount > 0) inboxBadgeCount else null
}

/**
 * Where a push goes (AND-012). Routes carry ids only and are plain data, so a deep link can name one later (none is
 * handled yet); the destination looks up its own holder.
 */
@Serializable
sealed interface AppRoute {
    @Serializable
    data class Decision(
        val id: String,
    ) : AppRoute

    @Serializable
    data class Approval(
        val id: String,
    ) : AppRoute

    @Serializable
    data class Issue(
        val id: String,
    ) : AppRoute

    @Serializable
    data class Agent(
        val id: String,
    ) : AppRoute

    @Serializable
    data class Run(
        val id: String,
    ) : AppRoute

    @Serializable
    data object ManageFactories : AppRoute

    /** `null` is the add-a-factory form. */
    @Serializable
    data class EditFactory(
        val id: String? = null,
    ) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object PairedDevices : AppRoute
}

/** What opens over the tabs instead of being pushed. */
@Serializable
sealed interface AppSheet {
    @Serializable
    data class BudgetIncident(
        val id: String,
    ) : AppSheet

    @Serializable
    data object Settings : AppSheet
}

/** The root of every tab's own back stack. */
@Serializable
internal data object TabRoot
