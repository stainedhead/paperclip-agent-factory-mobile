package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class SecretTest {
    @Test
    fun `FR-010 toString never prints the value`() {
        val secret = Secret.of("pcp_board_TESTONLY1234")

        secret.toString() shouldBe "Secret(***)"
        "$secret" shouldNotContain "TESTONLY"
    }

    @Test
    fun `reveal returns the value and equality is by value`() {
        Secret.of("pcp_board_TESTONLY1").reveal() shouldBe "pcp_board_TESTONLY1"
        (Secret.of("pcp_board_TESTONLY1") == Secret.of("pcp_board_TESTONLY1")) shouldBe true
        (Secret.of("pcp_board_TESTONLY1") == Secret.of("pcp_board_TESTONLY2")) shouldBe false
        Secret.of("a").hashCode() shouldBe Secret.of("a").hashCode()
        (Secret.of("a").equals("a")) shouldBe false
    }

    @Test
    fun `wipe clears the in-memory copy`() {
        val secret = Secret.of("pcp_board_TESTONLY1")

        secret.wipe()

        secret.isWiped shouldBe true
        secret.reveal() shouldBe ""
    }

    @Test
    fun `startsWith compares the prefix in place`() {
        val secret = Secret.of("pcp_board_TESTONLY1")

        secret.startsWith("pcp_board_") shouldBe true
        secret.startsWith("pcp_board_TESTONLY1") shouldBe true
        secret.startsWith("") shouldBe true
        secret.startsWith("pcp_agent_") shouldBe false
        secret.startsWith("pcp_board_TESTONLY1x") shouldBe false
    }

    @Test
    fun `startsWith on a wiped secret matches only the empty prefix`() {
        val secret = Secret.of("pcp_board_TESTONLY1")
        secret.wipe()

        secret.startsWith("") shouldBe true
        secret.startsWith("pcp_board_") shouldBe false
        Secret.of("").startsWith("p") shouldBe false
    }

    @Test
    fun `copy is an independent value that survives wiping the original and vice versa`() {
        val original = Secret.of("pcp_board_TESTONLY1")

        val copy = original.copy()
        original.wipe()

        copy shouldBe Secret.of("pcp_board_TESTONLY1")
        copy.isWiped shouldBe false
        original.isWiped shouldBe true

        val again = copy.copy()
        copy.wipe()
        again shouldBe Secret.of("pcp_board_TESTONLY1")
    }

    @Test
    fun `copy still redacts toString`() {
        Secret.of("pcp_board_TESTONLY1").copy().toString() shouldBe "Secret(***)"
    }
}
