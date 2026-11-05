package com.stainedhead.paperclipmobile.android.shell

import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.auth.AuthHostRegistration
import com.stainedhead.paperclipmobile.umbrella.AndroidAuthHost
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** AND-013, AND-014: the activity that can show the system prompt is registered only while resumed; privacy flags. */
@RunWith(AndroidJUnit4::class)
class WindowPrivacyTest {
    private fun FragmentActivity.secure() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun theActivityIsTheAuthHostOnlyWhileResumed() =
        runBlocking<Unit> {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java)
            val activity = controller.get()
            activity.lifecycle.addObserver(AuthHostRegistration(activity))

            controller.create().start()
            AndroidAuthHost.current().shouldBeNull()
            controller.resume()
            (AndroidAuthHost.current() === activity) shouldBe true
            controller.pause()
            AndroidAuthHost.current().shouldBeNull()
        }

    @Test
    fun theScreenIsSecureOnlyWhileTheLockOrPairingShows() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val privacy = PrivacyShield(activity.window, sdkInt = LATEST) {}

        privacy.setScreenSecure(true)
        activity.secure() shouldBe true
        privacy.setScreenSecure(false)
        activity.secure() shouldBe false
    }

    @Test
    fun theRecentsThumbnailIsTurnedOffFromTiramisuOn() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val recents = mutableListOf<Boolean>()

        PrivacyShield(activity.window, sdkInt = LATEST) { recents += it }.onCreate()

        recents shouldBe listOf(false)
    }

    @Test
    fun beforeTiramisuTheWindowIsSecureWhilePausedSoTheThumbnailIsBlank() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val recents = mutableListOf<Boolean>()
        val privacy = PrivacyShield(activity.window, sdkInt = PRE_TIRAMISU) { recents += it }

        privacy.onCreate()
        privacy.onPause()
        activity.secure() shouldBe true
        privacy.onResume()
        activity.secure() shouldBe false

        recents shouldBe emptyList()
    }

    @Test
    fun resumingKeepsTheWindowSecureWhenTheLockStillShows() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val privacy = PrivacyShield(activity.window, sdkInt = PRE_TIRAMISU) {}

        privacy.setScreenSecure(true)
        privacy.onPause()
        privacy.onResume()

        activity.secure() shouldBe true
    }

    private companion object {
        const val LATEST = 36
        const val PRE_TIRAMISU = 32
    }
}
