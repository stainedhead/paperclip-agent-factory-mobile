package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.ItemVerdict
import com.stainedhead.paperclipmobile.domain.QuestionAnswer
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.domain.Verdict

/** The group id of suggested tasks and checkbox options, which have a single choice group. */
internal const val SINGLE_GROUP = ""

/**
 * What the person has typed or picked on one card but not sent. Choices that were never touched fall back to the
 * interaction's defaults: every suggested task, the checkbox defaults.
 */
internal data class InteractionDraft(
    val selected: Map<String, Set<String>> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
    val verdicts: Map<String, Verdict> = emptyMap(),
) {
    fun selectedIn(
        interaction: Interaction,
        group: String,
    ): Set<String> = selected[group] ?: defaults(interaction, group)

    fun toggled(
        interaction: Interaction,
        group: String,
        option: String,
    ): InteractionDraft {
        val current = selectedIn(interaction, group)
        val single =
            interaction is AskUserQuestions && interaction.questions.firstOrNull { it.id == group }?.multi == false
        val next =
            when {
                option in current -> current - option
                single -> setOf(option)
                else -> current + option
            }
        return copy(selected = selected + (group to next))
    }

    fun withText(
        target: String,
        text: String,
    ) = copy(texts = texts + (target to text))

    fun withVerdict(
        item: String,
        verdict: Verdict,
    ) = copy(verdicts = verdicts + (item to verdict))

    /** The answer the main button would send, or null when the kind has none. */
    fun primaryAnswer(interaction: Interaction): InteractionAnswer? =
        when (interaction) {
            is SuggestTasks -> InteractionAnswer.AcceptTasks(selectedIn(interaction, SINGLE_GROUP))
            is RequestConfirmation -> InteractionAnswer.Accept
            is RequestCheckboxConfirmation -> InteractionAnswer.AcceptOptions(selectedIn(interaction, SINGLE_GROUP))
            is AskUserQuestions -> respond(interaction)
            is RequestItemVerdicts -> verdictsFor(interaction)
            is UnsupportedInteraction -> null
        }

    fun rejectAnswer(): InteractionAnswer = InteractionAnswer.Reject(texts[SINGLE_GROUP]?.trim()?.ifEmpty { null })

    private fun respond(interaction: AskUserQuestions) =
        InteractionAnswer.Respond(
            interaction.questions
                .map { question ->
                    val chosen = selectedIn(interaction, question.id)
                    QuestionAnswer(
                        question.id,
                        question.options.map { it.id }.filter { it in chosen },
                        texts[question.id]?.trim()?.ifEmpty { null },
                    )
                }.filter { it.optionIds.isNotEmpty() || it.otherText != null },
        )

    private fun verdictsFor(interaction: RequestItemVerdicts) =
        InteractionAnswer.SubmitVerdicts(
            interaction.items.mapNotNull { item ->
                verdicts[item.id]?.let { ItemVerdict(item.id, it, texts[item.id]?.trim()?.ifEmpty { null }) }
            },
        )

    private fun defaults(
        interaction: Interaction,
        group: String,
    ): Set<String> =
        when {
            group != SINGLE_GROUP -> emptySet()
            interaction is SuggestTasks -> interaction.tasks.map { it.clientKey }.toSet()
            interaction is RequestCheckboxConfirmation -> interaction.defaultSelected
            else -> emptySet()
        }
}
