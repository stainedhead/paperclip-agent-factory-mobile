package com.stainedhead.paperclipmobile.domain

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
