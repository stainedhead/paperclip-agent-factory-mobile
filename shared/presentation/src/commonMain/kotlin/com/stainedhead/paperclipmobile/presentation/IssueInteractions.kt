package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import kotlinx.coroutines.flow.update

/** The forms of the Issue thread: drafts the person edits, and the one answer each button sends (PRD FR-063). */
internal class IssueInteractions(
    private val session: IssueSession,
) {
    fun handle(intent: IssueIntent) {
        when (intent) {
            is IssueIntent.InteractionPrimaryTapped -> {
                answer(intent.interactionId) { draft, card -> draft.primaryAnswer(card) }
            }

            is IssueIntent.InteractionRejectTapped -> {
                answer(intent.interactionId) { draft, card -> draft.rejectAnswer().takeIf { card.canReject() } }
            }

            is IssueIntent.InteractionSkipTapped -> {
                answer(intent.interactionId) { _, _ -> InteractionAnswer.Skip(null) }
            }

            is IssueIntent.InteractionChoiceToggled -> {
                edit(intent.interactionId) { draft, card -> draft.toggled(card, intent.groupId, intent.optionId) }
            }

            is IssueIntent.InteractionTextChanged -> {
                edit(intent.interactionId) { draft, _ -> draft.withText(intent.targetId, intent.text) }
            }

            is IssueIntent.InteractionVerdictChosen -> {
                edit(intent.interactionId) { draft, _ -> draft.withVerdict(intent.itemId, intent.verdict.toDomain()) }
            }

            else -> {
                // Another group of intents: handled elsewhere.
            }
        }
    }

    private fun find(id: String): Interaction? =
        session.model.value.interactions
            .firstOrNull { it.meta.id.value == id }

    private fun edit(
        id: String,
        change: (InteractionDraft, Interaction) -> InteractionDraft,
    ) {
        val card = find(id) ?: return
        session.model.update { model ->
            val draft = model.drafts[id] ?: InteractionDraft()
            model.copy(drafts = model.drafts + (id to change(draft, card)), feedback = null)
        }
    }

    private fun answer(
        id: String,
        build: (InteractionDraft, Interaction) -> InteractionAnswer?,
    ) {
        val card = find(id)
        if (card == null || card is UnsupportedInteraction || !session.may(Action.RespondToInteraction)) return
        val answer = build(session.model.value.drafts[id] ?: InteractionDraft(), card) ?: return
        session.change(
            { mutate(session.context) { session.useCases.interactions.respond(it, session.id, card, answer) } },
            { model, updated ->
                model.copy(
                    interactions = model.interactions.map { if (it.meta.id == updated.meta.id) updated else it },
                    drafts = model.drafts - id,
                )
            },
        )
    }
}

private fun Interaction.canReject() =
    this is SuggestTasks || this is RequestConfirmation || this is RequestCheckboxConfirmation
