package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** R-019: the decide-by words the shared decision parser reads are written in shared code, not built in a UI. */
class DecideByHintRulesTest {
    @Test
    fun `R-019 each decide by kind has the wire word the parser reads`() {
        DecideByUi(DecideByKindUi.Today, null).hintText shouldBe "today"
        DecideByUi(DecideByKindUi.ThisWeek, null).hintText shouldBe "this_week"
        DecideByUi(DecideByKindUi.Whenever, null).hintText shouldBe "whenever"
        DecideByUi(DecideByKindUi.OnDate, "2026-11-30").hintText shouldBe "2026-11-30"
        DecideByUi(DecideByKindUi.None, null).hintText shouldBe null
    }
}
