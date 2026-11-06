package com.stainedhead.paperclipmobile.android.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** "Remove animations" (or the developer setting at 0x) sets the animator duration scale to 0 (AND-070). */
fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

private const val FADE_MILLIS = 700

/** The enter transition between screens: none when motion is reduced, otherwise the navigation library's own fade. */
fun enterTransition(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) EnterTransition.None else fadeIn(tween(FADE_MILLIS))

/** The exit transition between screens: none when motion is reduced, otherwise the navigation library's own fade. */
fun exitTransition(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) ExitTransition.None else fadeOut(tween(FADE_MILLIS))

/** Whether the system asks for no animation, kept current while the composition lives. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember { mutableStateOf(readReduced(resolver)) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = readReduced(resolver)
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readReduced(resolver: android.content.ContentResolver): Boolean =
    isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
