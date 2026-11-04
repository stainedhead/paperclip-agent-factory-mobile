package com.stainedhead.paperclipmobile.android.designsystem

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.MainActivity
import com.stainedhead.paperclipmobile.android.R
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val READABLE = 4.5
private const val RGB = 0xFFFFFF

private class RecordingHaptics : Haptics {
    val calls = mutableListOf<String>()

    override fun confirm() {
        calls += "confirm"
    }

    override fun reject() {
        calls += "reject"
    }
}

/** AND-070 polish: reduced motion, haptics on confirm and fail, dynamic colour for neutrals only, splash and icon. */
@RunWith(AndroidJUnit4::class)
class PolishTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun onlyAnAnimatorScaleOfZeroReducesMotion() {
        isReducedMotion(0f) shouldBe true
        isReducedMotion(0.5f) shouldBe false
        isReducedMotion(1f) shouldBe false
    }

    @Test
    fun reducedMotionHasNoTransitionBetweenScreensAndOtherwiseTheFade() {
        enterTransition(true) shouldBe EnterTransition.None
        exitTransition(true) shouldBe ExitTransition.None
        enterTransition(false) shouldNotBe EnterTransition.None
        exitTransition(false) shouldNotBe ExitTransition.None
    }

    @Test
    fun theSystemAnimationSettingReachesTheComposition() {
        val resolver = context.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var reduced: Boolean? = null
        rule.setContent { reduced = rememberReducedMotion() }
        rule.waitForIdle()
        reduced shouldBe true

        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.waitForIdle()
        rule.runOnIdle { reduced shouldBe false }
    }

    @Test
    fun aFailureBannerRejectsOnceAndAConfirmedActionConfirmsOnlyWhenItHappensWhileShown() {
        val haptics = RecordingHaptics()
        var resolved by mutableStateOf(true)
        var sent by mutableIntStateOf(2)
        var failure by mutableStateOf<String?>(null)
        rule.setContent {
            CompositionLocalProvider(LocalHaptics provides haptics) {
                ConfirmHapticOnRise(resolved)
                ConfirmHapticOnIncrease(sent)
                RejectHapticOnChange(failure)
            }
        }
        rule.waitForIdle()
        haptics.calls shouldBe emptyList()

        resolved = false
        rule.waitForIdle()
        resolved = true
        sent = 3
        failure = "the call failed"
        rule.waitForIdle()

        haptics.calls.sorted() shouldBe listOf("confirm", "confirm", "reject")
    }

    @Test
    fun dynamicNeutralsKeepEveryBrandAndSeverityColourAndStayReadable() {
        for ((dark, scheme) in listOf(false to lightColorScheme(), true to darkColorScheme())) {
            val brand = AppColors(dark)
            val dynamic = AppColors(dark, Neutrals(scheme, dark))

            dynamic.accent shouldBe brand.accent
            dynamic.destructive shouldBe brand.destructive
            dynamic.warn shouldBe brand.warn
            dynamic.success shouldBe brand.success
            dynamic.destructiveSoft shouldBe brand.destructiveSoft
            dynamic.warnSoft shouldBe brand.warnSoft
            dynamic.successSoft shouldBe brand.successSoft
            dynamic.background shouldNotBe brand.background

            for (page in listOf(dynamic.surface, dynamic.background, dynamic.muted)) {
                Contrast.ratio(dynamic.textPrimary.toArgb() and RGB, page.toArgb() and RGB) shouldBeGreaterThanOrEqualTo
                    READABLE
                Contrast.ratio(
                    dynamic.textSecondary.toArgb() and RGB,
                    page.toArgb() and RGB,
                ) shouldBeGreaterThanOrEqualTo
                    READABLE
            }
        }
    }

    @Test
    fun theLauncherStartsOnTheSplashThemeAndTheIconHasEveryAdaptiveLayer() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        info.theme shouldBe R.style.Theme_Paperclip_Starting

        val icon = File("src/main/res/mipmap-anydpi/ic_launcher.xml").readText()
        listOf("<background", "<foreground", "<monochrome").forEach { icon.contains(it) shouldBe true }
        val themes = File("src/main/res/values/themes.xml").readText()
        listOf("windowSplashScreenBackground", "windowSplashScreenAnimatedIcon", "postSplashScreenTheme")
            .forEach { themes.contains(it) shouldBe true }
        File("src/main/res/values-night/colors.xml").readText().contains("splash_icon") shouldBe true
    }
}
