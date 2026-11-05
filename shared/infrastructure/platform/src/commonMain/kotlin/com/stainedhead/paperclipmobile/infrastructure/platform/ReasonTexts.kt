package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.AuthReason

/** The sentence the system prompt shows for each reason. The app passes localized text; this is only a default. */
class ReasonTexts(
    val unlockApp: String,
    val confirmDestructiveAction: String,
) {
    internal fun textFor(reason: AuthReason): String =
        when (reason) {
            AuthReason.UnlockApp -> unlockApp
            AuthReason.ConfirmDestructiveAction -> confirmDestructiveAction
        }

    companion object {
        val default: ReasonTexts = ReasonTexts("Unlock Paperclip", "Confirm this action")
    }
}
