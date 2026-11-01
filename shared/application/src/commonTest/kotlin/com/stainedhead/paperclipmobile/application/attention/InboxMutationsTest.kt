package com.stainedhead.paperclipmobile.application.attention

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.SnoozePreset
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SnoozeItemTest {
    private val item = AttentionFixtures.approvalItem()

    @Test
    fun `FR-034 an operator snoozes an item for an hour and the gateway gets the item key and the time`() =
        runTest {
            val env = Env()

            val result =
                SnoozeItem(
                    env.attention,
                    env.clock,
                    env.timeZone,
                )(env.context(Role.Operator), item, SnoozePreset.OneHour)

            result.shouldBeInstanceOf<MutationResult.Done<*>>()
            env.attention.snoozes shouldBe listOf(item.dismissalKey to env.clock.now() + 1.hours)
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `ADR-020 tonight is 20 00 in the device time zone`() =
        runTest {
            val env = Env()
            env.timeZone.zone = TimeZone.of("Europe/Berlin")

            SnoozeItem(env.attention, env.clock, env.timeZone)(env.context(), item, SnoozePreset.Tonight)

            // the fake clock starts at 2026-11-01T12:00Z, which is 13:00 in Berlin; tonight = 20:00 CET = 19:00Z
            env.attention.snoozes
                .single()
                .second shouldBe Instant.parse("2026-11-01T19:00:00Z")
        }

    @Test
    fun `tonight after the cut-off is refused before any gateway call`() =
        runTest {
            val env = Env()
            env.timeZone.zone = TimeZone.of("Europe/Berlin")
            env.clock.advance(7.hours + 30.seconds) // 19:30Z = 20:30 in Berlin

            val result = SnoozeItem(env.attention, env.clock, env.timeZone)(env.context(), item, SnoozePreset.Tonight)

            result shouldBe MutationResult.Invalid(InvalidInput.SnoozeNotInFuture)
            env.attention.count("snooze") shouldBe 0
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            val result =
                SnoozeItem(env.attention, env.clock, env.timeZone)(env.context(Role.Viewer), item, SnoozePreset.OneHour)

            result shouldBe MutationResult.Denied
            env.attention.count("snooze") shouldBe 0
        }

    @Test
    fun `FR-096 a failed snooze is reported once and never retried`() =
        runTest {
            val env = Env()
            env.attention.failures["snooze"] = DomainError.Timeout

            val result = SnoozeItem(env.attention, env.clock, env.timeZone)(env.context(), item, SnoozePreset.OneHour)

            result shouldBe MutationResult.Failed(DomainError.Timeout)
            env.attention.count("snooze") shouldBe 1
        }
}

class DismissItemTest {
    private val item = AttentionFixtures.approvalItem()

    @Test
    fun `FR-034 dismissing sends the item's dismissal key and returns the dismissal`() =
        runTest {
            val env = Env()

            val result = DismissItem(env.attention)(env.context(Role.Operator), item)

            result.shouldBeInstanceOf<MutationResult.Done<*>>()
            env.attention.count("dismiss") shouldBe 1
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            DismissItem(env.attention)(env.context(Role.Viewer), item) shouldBe MutationResult.Denied

            env.attention.count("dismiss") shouldBe 0
        }

    @Test
    fun `a failed dismissal is reported once and never retried`() =
        runTest {
            val env = Env()
            env.attention.failures["dismiss"] = DomainError.ServerError

            DismissItem(env.attention)(env.context(), item) shouldBe MutationResult.Failed(DomainError.ServerError)

            env.attention.count("dismiss") shouldBe 1
        }
}

class UndoDismissTest {
    private val item = AttentionFixtures.approvalItem()

    @Test
    fun `FR-034 undo inside five seconds restores the item`() =
        runTest {
            val env = Env()
            val shownAt = env.clock.now()
            env.clock.advance(5.seconds)

            val result = UndoDismiss(env.attention, env.clock)(env.context(), item, shownAt)

            result.shouldBeInstanceOf<MutationResult.Done<*>>()
            env.attention.count("restore") shouldBe 1
        }

    @Test
    fun `undo after the five second window is refused before any gateway call`() =
        runTest {
            val env = Env()
            val shownAt = env.clock.now()
            env.clock.advance(5.seconds + 1.milliseconds)

            val result = UndoDismiss(env.attention, env.clock)(env.context(), item, shownAt)

            result shouldBe MutationResult.Invalid(InvalidInput.UndoWindowClosed)
            env.attention.count("restore") shouldBe 0
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            UndoDismiss(env.attention, env.clock)(env.context(Role.Viewer), item, env.clock.now()) shouldBe
                MutationResult.Denied

            env.attention.count("restore") shouldBe 0
        }

    @Test
    fun `a failed restore is reported once`() =
        runTest {
            val env = Env()
            env.attention.failures["restore"] = DomainError.Unreachable

            UndoDismiss(env.attention, env.clock)(env.context(), item, env.clock.now()) shouldBe
                MutationResult.Failed(DomainError.Unreachable)
            env.attention.count("restore") shouldBe 1
        }
}

class SetDecideByTest {
    private val item = AttentionFixtures.decisionItem()

    @Test
    fun `FR-034 setting decide-by sends the source kind and the subject id`() =
        runTest {
            val env = Env()

            val result = SetDecideBy(env.attention)(env.context(Role.Operator), item, DecideBy.Today)

            result.shouldBeInstanceOf<MutationResult.Done<*>>()
            env.attention.decideByCalls shouldBe listOf(Triple(item.sourceKind, item.subject.id, DecideBy.Today))
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            SetDecideBy(env.attention)(env.context(Role.Viewer), item, DecideBy.Today) shouldBe MutationResult.Denied

            env.attention.count("setDecideBy") shouldBe 0
        }

    @Test
    fun `a failure is reported once`() =
        runTest {
            val env = Env()
            env.attention.failures["setDecideBy"] = DomainError.Forbidden

            SetDecideBy(env.attention)(env.context(), item, DecideBy.None) shouldBe
                MutationResult.Failed(DomainError.Forbidden)
            env.attention.count("setDecideBy") shouldBe 1
        }
}
