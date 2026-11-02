package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionItem
import com.stainedhead.paperclipmobile.domain.InteractionMeta
import com.stainedhead.paperclipmobile.domain.InteractionOption
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.Verdict
import kotlinx.serialization.json.JsonObject

// Payloads of the confirmation kinds (pinned `shared/dist/types/issue.d.ts:1105-1330`): see InteractionMapper.

internal fun JsonObject.options(name: String): List<InteractionOption>? {
    val read = objects(name).map { optionOf(it) }
    return if (read.any { it == null }) null else read.filterNotNull()
}

private fun optionOf(option: JsonObject): InteractionOption? {
    val id = option.text("id") ?: return null
    val label = option.text("label") ?: return null
    return InteractionOption(id, label, option.text("description"))
}

internal fun confirmation(
    meta: InteractionMeta,
    payload: JsonObject,
): Interaction? {
    val privileged = listOf("toolAction", "secretProposal", "connectionAuthorization").any { it in payload }
    val prompt = payload.text("prompt")
    return if (privileged || prompt == null) {
        null
    } else {
        RequestConfirmation(
            meta = meta,
            prompt = prompt,
            acceptLabel = payload.text("acceptLabel"),
            rejectLabel = payload.text("rejectLabel"),
            rejectRequiresReason = payload.flag("rejectRequiresReason") == true,
            detailsMarkdown = payload.text("detailsMarkdown"),
        )
    }
}

internal fun checkbox(
    meta: InteractionMeta,
    payload: JsonObject,
): Interaction? {
    val prompt = payload.text("prompt") ?: return null
    val options = payload.options("options")?.takeIf { it.isNotEmpty() } ?: return null
    return RequestCheckboxConfirmation(
        meta = meta,
        prompt = prompt,
        options = options,
        defaultSelected = payload.texts("defaultSelectedOptionIds").toSet(),
        minSelected = payload.whole("minSelected") ?: 0,
        maxSelected = payload.whole("maxSelected"),
        rejectRequiresReason = payload.flag("rejectRequiresReason") == true,
        detailsMarkdown = payload.text("detailsMarkdown"),
    )
}

internal fun verdicts(
    meta: InteractionMeta,
    payload: JsonObject,
): Interaction? {
    val prompt = payload.text("prompt") ?: return null
    val items = payload.options("items")?.takeIf { it.isNotEmpty() } ?: return null
    return RequestItemVerdicts(
        meta = meta,
        prompt = prompt,
        items = items.map { InteractionItem(it.id, it.label, it.description) },
        verdicts = payload.verdictSet("verdicts") ?: setOf(Verdict.Approve, Verdict.Reject),
        requireReasonOn = payload.verdictSet("requireReasonOn") ?: setOf(Verdict.Reject),
        detailsMarkdown = payload.text("detailsMarkdown"),
    )
}

/** The verdicts a field lists (unknown names are skipped), or null when the field is absent. */
private fun JsonObject.verdictSet(name: String): Set<Verdict>? =
    if (name in
        this
    ) {
        texts(name).mapNotNull { raw -> Verdict.entries.firstOrNull { it.serverName == raw } }.toSet()
    } else {
        null
    }
