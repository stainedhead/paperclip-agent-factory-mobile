package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppSheet
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecideByUi
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.OpenKindUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi

/*
 * Lookups the Inbox needs and nothing more (AND-030): where a card goes, which navigation button goes with its kind,
 * and the words that join. Which actions exist, in which order and for which role is the shared state's.
 */

private const val HTTPS = "https://"

/** Where a tapped card goes: a pushed route, the budget sheet, the web app, or nowhere. */
sealed interface InboxDestination {
    data class Route(
        val route: AppRoute,
    ) : InboxDestination

    data class Sheet(
        val sheet: AppSheet,
    ) : InboxDestination

    data class Web(
        val url: String,
    ) : InboxDestination

    data object None : InboxDestination
}

/** The decide-by written in the words the shared decision parser reads; a deadline-less card has none. */
fun DecideByUi.hintText(): String? =
    when (kind) {
        DecideByKindUi.Today -> "today"
        DecideByKindUi.ThisWeek -> "this_week"
        DecideByKindUi.Whenever -> "whenever"
        DecideByKindUi.OnDate -> isoDate
        DecideByKindUi.None -> null
    }

/** The mapping of the shared open target onto this app's routes (the shared policy decided the kind). */
fun inboxDestination(item: InboxItemUi): InboxDestination =
    when (item.open.kind) {
        OpenKindUi.Decision -> {
            InboxDestination.Route(
                AppRoute.Decision(
                    id = item.open.id,
                    originAgentName = item.originAgentName,
                    decideBy = item.decideBy.hintText(),
                    dismissalKey = item.dismissalKey,
                    identifier = item.identifier,
                ),
            )
        }

        OpenKindUi.Approval -> {
            InboxDestination.Route(AppRoute.Approval(item.open.id))
        }

        OpenKindUi.BudgetIncident -> {
            InboxDestination.Sheet(AppSheet.BudgetIncident(item.open.id))
        }

        OpenKindUi.Interaction, OpenKindUi.Issue -> {
            InboxDestination.Route(AppRoute.Issue(item.open.issueId ?: item.open.id))
        }

        OpenKindUi.Run -> {
            InboxDestination.Route(AppRoute.Run(item.open.id))
        }

        OpenKindUi.Agent -> {
            InboxDestination.Route(AppRoute.Agent(item.open.id))
        }

        OpenKindUi.WebOnly -> {
            item.webUrl?.takeIf { it.startsWith(HTTPS) }?.let { InboxDestination.Web(it) } ?: InboxDestination.None
        }
    }

/** The navigation button that goes with a card's kind: the inline actions come from the state, this never adds one. */
enum class InboxCardLink {
    ReviewOptions,
    Details,
    OpenInWeb,
    Chevron,
    ;

    companion object {
        fun of(item: InboxItemUi): InboxCardLink =
            when {
                item.kind == SourceKindUi.Decision -> ReviewOptions
                item.kind == SourceKindUi.Approval -> Details
                item.open.kind == OpenKindUi.WebOnly -> OpenInWeb
                else -> Chevron
            }
    }
}

/** The reason the card is here, plus the agent it came from when the reason does not already name it. */
fun cardContext(
    whyNow: String,
    origin: String?,
    separator: String,
): String =
    if (origin.isNullOrEmpty() ||
        whyNow.contains(origin, ignoreCase = true)
    ) {
        whyNow
    } else {
        "$whyNow$separator$origin"
    }

/** "Decision · PAP-1182": the kind and, when there is one, the identifier. */
fun kindLine(
    kind: String,
    identifier: String?,
    separator: String,
): String = listOfNotNull(kind, identifier).joinToString(separator)
