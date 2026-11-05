package com.stainedhead.paperclipmobile.android.designsystem

import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.stainedhead.paperclipmobile.android.R
import kotlinx.coroutines.delay

enum class AgeUnit {
    Seconds,
    Minutes,
    Hours,
    Days,
}

data class Age(
    val unit: AgeUnit,
    val value: Int,
)

/** The mocks' compact ages ("12s", "8m", "1h", "3d"): each age is cut down to its largest whole unit. */
object RelativeAge {
    private const val MINUTE = 60L
    private const val HOUR = 3_600L
    private const val DAY = 86_400L

    fun of(seconds: Long): Age {
        val value = maxOf(0L, seconds)
        return when {
            value < MINUTE -> Age(AgeUnit.Seconds, value.toInt())
            value < HOUR -> Age(AgeUnit.Minutes, (value / MINUTE).toInt())
            value < DAY -> Age(AgeUnit.Hours, (value / HOUR).toInt())
            else -> Age(AgeUnit.Days, (value / DAY).toInt())
        }
    }
}

@Composable
internal fun Age.compactText(): String =
    when (unit) {
        AgeUnit.Seconds -> stringResource(R.string.age_seconds, value)
        AgeUnit.Minutes -> stringResource(R.string.age_minutes, value)
        AgeUnit.Hours -> stringResource(R.string.age_hours, value)
        AgeUnit.Days -> stringResource(R.string.age_days, value)
    }

@Composable
internal fun Age.spokenText(): String =
    when (unit) {
        AgeUnit.Seconds -> pluralStringResource(R.plurals.age_spoken_seconds, value, value)
        AgeUnit.Minutes -> pluralStringResource(R.plurals.age_spoken_minutes, value, value)
        AgeUnit.Hours -> pluralStringResource(R.plurals.age_spoken_hours, value, value)
        AgeUnit.Days -> pluralStringResource(R.plurals.age_spoken_days, value, value)
    }

/** "Now" for relative labels. Null means the wall clock; the demo graph pins its own frozen clock, tests pin theirs. */
val LocalReferenceMillis = staticCompositionLocalOf<Long?> { null }

private const val AGE_TICK_MILLIS = 15_000L
private const val MILLIS_PER_SECOND = 1_000L

/** What "now" is: a pinned clock as it is, otherwise the wall clock on a coarse tick, with no timer per label. */
@Composable
fun rememberNowMillis(tickMillis: Long = AGE_TICK_MILLIS): State<Long> {
    val pinned = LocalReferenceMillis.current
    return produceState(initialValue = pinned ?: System.currentTimeMillis(), pinned, tickMillis) {
        if (pinned != null) {
            value = pinned
        } else {
            while (true) {
                value = System.currentTimeMillis()
                delay(tickMillis)
            }
        }
    }
}

/**
 * "Updated 12s ago": always shows the server's timestamp as an age, so stale data never looks current, also when the
 * phone is offline or the screen is final and no longer refreshes.
 */
@Composable
fun UpdatedLabel(
    epochMillis: Long?,
    modifier: Modifier = Modifier,
) {
    if (epochMillis == null) return
    val now by rememberNowMillis()
    val age = RelativeAge.of((now - epochMillis) / MILLIS_PER_SECOND)
    val spoken = stringResource(R.string.common_updated_ago, age.spokenText())
    Text(
        text = stringResource(R.string.common_updated_ago, age.compactText()),
        modifier = modifier.fillMaxWidth().testTag("updated-label").semantics { contentDescription = spoken },
        style = AppType.secondary,
        color = AppTheme.colors.textSecondary,
    )
}

/** The one place the app decides whether an explicit animation runs: never when the person turned animations off. */
object Motion {
    private const val STANDARD_MILLIS = 200

    fun durationMillis(reduceMotion: Boolean): Int = if (reduceMotion) 0 else STANDARD_MILLIS
}

/** True when the system animator scale is zero (Settings, Accessibility, Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/** The screen's one clock (R-008): labels below it follow a single tick instead of each running a timer. */
@Composable
fun ProvideNow(content: @Composable () -> Unit) = content()
