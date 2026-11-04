package com.stainedhead.paperclipmobile.android.env

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** AND-011, AND-019: rotation keeps the view model and so the holder; finishing the activity closes the holder. */
@RunWith(AndroidJUnit4::class)
class HolderViewModelRotationTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var closes = 0

    private fun model(activity: ComponentActivity): ViewModel {
        val factory =
            viewModelFactory {
                initializer { HolderViewModel(ScreenHolder(MutableStateFlow(0), { _: Int -> }, { closes++ })) }
            }
        return ViewModelProvider.create(activity, factory)[HolderViewModel::class.java]
    }

    @Test
    fun rotationKeepsTheSameHolderAndDoesNotCloseIt() {
        var before: ViewModel? = null
        rule.activityRule.scenario.onActivity { before = model(it) }

        rule.activityRule.scenario.recreate()

        var after: ViewModel? = null
        rule.activityRule.scenario.onActivity { after = model(it) }
        (after === before) shouldBe true
        closes shouldBe 0
    }

    @Test
    fun finishingTheActivityClosesTheHolder() {
        rule.activityRule.scenario.onActivity { model(it) }

        rule.activityRule.scenario.close()

        closes shouldBe 1
    }
}
