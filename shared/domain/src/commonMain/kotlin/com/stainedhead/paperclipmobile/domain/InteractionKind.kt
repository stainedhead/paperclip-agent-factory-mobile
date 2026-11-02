package com.stainedhead.paperclipmobile.domain

/**
 * The six issue-thread interaction kinds of the pinned server (the sixth, `connection_intent`, is unsupported in v1).
 * Slice 1a-2 only needs the kind, to route an Inbox card; full handling is slice 1a-3.
 */
sealed interface InteractionKind {
    val serverName: String

    data object SuggestTasks : InteractionKind {
        override val serverName = "suggest_tasks"
    }

    data object AskUserQuestions : InteractionKind {
        override val serverName = "ask_user_questions"
    }

    data object RequestConfirmation : InteractionKind {
        override val serverName = "request_confirmation"
    }

    data object RequestCheckboxConfirmation : InteractionKind {
        override val serverName = "request_checkbox_confirmation"
    }

    data object RequestItemVerdicts : InteractionKind {
        override val serverName = "request_item_verdicts"
    }

    data object ConnectionIntent : InteractionKind {
        override val serverName = "connection_intent"
    }

    data class Unsupported(
        val raw: String,
    ) : InteractionKind {
        override val serverName: String get() = raw
    }

    companion object {
        val known: List<InteractionKind> =
            listOf(
                SuggestTasks,
                AskUserQuestions,
                RequestConfirmation,
                RequestCheckboxConfirmation,
                RequestItemVerdicts,
                ConnectionIntent,
            )

        fun fromServer(raw: String): InteractionKind = known.firstOrNull { it.serverName == raw } ?: Unsupported(raw)
    }
}
