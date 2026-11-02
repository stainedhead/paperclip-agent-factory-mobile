package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.domain.validate

/** Who is looking at the card, for the rules that depend on it. */
internal data class InteractionViewer(
    val role: Role?,
    val userId: String?,
    val online: Boolean,
    val issueUrl: String?,
)

/** Draws an interaction as a card (PRD FR-063). Every rule comes from the domain's `validate`. */
internal object InteractionRenderer {
    fun render(
        interaction: Interaction,
        draft: InteractionDraft,
        viewer: InteractionViewer,
    ): InteractionUi {
        val readOnly = readOnlyReason(interaction, viewer)
        val primary = if (interaction is UnsupportedInteraction) null else draft.primaryAnswer(interaction)
        val submit = primary?.let { if (readOnly == null) interaction.validate(it) else null }
        val reject =
            if (hasReject(interaction) &&
                readOnly == null
            ) {
                interaction.validate(draft.rejectAnswer())
            } else {
                null
            }
        return InteractionUi(
            id = interaction.meta.id.value,
            kind = kindOf(interaction),
            status = interaction.meta.status.toUi(),
            title = interaction.meta.title,
            prompt = promptOf(interaction),
            detailsMarkdown = detailsOf(interaction),
            canAnswer = readOnly == null,
            readOnlyReason = readOnly,
            primary = primaryOf(interaction),
            hasReject = hasReject(interaction),
            rejectRequiresReason = rejectRequiresReason(interaction),
            rejectReason = draft.texts[SINGLE_GROUP].orEmpty(),
            rejectIssue = reject?.toKey(),
            submitIssue = submit?.toKey(),
            submitIssueTargetId = submit?.targetId(),
            tasks = tasksOf(interaction, draft),
            options = optionsOf(interaction, draft),
            questions = questionsOf(interaction, draft),
            items = itemsOf(interaction, draft),
            enabledVerdicts = (interaction as? RequestItemVerdicts)?.verdicts?.map { it.toUi() }.orEmpty(),
            openInWebUrl = viewer.issueUrl.takeIf { interaction is UnsupportedInteraction },
        )
    }

    private fun readOnlyReason(
        interaction: Interaction,
        viewer: InteractionViewer,
    ): InteractionReadOnlyUi? =
        when {
            interaction is UnsupportedInteraction -> {
                InteractionReadOnlyUi.Unsupported
            }

            !interaction.meta.status.isOpen -> {
                InteractionReadOnlyUi.NotPending
            }

            viewer.role == null || !Permissions.allows(viewer.role, Action.RespondToInteraction) -> {
                InteractionReadOnlyUi.ViewerRole
            }

            !viewer.online -> {
                InteractionReadOnlyUi.Offline
            }

            interaction.meta.acceptanceBlocked -> {
                InteractionReadOnlyUi.Blocked
            }

            !interaction.meta.audience.allowsUser(viewer.userId) -> {
                InteractionReadOnlyUi.NotForYou
            }

            else -> {
                null
            }
        }

    private fun hasReject(interaction: Interaction) =
        interaction is SuggestTasks || interaction is RequestConfirmation || interaction is RequestCheckboxConfirmation

    private fun rejectRequiresReason(interaction: Interaction) =
        (interaction as? RequestConfirmation)?.rejectRequiresReason == true ||
            (interaction as? RequestCheckboxConfirmation)?.rejectRequiresReason == true

    private fun primaryOf(interaction: Interaction) =
        when (interaction) {
            is AskUserQuestions -> InteractionPrimaryUi.Respond
            is RequestItemVerdicts -> InteractionPrimaryUi.SubmitVerdicts
            is UnsupportedInteraction -> null
            else -> InteractionPrimaryUi.Accept
        }
}

internal fun kindOf(interaction: Interaction) =
    when (interaction) {
        is SuggestTasks -> InteractionKindUi.SuggestTasks
        is AskUserQuestions -> InteractionKindUi.AskUserQuestions
        is RequestConfirmation -> InteractionKindUi.RequestConfirmation
        is RequestCheckboxConfirmation -> InteractionKindUi.RequestCheckboxConfirmation
        is RequestItemVerdicts -> InteractionKindUi.RequestItemVerdicts
        is UnsupportedInteraction -> InteractionKindUi.Unsupported
    }

private fun promptOf(interaction: Interaction) =
    when (interaction) {
        is RequestConfirmation -> interaction.prompt
        is RequestCheckboxConfirmation -> interaction.prompt
        is RequestItemVerdicts -> interaction.prompt
        else -> null
    }

private fun detailsOf(interaction: Interaction) =
    when (interaction) {
        is RequestConfirmation -> interaction.detailsMarkdown
        is RequestCheckboxConfirmation -> interaction.detailsMarkdown
        is RequestItemVerdicts -> interaction.detailsMarkdown
        else -> null
    }

private fun tasksOf(
    interaction: Interaction,
    draft: InteractionDraft,
): List<ChoiceUi> {
    if (interaction !is SuggestTasks) return emptyList()
    val chosen = draft.selectedIn(interaction, SINGLE_GROUP)
    return interaction.tasks.map { ChoiceUi(it.clientKey, it.title, it.description, it.clientKey in chosen) }
}

private fun optionsOf(
    interaction: Interaction,
    draft: InteractionDraft,
): List<ChoiceUi> {
    if (interaction !is RequestCheckboxConfirmation) return emptyList()
    val chosen = draft.selectedIn(interaction, SINGLE_GROUP)
    return interaction.options.map { ChoiceUi(it.id, it.label, it.description, it.id in chosen) }
}

private fun questionsOf(
    interaction: Interaction,
    draft: InteractionDraft,
): List<QuestionUi> {
    if (interaction !is AskUserQuestions) return emptyList()
    return interaction.questions.map { question ->
        val chosen = draft.selectedIn(interaction, question.id)
        QuestionUi(
            id = question.id,
            prompt = question.prompt,
            helpText = question.helpText,
            multi = question.multi,
            required = question.required,
            allowOther = question.allowOther,
            options = question.options.map { ChoiceUi(it.id, it.label, it.description, it.id in chosen) },
            otherText = draft.texts[question.id].orEmpty(),
        )
    }
}

private fun itemsOf(
    interaction: Interaction,
    draft: InteractionDraft,
): List<VerdictItemUi> {
    if (interaction !is RequestItemVerdicts) return emptyList()
    return interaction.items.map {
        VerdictItemUi(it.id, it.label, it.description, draft.verdicts[it.id]?.toUi(), draft.texts[it.id].orEmpty())
    }
}
