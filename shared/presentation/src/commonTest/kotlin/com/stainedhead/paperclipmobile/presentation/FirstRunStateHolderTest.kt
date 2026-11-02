package com.stainedhead.paperclipmobile.presentation

import app.cash.turbine.test
import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class FirstRunStateHolderTest {
    private fun holder(allowInsecureLocal: Boolean = false) =
        FirstRunStateHolder(ValidateInstanceAddress(allowInsecureLocal))

    @Test
    fun `starts empty with nothing to continue and no error shown`() {
        holder().state.value shouldBe FirstRunState()
    }

    @Test
    fun `a valid address enables continue and shows the normalised address`() {
        val holder = holder()

        holder.onIntent(FirstRunIntent.AddressChanged("HTTPS://Paperclip.example.com/"))

        holder.state.value shouldBe
            FirstRunState(
                address = "HTTPS://Paperclip.example.com/",
                normalizedAddress = "https://paperclip.example.com",
                issue = null,
                canContinue = true,
            )
    }

    @Test
    fun `an address without a scheme shows the missing scheme issue`() {
        val holder = holder()

        holder.onIntent(FirstRunIntent.AddressChanged("paperclip.example.com"))

        holder.state.value.issue shouldBe AddressIssue.MissingScheme
        holder.state.value.canContinue shouldBe false
        holder.state.value.normalizedAddress shouldBe null
    }

    @Test
    fun `insecure http maps to the https required issue`() {
        val holder = holder()

        holder.onIntent(FirstRunIntent.AddressChanged("http://example.com"))

        holder.state.value.issue shouldBe AddressIssue.MustUseHttps
    }

    @Test
    fun `each address problem maps to its own issue key`() {
        val expected =
            mapOf(
                "ftp://example.com" to AddressIssue.MustUseHttps,
                "https://" to AddressIssue.MissingHost,
                "https://exa mple.com" to AddressIssue.InvalidHost,
                "https://example.com:abc" to AddressIssue.InvalidPort,
                "https://user:secret@example.com" to AddressIssue.RemoveCredentials,
                "https://example.com?x=1" to AddressIssue.RemoveQueryOrFragment,
            )

        expected.forEach { (input, issue) ->
            val holder = holder()
            holder.onIntent(FirstRunIntent.AddressChanged(input))
            holder.state.value.issue shouldBe issue
            holder.state.value.canContinue shouldBe false
        }
    }

    @Test
    fun `clearing the field removes the error`() {
        val holder = holder()
        holder.onIntent(FirstRunIntent.AddressChanged("nonsense"))

        holder.onIntent(FirstRunIntent.AddressChanged(""))

        holder.state.value shouldBe FirstRunState()
    }

    @Test
    fun `state changes are emitted to collectors`() =
        kotlinx.coroutines.test.runTest {
            val holder = holder()

            holder.state.test {
                awaitItem() shouldBe FirstRunState()
                holder.onIntent(FirstRunIntent.AddressChanged("https://example.com"))
                awaitItem().canContinue shouldBe true
            }
        }
}
