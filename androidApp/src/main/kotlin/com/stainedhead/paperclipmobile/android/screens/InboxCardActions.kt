package com.stainedhead.paperclipmobile.android.screens

/** What a card can do; each is a plain forward of an intent or a navigation. */
class InboxCardActions(
    val onOpen: () -> Unit,
    val onApprove: () -> Unit,
    val onReject: () -> Unit,
    val onKeepPaused: () -> Unit,
    val onSnooze: (() -> Unit)?,
    val onDismiss: (() -> Unit)?,
)
