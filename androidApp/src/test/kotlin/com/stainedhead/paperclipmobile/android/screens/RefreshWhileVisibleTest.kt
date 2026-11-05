package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Invalidation
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

private const val QUIET_MILLIS = 2_500L

private class CountingTarget(
    seconds: Int,
) : RefreshTarget {
    val refreshes = AtomicInteger()
    override val refreshIntervalSeconds: Flow<Int> = flowOf(seconds)

    override fun refreshNow() {
        refreshes.incrementAndGet()
    }

    override fun isAffectedBy(invalidation: Invalidation) = false
}

/**
 * The refresh session of a screen with a refresh target lives exactly as long as the screen is on screen: started when
 * it appears, stopped when it leaves (AND-050 to AND-052, PRD FR-090). The interval is the target's own.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class RefreshWhileVisibleTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    @Composable
    private fun Screen(
        target: RefreshTarget,
        visible: Boolean,
    ) {
        if (visible) RefreshWhileVisible(app.session, target)
    }

    @Test
    fun theTargetIsRefreshedAtItsIntervalWhileVisibleAndNeverAfterTheScreenLeaves() {
        val target = CountingTarget(seconds = 1)
        var visible by mutableStateOf(true)
        rule.setContent { Screen(target, visible) }

        rule.waitUntil(WAIT_MILLIS) { target.refreshes.get() >= 2 }
        visible = false
        rule.waitForIdle()
        val atLeave = target.refreshes.get()
        runBlocking { delay(QUIET_MILLIS) }

        target.refreshes.get() shouldBe atLeave
    }

    @Test
    fun aTargetWithNoIntervalIsNeverRefreshedByTimer() {
        val target = CountingTarget(seconds = 0)
        rule.setContent { Screen(target, visible = true) }

        runBlocking { delay(QUIET_MILLIS) }

        target.refreshes.get() shouldBe 0
    }
}
