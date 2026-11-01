package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

/** What every interaction shares (research W7). */
data class InteractionMeta(
    val id: InteractionId,
    val status: InteractionStatus,
    val title: String?,
    val summary: String?,
    val createdAt: Instant,
    val audience: ResolverAudience,
    /** The server refuses accept while the workspace is syncing (`acceptanceBlocker`). */
    val acceptanceBlocked: Boolean = false,
    val resolvedAt: Instant? = null,
)

data class InteractionOption(
    val id: String,
    val label: String,
    val description: String? = null,
)

data class SuggestedTask(
    val clientKey: String,
    val title: String,
    val description: String?,
)

data class Question(
    val id: String,
    val prompt: String,
    val multi: Boolean,
    val required: Boolean,
    val allowOther: Boolean,
    val options: List<InteractionOption>,
    val helpText: String? = null,
)

data class InteractionItem(
    val id: String,
    val label: String,
    val description: String? = null,
)

enum class Verdict(
    val serverName: String,
) {
    Approve("approve"),
    Reject("reject"),
    Defer("defer"),
}

/**
 * An issue-thread interaction, modelled to the fields a native form renders (ADR-023). `connection_intent` and
 * kinds from a newer server are [UnsupportedInteraction]: shown with "open in web app", never answered.
 */
sealed interface Interaction {
    val meta: InteractionMeta
    val kind: InteractionKind
    val isSupported: Boolean get() = this !is UnsupportedInteraction

    /** Pending, unblocked, supported and not addressed to someone else (the creator rule is the server's). */
    fun canBeResolvedBy(userId: String?): Boolean =
        isSupported && meta.status.isOpen && !meta.acceptanceBlocked && meta.audience.allowsUser(userId)
}

data class SuggestTasks(
    override val meta: InteractionMeta,
    val tasks: List<SuggestedTask>,
) : Interaction {
    override val kind: InteractionKind get() = InteractionKind.SuggestTasks
}

data class AskUserQuestions(
    override val meta: InteractionMeta,
    val questions: List<Question>,
    val submitLabel: String? = null,
) : Interaction {
    override val kind: InteractionKind get() = InteractionKind.AskUserQuestions
}

data class RequestConfirmation(
    override val meta: InteractionMeta,
    val prompt: String,
    val acceptLabel: String? = null,
    val rejectLabel: String? = null,
    val rejectRequiresReason: Boolean = false,
    val detailsMarkdown: String? = null,
) : Interaction {
    override val kind: InteractionKind get() = InteractionKind.RequestConfirmation
}

data class RequestCheckboxConfirmation(
    override val meta: InteractionMeta,
    val prompt: String,
    val options: List<InteractionOption>,
    val defaultSelected: Set<String> = emptySet(),
    val minSelected: Int = 0,
    val maxSelected: Int? = null,
    val rejectRequiresReason: Boolean = false,
    val detailsMarkdown: String? = null,
) : Interaction {
    override val kind: InteractionKind get() = InteractionKind.RequestCheckboxConfirmation
}

data class RequestItemVerdicts(
    override val meta: InteractionMeta,
    val prompt: String,
    val items: List<InteractionItem>,
    val verdicts: Set<Verdict> = setOf(Verdict.Approve, Verdict.Reject),
    val requireReasonOn: Set<Verdict> = setOf(Verdict.Reject),
    val detailsMarkdown: String? = null,
) : Interaction {
    override val kind: InteractionKind get() = InteractionKind.RequestItemVerdicts
}

data class UnsupportedInteraction(
    override val meta: InteractionMeta,
    override val kind: InteractionKind,
) : Interaction
