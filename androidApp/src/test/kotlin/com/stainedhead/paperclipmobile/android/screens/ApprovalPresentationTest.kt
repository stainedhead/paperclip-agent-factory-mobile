package com.stainedhead.paperclipmobile.android.screens

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** AND-041: the lookups of the Approval screen; which actions exist and when is the shared state's. */
class ApprovalPresentationTest {
    @Test
    fun `a common role reads as its acronym and any other as plain words`() {
        roleName("qa") shouldBe "QA"
        roleName("CTO") shouldBe "CTO"
        roleName("product_designer") shouldBe "Product designer"
        roleName("") shouldBe null
        roleName(null) shouldBe null
    }

    @Test
    fun `risks read as a bulleted list and none as nothing`() {
        bullets(listOf("Downtime", "Cost")) shouldBe "• Downtime\n• Cost"
        bullets(emptyList()) shouldBe null
    }
}
