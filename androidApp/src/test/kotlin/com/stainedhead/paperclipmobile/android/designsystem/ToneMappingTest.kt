package com.stainedhead.paperclipmobile.android.designsystem

import com.stainedhead.paperclipmobile.presentation.ToneUi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The app only chooses the colour of each family the shared presentation names (R-019). */
class ToneMappingTest {
    @Test
    fun `every shared tone family has its own app tone`() {
        ToneUi.entries.map { it.statusTone() } shouldBe
            listOf(StatusTone.Neutral, StatusTone.Success, StatusTone.Warn, StatusTone.Critical)
        ToneUi.entries
            .map { it.statusTone() }
            .toSet()
            .size shouldBe ToneUi.entries.size
    }
}
