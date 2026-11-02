package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One answer as the server wants it: the endpoint under `interactions/{id}/` and the JSON body (ADR-065). */
internal class AnswerRequest(
    val action: String,
    val body: String,
)

/** Which answer goes to which endpoint, and which answers fit which kind of interaction. */
internal object InteractionRequests {
    /** Skip fits every kind; the others fit exactly the kind whose endpoint they use. */
    fun fits(
        interaction: Interaction,
        answer: InteractionAnswer,
    ): Boolean =
        answer is InteractionAnswer.Skip ||
            when (interaction) {
                is SuggestTasks -> {
                    answer is InteractionAnswer.AcceptTasks || answer is InteractionAnswer.Reject
                }

                is AskUserQuestions -> {
                    answer is InteractionAnswer.Respond
                }

                is RequestConfirmation -> {
                    answer == InteractionAnswer.Accept || answer is InteractionAnswer.Reject
                }

                is RequestCheckboxConfirmation -> {
                    answer is InteractionAnswer.AcceptOptions ||
                        answer is InteractionAnswer.Reject
                }

                is RequestItemVerdicts -> {
                    answer is InteractionAnswer.SubmitVerdicts
                }

                else -> {
                    false
                }
            }

    fun of(answer: InteractionAnswer): AnswerRequest =
        when (answer) {
            InteractionAnswer.Accept -> {
                AnswerRequest("accept", "{}")
            }

            is InteractionAnswer.AcceptTasks -> {
                AnswerRequest(
                    "accept",
                    strings("selectedClientKeys", answer.clientKeys),
                )
            }

            is InteractionAnswer.AcceptOptions -> {
                AnswerRequest(
                    "accept",
                    strings("selectedOptionIds", answer.optionIds),
                )
            }

            is InteractionAnswer.Reject -> {
                AnswerRequest("reject", reason(answer.reason))
            }

            is InteractionAnswer.Respond -> {
                AnswerRequest("respond", respond(answer))
            }

            is InteractionAnswer.SubmitVerdicts -> {
                AnswerRequest("verdicts", verdicts(answer))
            }

            is InteractionAnswer.Skip -> {
                AnswerRequest("skip", reason(answer.reason))
            }
        }

    private fun strings(
        name: String,
        values: Set<String>,
    ): String = buildJsonObject { put(name, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }) }.toString()

    private fun reason(text: String?): String =
        buildJsonObject {
            text?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
        }.toString()

    private fun respond(answer: InteractionAnswer.Respond): String =
        buildJsonObject {
            put(
                "answers",
                buildJsonArray {
                    answer.answers.forEach { given ->
                        add(
                            buildJsonObject {
                                put("questionId", given.questionId)
                                put("optionIds", buildJsonArray { given.optionIds.forEach { add(JsonPrimitive(it)) } })
                                given.otherText?.takeIf { it.isNotBlank() }?.let { put("otherText", it) }
                            },
                        )
                    }
                },
            )
        }.toString()

    private fun verdicts(answer: InteractionAnswer.SubmitVerdicts): String =
        buildJsonObject {
            put(
                "verdicts",
                buildJsonArray {
                    answer.verdicts.forEach { given ->
                        add(
                            buildJsonObject {
                                put("id", given.itemId)
                                put("verdict", given.verdict.serverName)
                                given.reason?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
                            },
                        )
                    }
                },
            )
        }.toString()
}
