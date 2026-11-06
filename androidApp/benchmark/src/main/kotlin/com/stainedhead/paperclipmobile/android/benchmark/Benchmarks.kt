package com.stainedhead.paperclipmobile.android.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The `benchmark` and `nonMinified` build types of the app (androidApp/build.gradle.kts). */
internal const val TARGET_PACKAGE = "com.stainedhead.paperclipmobile.benchmark"

/** How many generated cards the large Inbox holds besides the mock's nine: 209 cards, past the PRD's 200. */
internal const val GENERATED_INBOX_CARDS = 200

private const val CONTENT_WAIT_MILLIS = 10_000L
private const val FLING_COUNT = 5
private const val SETTLE_MILLIS = 500L
private const val GESTURE_MARGIN_DIVISOR = 5

/** Starts the app on the demo graph (the build offers the demo extras) and waits for the first frame. */
internal fun MacrobenchmarkScope.launchDemo(inboxCards: Int = 0) {
    pressHome()
    startActivityAndWait { intent: Intent ->
        intent.putExtra("demo", true)
        if (inboxCards > 0) intent.putExtra("demo_inbox_cards", inboxCards)
    }
}

/** The Inbox's filter chips (their content description): shown with the content, never while it is loading. */
private const val CONTENT_MARKER = "Filter by kind"

/**
 * Waits until the Inbox shows its content and returns the list: the tallest scrollable (the filter row also scrolls).
 * The list exists while the Inbox still loads, so the wait is for the filter chips, which only the content has: a
 * fling must never start on a screen that is still opening (R-008).
 */
internal fun MacrobenchmarkScope.awaitInboxList(): androidx.test.uiautomator.UiObject2 {
    check(device.wait(Until.hasObject(By.desc(CONTENT_MARKER).pkg(TARGET_PACKAGE)), CONTENT_WAIT_MILLIS)) {
        "the Inbox content did not appear"
    }
    return device.findObjects(By.scrollable(true).pkg(TARGET_PACKAGE)).maxBy { it.visibleBounds.height() }
}

/** Flings the Inbox list down and back up, [FLING_COUNT] times each way, letting each fling settle. */
internal fun MacrobenchmarkScope.flingInbox() {
    val list = awaitInboxList()
    // Keep clear of the system gesture area at the screen edges.
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
    repeat(FLING_COUNT) {
        list.fling(Direction.DOWN)
        device.waitForIdle(SETTLE_MILLIS)
    }
    repeat(FLING_COUNT) {
        list.fling(Direction.UP)
        device.waitForIdle(SETTLE_MILLIS)
    }
}
