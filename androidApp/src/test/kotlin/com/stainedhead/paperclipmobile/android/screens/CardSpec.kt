package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.presentation.ChoiceUi
import com.stainedhead.paperclipmobile.presentation.InteractionKindUi
import com.stainedhead.paperclipmobile.presentation.InteractionPrimaryUi
import com.stainedhead.paperclipmobile.presentation.InteractionReadOnlyUi
import com.stainedhead.paperclipmobile.presentation.InteractionStatusUi
import com.stainedhead.paperclipmobile.presentation.InteractionUi
import com.stainedhead.paperclipmobile.presentation.InvalidKey
import com.stainedhead.paperclipmobile.presentation.QuestionUi
import com.stainedhead.paperclipmobile.presentation.VerdictItemUi
import com.stainedhead.paperclipmobile.presentation.VerdictUi

/** What a test says about a card; everything it does not name has a sensible default. */
data class CardSpec(
    val id: String = "int-1",
    val kind: InteractionKindUi = InteractionKindUi.RequestConfirmation,
    val status: InteractionStatusUi = InteractionStatusUi.Pending,
    val title: String? = null,
    val prompt: String? = null,
    val details: String? = null,
    val readOnly: InteractionReadOnlyUi? = null,
    val primary: InteractionPrimaryUi? = InteractionPrimaryUi.Accept,
    val hasReject: Boolean = false,
    val rejectRequiresReason: Boolean = false,
    val rejectReason: String = "",
    val rejectIssue: InvalidKey? = null,
    val submitIssue: InvalidKey? = null,
    val tasks: List<ChoiceUi> = emptyList(),
    val options: List<ChoiceUi> = emptyList(),
    val questions: List<QuestionUi> = emptyList(),
    val items: List<VerdictItemUi> = emptyList(),
    val enabledVerdicts: List<VerdictUi> = emptyList(),
    val openInWebUrl: String? = null,
)

/** An interaction card as the shared core would hand it to the screen. */
fun interactionCard(spec: CardSpec = CardSpec()) =
    InteractionUi(
        id = spec.id,
        kind = spec.kind,
        status = spec.status,
        title = spec.title,
        prompt = spec.prompt,
        detailsMarkdown = spec.details,
        canAnswer = spec.readOnly == null,
        readOnlyReason = spec.readOnly,
        primary = spec.primary,
        hasReject = spec.hasReject,
        rejectRequiresReason = spec.rejectRequiresReason,
        rejectReason = spec.rejectReason,
        rejectIssue = spec.rejectIssue,
        submitIssue = spec.submitIssue,
        submitIssueTargetId = null,
        tasks = spec.tasks,
        options = spec.options,
        questions = spec.questions,
        items = spec.items,
        enabledVerdicts = spec.enabledVerdicts,
        openInWebUrl = spec.openInWebUrl,
    )

fun choice(
    id: String,
    label: String,
    selected: Boolean = false,
    summary: String? = null,
) = ChoiceUi(id, label, summary, selected)
