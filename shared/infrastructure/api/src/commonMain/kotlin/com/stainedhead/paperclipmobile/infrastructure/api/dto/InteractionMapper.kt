package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.InteractionMeta
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.Question
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.SuggestedTask
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.json.JsonObject

// Shapes from the pinned release 2026.1023.0 (compiled dist): the hydrated row (`types/issue.d.ts:1332-1400`,
// `server/dist/services/issue-thread-interactions.js:322-380`) and one payload per kind (`issue.d.ts:985-1330`). The
// rows are read by hand: a payload this app cannot read must not hide the rest of the thread (ADR-065).

/**
 * The interaction in [this] row, or null when the row cannot be identified (no id, kind or creation time), which makes
 * the list Malformed. A kind this app does not answer (`connection_intent`, one from a newer server), a confirmation
 * that gates a tool call, a secret or a connection, and a supported kind whose payload cannot be read are all
 * [UnsupportedInteraction]: shown, never answered from the phone.
 */
internal fun JsonObject.toInteraction(): Interaction? {
    val meta = interactionMeta() ?: return null
    val kind = text("kind")?.let { InteractionKind.fromServer(it) } ?: return null
    val payload = obj("payload") ?: JsonObject(emptyMap())
    val mapped =
        when (kind) {
            InteractionKind.SuggestTasks -> suggestTasks(meta, payload)
            InteractionKind.AskUserQuestions -> askUserQuestions(meta, payload)
            InteractionKind.RequestConfirmation -> confirmation(meta, payload)
            InteractionKind.RequestCheckboxConfirmation -> checkbox(meta, payload)
            InteractionKind.RequestItemVerdicts -> verdicts(meta, payload)
            else -> null
        }
    return mapped ?: UnsupportedInteraction(meta, kind)
}

/** The company a row names, to check it against the scope. */
internal fun JsonObject.companyOfRow(): String? = text("companyId")

private fun JsonObject.interactionMeta(): InteractionMeta? {
    val id = text("id") ?: return null
    return InteractionMeta(
        id = InteractionId(id),
        status = InteractionStatus.fromServer(text("status").orEmpty()),
        title = text("title"),
        summary = text("summary"),
        createdAt = parseInstantOrNull(text("createdAt")) ?: return null,
        audience = resolverAudienceOf(this),
        acceptanceBlocked = text("acceptanceBlocker") != null,
        resolvedAt = parseInstantOrNull(text("resolvedAt")),
    )
}

private fun suggestTasks(
    meta: InteractionMeta,
    payload: JsonObject,
): Interaction? {
    val tasks = payload.objects("tasks").map { task -> suggestedTask(task) }
    return if (tasks.isEmpty() || tasks.any { it == null }) null else SuggestTasks(meta, tasks.filterNotNull())
}

private fun suggestedTask(task: JsonObject): SuggestedTask? {
    val key = task.text("clientKey") ?: return null
    val title = task.text("title") ?: return null
    return SuggestedTask(key, title, task.text("description"))
}

private fun askUserQuestions(
    meta: InteractionMeta,
    payload: JsonObject,
): Interaction? {
    val questions = payload.objects("questions").map { question(it) }
    if (questions.isEmpty() || questions.any { it == null }) return null
    return AskUserQuestions(meta, questions.filterNotNull(), payload.text("submitLabel"))
}

/** `required` is only true when it says so and `allowOther` only false when it says so (`services` line 1082). */
private fun question(raw: JsonObject): Question? {
    val id = raw.text("id") ?: return null
    val prompt = raw.text("prompt") ?: return null
    val mode = raw.text("selectionMode")
    val options = raw.options("options")?.takeIf { it.isNotEmpty() }
    if (options == null || (mode != "single" && mode != "multi")) return null
    return Question(
        id = id,
        prompt = prompt,
        multi = mode == "multi",
        required = raw.flag("required") == true,
        allowOther = raw.flag("allowOther") != false,
        options = options,
        helpText = raw.text("helpText"),
    )
}
