package com.stainedhead.paperclipmobile.android.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods the first launch and the Inbox scroll use, as the app's baseline profile
 * (androidApp/src/main/baseline-prof.txt). Run it against the `nonMinified` build, which keeps real class names;
 * scripts/generate-baseline-profile.sh does that and copies the result. The profile covers startup only
 * (`includeInStartupProfile`) plus the one scroll the PRD measures.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            launchDemo(GENERATED_INBOX_CARDS)
            flingInbox()
        }
}
