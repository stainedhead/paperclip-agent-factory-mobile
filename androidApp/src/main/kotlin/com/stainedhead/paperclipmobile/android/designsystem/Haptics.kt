package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The two feelings the app uses (AND-070): an action went through, or it did not. Nothing else vibrates. */
interface Haptics {
    fun confirm()

    fun reject()
}

private class ComposeHaptics(
    private val feedback: HapticFeedback,
) : Haptics {
    override fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    override fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

/** Tests provide a recording [Haptics]; the app leaves it null and gets the platform's feedback. */
val LocalHaptics = compositionLocalOf<Haptics?> { null }

@Composable
fun rememberHaptics(): Haptics {
    val provided = LocalHaptics.current
    val feedback = LocalHapticFeedback.current
    return provided ?: remember(feedback) { ComposeHaptics(feedback) }
}

/** Reject feedback whenever a new failure [key] appears (the banner that says what went wrong). */
@Composable
fun RejectHapticOnChange(key: Any?) {
    val haptics = rememberHaptics()
    LaunchedEffect(key) { if (key != null) haptics.reject() }
}

/**
 * Confirm feedback when [done] turns true after the screen was first shown. A screen opened on something already
 * done (a resolved approval seen from the list) does not vibrate.
 */
@Composable
fun ConfirmHapticOnRise(done: Boolean) {
    val haptics = rememberHaptics()
    var before by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !before) haptics.confirm()
        before = done
    }
}

/** Confirm feedback each time [count] goes up after the screen was first shown (a comment sent). */
@Composable
fun ConfirmHapticOnIncrease(count: Int) {
    val haptics = rememberHaptics()
    var before by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > before) haptics.confirm()
        before = count
    }
}
