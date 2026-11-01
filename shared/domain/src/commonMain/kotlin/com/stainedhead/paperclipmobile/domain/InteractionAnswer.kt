package com.stainedhead.paperclipmobile.domain

data class QuestionAnswer(
    val questionId: String,
    val optionIds: List<String>,
    val otherText: String? = null,
) {
    /** A chosen option, or free text where the question allows it. */
    fun isAnswered(question: Question?) =
        optionIds.isNotEmpty() || (question?.allowOther == true && !otherText.isNullOrBlank())
}

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
