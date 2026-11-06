package com.stainedhead.paperclipmobile.android.designsystem

import android.content.res.Resources
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
internal fun Age.spokenText(): String = spokenText(LocalResources.current)

/** The age in words, for a label that is built late (a semantics lambda), where a composable cannot be called. */
internal fun Age.spokenText(resources: Resources): String =
    when (unit) {
        AgeUnit.Seconds -> resources.getQuantityString(R.plurals.age_spoken_seconds, value, value)
        AgeUnit.Minutes -> resources.getQuantityString(R.plurals.age_spoken_minutes, value, value)
        AgeUnit.Hours -> resources.getQuantityString(R.plurals.age_spoken_hours, value, value)
        AgeUnit.Days -> resources.getQuantityString(R.plurals.age_spoken_days, value, value)
    }

/** "Now" for relative labels. Null means the wall clock; the demo graph pins its own frozen clock, tests pin theirs. */
val LocalReferenceMillis = staticCompositionLocalOf<Long?> { null }

private const val AGE_TICK_MILLIS = 15_000L
private const val MILLIS_PER_SECOND = 1_000L

/** The one clock a screen shares ([ProvideNow]); null where nothing provides it, and a label then ticks by itself. */
private val LocalNow = staticCompositionLocalOf<State<Long>?> { null }

/**
 * The screen's one clock (R-008): every label below it follows a single coarse tick instead of each running a timer,
 * so a list of 200 cards costs one coroutine, not 200. The clock is a state that is read late, by the label that
 * shows it: a tick moves the age labels and recomposes nothing else.
 */
@Composable
fun ProvideNow(content: @Composable () -> Unit) {
    val now = tickingNow(AGE_TICK_MILLIS)
    CompositionLocalProvider(LocalNow provides now, content = content)
}

/**
 * What "now" is: a pinned clock as it is, otherwise the wall clock on a coarse tick. The screen's shared clock when
 * [ProvideNow] gives one (and the tick is the standard one), else a clock of its own.
 */
@Composable
fun rememberNowMillis(tickMillis: Long = AGE_TICK_MILLIS): State<Long> {
    val shared = LocalNow.current
    return if (shared != null && tickMillis == AGE_TICK_MILLIS) shared else tickingNow(tickMillis)
}

@Composable
private fun tickingNow(tickMillis: Long): State<Long> {
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
 * How long ago something happened, read late: only the code that calls [age] follows the clock, so a card that holds
 * an [ElapsedSince] is not recomposed by a tick.
 */
@Stable
class ElapsedSince(
    private val now: State<Long>,
    private val sinceMillis: Long,
) {
    fun age(): Age = RelativeAge.of((now.value - sinceMillis) / MILLIS_PER_SECOND)
}

@Composable
fun rememberElapsedSince(sinceMillis: Long): ElapsedSince {
    val now = rememberNowMillis()
    return remember(now, sinceMillis) { ElapsedSince(now, sinceMillis) }
}

/** An age as a label of its own: it is the only thing a clock tick recomposes. */
@Composable
fun AgeText(
    age: @Composable () -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(age(), modifier, style = style, color = color)
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
