package com.stainedhead.paperclipmobile.android

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * A JUnit 5 test in the app's unit-test source set. Pure tests (labels, presentation helpers, route table) use JUnit 5
 * and run on the same Gradle test task as the JUnit 4 Robolectric tests, through the JUnit Vintage engine (ADR-121).
 */
class PureJvmSmokeTest {
    @Test
    fun `a JUnit 5 test runs in the app unit-test task`() {
        (1 + 1) shouldBe 2
    }
}
