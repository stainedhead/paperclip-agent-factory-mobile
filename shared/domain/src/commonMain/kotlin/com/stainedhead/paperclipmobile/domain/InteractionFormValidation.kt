package com.stainedhead.paperclipmobile.domain

internal fun AskUserQuestions.validateAnswers(answer: InteractionAnswer.Respond): AnswerIssue? {
    val byId = questions.associateBy { it.id }
    answer.answers.forEach { given ->
        val question = byId[given.questionId] ?: return AnswerIssue.UnknownChoice(given.questionId)
        val known = question.options.map { it.id }.toSet()
        if (!question.multi && given.optionIds.size > 1) return AnswerIssue.TooManySelected(1)
        given.optionIds.firstOrNull { it !in known }?.let { return AnswerIssue.UnknownChoice(it) }
    }
    val answered =
        answer.answers
            .filter { given -> given.isAnswered(byId[given.questionId]) }
            .map { it.questionId }
            .toSet()
    return questions.firstOrNull { it.required && it.id !in answered }?.let { AnswerIssue.QuestionRequired(it.id) }
}

internal fun RequestItemVerdicts.validateVerdicts(answer: InteractionAnswer.SubmitVerdicts): AnswerIssue? {
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
