package com.stainedhead.paperclipmobile.domain

data class QuestionAnswer(
    val questionId: String,
    val optionIds: List<String>,
    val otherText: String? = null,
)

data class ItemVerdict(
    val itemId: String,
    val verdict: Verdict,
    val reason: String? = null,
)

/** What the person chose; each maps to exactly one server endpoint (research W7). */
sealed interface InteractionAnswer {
    /** Accept a confirmation. */
    data object Accept : InteractionAnswer

    data class AcceptTasks(
        val clientKeys: Set<String>,
    ) : InteractionAnswer

    data class AcceptOptions(
        val optionIds: Set<String>,
    ) : InteractionAnswer

    data class Reject(
        val reason: String?,
    ) : InteractionAnswer

    data class Respond(
        val answers: List<QuestionAnswer>,
    ) : InteractionAnswer

    data class SubmitVerdicts(
        val verdicts: List<ItemVerdict>,
    ) : InteractionAnswer

    data class Skip(
        val reason: String?,
    ) : InteractionAnswer
}

/** Why an answer was refused on the device, before any request (ADR-023). */
sealed interface AnswerIssue {
    data object Unsupported : AnswerIssue

    data object NotPending : AnswerIssue

    data object Blocked : AnswerIssue

    data object WrongKind : AnswerIssue

    data object NothingSelected : AnswerIssue

    data class UnknownChoice(
        val id: String,
    ) : AnswerIssue

    data class TooFewSelected(
        val min: Int,
    ) : AnswerIssue

    data class TooManySelected(
        val max: Int,
    ) : AnswerIssue

    data class QuestionRequired(
        val questionId: String,
    ) : AnswerIssue

    data class VerdictNotEnabled(
        val itemId: String,
    ) : AnswerIssue

    /** [id] is the item the reason is for, or null for the single reason of a confirmation. */
    data class ReasonRequired(
        val id: String?,
    ) : AnswerIssue
}

/** The first problem with [answer] for this interaction, or null when it can be sent. */
fun Interaction.validate(answer: InteractionAnswer): AnswerIssue? =
    when {
        this is UnsupportedInteraction -> AnswerIssue.Unsupported
        !meta.status.isOpen -> AnswerIssue.NotPending
        answer is InteractionAnswer.Skip -> null
        answer.isAcceptance() && meta.acceptanceBlocked -> AnswerIssue.Blocked
        else -> validateForKind(answer)
    }

private fun InteractionAnswer.isAcceptance() =
    this is InteractionAnswer.Accept || this is InteractionAnswer.AcceptTasks || this is InteractionAnswer.AcceptOptions

private fun Interaction.validateForKind(answer: InteractionAnswer): AnswerIssue? =
    when (this) {
        is SuggestTasks -> {
            validateTasks(answer)
        }

        is RequestConfirmation -> {
            validateConfirmation(answer)
        }

        is RequestCheckboxConfirmation -> {
            validateCheckbox(answer)
        }

        is AskUserQuestions -> {
            if (answer is InteractionAnswer.Respond) validateAnswers(answer) else AnswerIssue.WrongKind
        }

        is RequestItemVerdicts -> {
            if (answer is InteractionAnswer.SubmitVerdicts) validateVerdicts(answer) else AnswerIssue.WrongKind
        }

        is UnsupportedInteraction -> {
            AnswerIssue.Unsupported
        }
    }

private fun SuggestTasks.validateTasks(answer: InteractionAnswer): AnswerIssue? =
    when (answer) {
        is InteractionAnswer.AcceptTasks -> validateTasks(answer)
        is InteractionAnswer.Reject -> null
        else -> AnswerIssue.WrongKind
    }

private fun RequestConfirmation.validateConfirmation(answer: InteractionAnswer): AnswerIssue? =
    when (answer) {
        InteractionAnswer.Accept -> null
        is InteractionAnswer.Reject -> reasonIssue(rejectRequiresReason, answer)
        else -> AnswerIssue.WrongKind
    }

private fun RequestCheckboxConfirmation.validateCheckbox(answer: InteractionAnswer): AnswerIssue? =
    when (answer) {
        is InteractionAnswer.AcceptOptions -> validateOptions(answer)
        is InteractionAnswer.Reject -> reasonIssue(rejectRequiresReason, answer)
        else -> AnswerIssue.WrongKind
    }

private fun reasonIssue(
    required: Boolean,
    answer: InteractionAnswer.Reject,
): AnswerIssue? = if (required && answer.reason.isNullOrBlank()) AnswerIssue.ReasonRequired(null) else null

private fun SuggestTasks.validateTasks(answer: InteractionAnswer.AcceptTasks): AnswerIssue? {
    val known = tasks.map { it.clientKey }.toSet()
    return when {
        answer.clientKeys.isEmpty() -> AnswerIssue.NothingSelected
        else -> answer.clientKeys.firstOrNull { it !in known }?.let { AnswerIssue.UnknownChoice(it) }
    }
}

private fun RequestCheckboxConfirmation.validateOptions(answer: InteractionAnswer.AcceptOptions): AnswerIssue? {
    val known = options.map { it.id }.toSet()
    val max = maxSelected
    return answer.optionIds.firstOrNull { it !in known }?.let { AnswerIssue.UnknownChoice(it) }
        ?: when {
            answer.optionIds.size < minSelected -> AnswerIssue.TooFewSelected(minSelected)
            max != null && answer.optionIds.size > max -> AnswerIssue.TooManySelected(max)
            else -> null
        }
}

private fun AskUserQuestions.validateAnswers(answer: InteractionAnswer.Respond): AnswerIssue? {
    val byId = questions.associateBy { it.id }
    answer.answers.forEach { given ->
        val question = byId[given.questionId] ?: return AnswerIssue.UnknownChoice(given.questionId)
        val known = question.options.map { it.id }.toSet()
        if (!question.multi && given.optionIds.size > 1) return AnswerIssue.TooManySelected(1)
        given.optionIds.firstOrNull { it !in known }?.let { return AnswerIssue.UnknownChoice(it) }
    }
    val answered =
        answer.answers
            .filter { given ->
                given.optionIds.isNotEmpty() ||
                    (byId[given.questionId]?.allowOther == true && !given.otherText.isNullOrBlank())
            }.map { it.questionId }
            .toSet()
    return questions.firstOrNull { it.required && it.id !in answered }?.let { AnswerIssue.QuestionRequired(it.id) }
}

private fun RequestItemVerdicts.validateVerdicts(answer: InteractionAnswer.SubmitVerdicts): AnswerIssue? {
    if (answer.verdicts.isEmpty()) return AnswerIssue.NothingSelected
    val known = items.map { it.id }.toSet()
    answer.verdicts.forEach { given ->
        when {
            given.itemId !in known -> return AnswerIssue.UnknownChoice(given.itemId)

            given.verdict !in verdicts -> return AnswerIssue.VerdictNotEnabled(given.itemId)

            given.verdict in requireReasonOn && given.reason.isNullOrBlank() -> return AnswerIssue.ReasonRequired(
                given.itemId,
            )
        }
    }
    return null
}

private fun QuestionAnswer.isAnswered(question: Question?) =
    optionIds.isNotEmpty() || (question?.allowOther == true && !otherText.isNullOrBlank())
