package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SeverityTest {
    @Test
    fun `server values map and unknown values fall back to medium`() {
        Severity.fromServer("critical") shouldBe Severity.Critical
        Severity.fromServer("high") shouldBe Severity.High
        Severity.fromServer("medium") shouldBe Severity.Medium
        Severity.fromServer("low") shouldBe Severity.Low
        Severity.fromServer("urgent-from-the-future") shouldBe Severity.Medium
        Severity.fromServer(null) shouldBe Severity.Medium
    }

    @Test
    fun `critical sorts before low`() {
        (Severity.Critical.rank < Severity.High.rank) shouldBe true
        (Severity.High.rank < Severity.Medium.rank) shouldBe true
        (Severity.Medium.rank < Severity.Low.rank) shouldBe true
    }
}

class MoneyTest {
    @Test
    fun `amounts are integer minor units and compare in the same currency`() {
        val low = Money.usdCents(1_000)
        val high = Money.usdCents(1_001)
        (high > low) shouldBe true
        (low < high) shouldBe true
        low.compareTo(Money.usdCents(1_000)) shouldBe 0
        low.currency shouldBe "USD"
    }

    @Test
    fun `comparing different currencies is refused`() {
        assertFailsWith<IllegalArgumentException> { Money(10, "EUR").compareTo(Money.usdCents(10)) }
    }

    @Test
    fun `a currency must be three capital letters`() {
        assertFailsWith<IllegalArgumentException> { Money(1, "usd") }
        assertFailsWith<IllegalArgumentException> { Money(1, "US") }
        Money(1, "EUR").currency shouldBe "EUR"
    }

    @Test
    fun `a negative amount is refused`() {
        assertFailsWith<IllegalArgumentException> { Money.usdCents(-1) }
    }
}

class DecideByTest {
    @Test
    fun `the server presets parse`() {
        DecideBy.parse("today") shouldBe DecideBy.Today
        DecideBy.parse("this_week") shouldBe DecideBy.ThisWeek
        DecideBy.parse("whenever") shouldBe DecideBy.Whenever
    }

    @Test
    fun `a calendar date parses`() {
        DecideBy.parse("2026-11-03") shouldBe DecideBy.OnDate(LocalDate(2026, 11, 3))
    }

    @Test
    fun `null and anything unparseable is none`() {
        DecideBy.parse(null) shouldBe DecideBy.None
        DecideBy.parse("") shouldBe DecideBy.None
        DecideBy.parse("next-tuesday") shouldBe DecideBy.None
        DecideBy.parse("2026-02-30") shouldBe DecideBy.None
        DecideBy.parse("2026-1-3") shouldBe DecideBy.None
        DecideBy.parse("+12026-11-03") shouldBe DecideBy.None
    }

    @Test
    fun `values go back to the server string`() {
        DecideBy.Today.toServer() shouldBe "today"
        DecideBy.ThisWeek.toServer() shouldBe "this_week"
        DecideBy.Whenever.toServer() shouldBe "whenever"
        DecideBy.OnDate(LocalDate(2026, 11, 3)).toServer() shouldBe "2026-11-03"
        DecideBy.None.toServer() shouldBe null
    }
}

class ServerKindsTest {
    @Test
    fun `all eleven source kinds parse and unknown ones are kept as unsupported`() {
        AttentionSourceKind.known.size shouldBe 11
        AttentionSourceKind.known.forEach { AttentionSourceKind.fromServer(it.serverName) shouldBe it }
        AttentionSourceKind.fromServer("budget_alert") shouldBe AttentionSourceKind.BudgetAlert
        val unknown = AttentionSourceKind.fromServer("quantum_review")
        unknown shouldBe AttentionSourceKind.Unsupported("quantum_review")
        unknown.serverName shouldBe "quantum_review"
    }

    @Test
    fun `approval types parse and unknown ones are unsupported`() {
        ApprovalType.fromServer("hire_agent") shouldBe ApprovalType.HireAgent
        ApprovalType.fromServer("approve_ceo_strategy") shouldBe ApprovalType.ApproveCeoStrategy
        ApprovalType.fromServer("budget_override_required") shouldBe ApprovalType.BudgetOverrideRequired
        ApprovalType.fromServer("request_board_approval") shouldBe ApprovalType.RequestBoardApproval
        ApprovalType.fromServer("sign_contract") shouldBe ApprovalType.Unsupported("sign_contract")
    }

    @Test
    fun `interaction kinds parse including the sixth and unknown ones are unsupported`() {
        InteractionKind.known.size shouldBe 6
        InteractionKind.known.forEach { InteractionKind.fromServer(it.serverName) shouldBe it }
        InteractionKind.fromServer("connection_intent") shouldBe InteractionKind.ConnectionIntent
        InteractionKind.fromServer("future_kind") shouldBe InteractionKind.Unsupported("future_kind")
    }
}

class ResolverAudienceTest {
    private fun audience(addresseeUserId: String?) =
        ResolverAudience(
            requestedPolicy = ResolverPolicy.Anyone,
            effectivePolicy = ResolverPolicy.Anyone,
            policySource = ResolverPolicySource.Requested,
            addresseeUserId = addresseeUserId,
        )

    @Test
    fun `an open card allows any user`() {
        audience(null).allowsUser("user-1") shouldBe true
        audience(null).allowsUser(null) shouldBe true
    }

    @Test
    fun `a card addressed to someone else excludes this user but not the addressee`() {
        audience("user-2").allowsUser("user-1") shouldBe false
        audience("user-2").allowsUser(null) shouldBe false
        audience("user-1").allowsUser("user-1") shouldBe true
    }

    @Test
    fun `policies map from the server and unknown ones fail closed`() {
        ResolverPolicy.fromServer("anyone") shouldBe ResolverPolicy.Anyone
        ResolverPolicy.fromServer("board_or_agents") shouldBe ResolverPolicy.Anyone
        ResolverPolicy.fromServer("not_creator") shouldBe ResolverPolicy.NotCreator
        ResolverPolicy.fromServer("human_only") shouldBe ResolverPolicy.HumanOnly
        ResolverPolicy.fromServer("something_new") shouldBe ResolverPolicy.HumanOnly
        ResolverPolicy.fromServer(null) shouldBe ResolverPolicy.HumanOnly
    }
}
