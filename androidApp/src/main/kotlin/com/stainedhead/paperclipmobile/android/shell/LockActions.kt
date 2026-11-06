package com.stainedhead.paperclipmobile.android.shell

import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue

/** What the lock screen needs: why the last unlock did not go ahead, and the one thing it can ask for. */
class LockActions(
    val issue: DeviceAuthIssue?,
    val onUnlock: () -> Unit,
)
