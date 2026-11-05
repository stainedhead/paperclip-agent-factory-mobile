package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** R-028: the list shows the order the store accepted, never one it refused. */
@RunWith(AndroidJUnit4::class)
class ReorderableColumnTest {
    @get:Rule
    val rule = createComposeRule()

    private val rows = listOf("a", "b", "c")

    /** The ids in the order the rows are laid out, top to bottom. */
    private fun shownOrder() =
        rows.sortedBy {
            rule
                .onNodeWithTag("row-$it")
                .fetchSemanticsNode()
                .positionInRoot.y
        }

    @Test
    fun aRejectedReorderPutsTheStoredOrderBack() {
        var rejected by mutableStateOf(false)
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ReorderableColumn(rows, { it }, {}, reorderRejected = rejected) { id, controls ->
                    Column(Modifier.testTag("row-$id")) {
                        Text(id)
                        Text("down", Modifier.testTag("down-$id").clickable { controls.moveDown?.invoke() })
                    }
                }
            }
        }
        rule.onNodeWithTag("down-a").performClick()
        shownOrder() shouldBe listOf("b", "a", "c")

        // The same rows come back from the store with the refusal: the local move is dropped.
        rejected = true
        rule.waitForIdle()

        shownOrder() shouldBe listOf("a", "b", "c")
    }

    @Test
    fun anAcceptedReorderKeepsTheNewOrder() {
        var items by mutableStateOf(rows)
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ReorderableColumn(items, { it }, { items = it }, reorderRejected = false) { id, controls ->
                    Column(Modifier.testTag("row-$id")) {
                        Text(id)
                        Text("down", Modifier.testTag("down-$id").clickable { controls.moveDown?.invoke() })
                    }
                }
            }
        }

        rule.onNodeWithTag("down-a").performClick()
        rule.waitForIdle()

        shownOrder() shouldBe listOf("b", "a", "c")
    }
}
