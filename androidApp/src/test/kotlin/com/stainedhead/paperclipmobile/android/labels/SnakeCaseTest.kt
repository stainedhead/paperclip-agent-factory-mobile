package com.stainedhead.paperclipmobile.android.labels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SnakeCaseTest {
    @Test
    fun `entry names become resource name parts`() {
        "Unreachable".toSnakeCase() shouldBe "unreachable"
        "NeedsRePairing".toSnakeCase() shouldBe "needs_re_pairing"
        "LiveGraphUnavailable".toSnakeCase() shouldBe "live_graph_unavailable"
        "MustUseHttps".toSnakeCase() shouldBe "must_use_https"
    }
}
