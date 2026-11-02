@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.DeviceAuthPolicy
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-B-5: every `can...` flag is `online && Permissions.allows(role, action)`, and a missing permission has a key. */
class RoleGatingTest {
    @Test
    fun `the disabled reason and the may flag agree with Permissions for every role action and connection`() {
        val combinations =
            Role.entries.flatMap { role ->
                Action.entries.flatMap { action -> listOf(true, false).map { online -> Triple(role, action, online) } }
            }
        combinations.forEach { (role, action, online) ->
            val allowed = online && Permissions.allows(role, action)
            mayAct(online, role, action) shouldBe allowed
            (disabledReason(online, role, action) == null) shouldBe allowed
        }
        disabledReason(true, Role.Viewer, Action.PauseAgent) shouldBe ActionsDisabledReason.ViewerRole
        disabledReason(true, Role.Operator, Action.RaiseBudgetAndResume) shouldBe ActionsDisabledReason.OwnerRole
        disabledReason(false, Role.Owner, Action.PauseAgent) shouldBe ActionsDisabledReason.Offline
    }

    @Test
    fun `an operator sees why Raise is disabled on a budget incident`() =
        runTest {
            val env = budgetEnv(Role.Operator)
            val holder = budgetHolder(env)
            advanceUntilIdle()

            holder.state.value.canKeepPaused shouldBe true
            holder.state.value.canRaise shouldBe false
            holder.state.value.raiseDisabledReason shouldBe ActionsDisabledReason.OwnerRole
            holder.state.value.actionsDisabledReason shouldBe null
        }

    @Test
    fun `an operator sees why Set budget is disabled on an agent`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.state.value.canSetBudget shouldBe false
            holder.state.value.budgetDisabledReason shouldBe ActionsDisabledReason.OwnerRole
            holder.state.value.actions
                .isNotEmpty() shouldBe true
        }

    @Test
    fun `an owner has no disabled reason anywhere`() =
        runTest {
            val env = workEnv(Role.Owner)
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.state.value.budgetDisabledReason shouldBe null
            holder.state.value.canSetBudget shouldBe true
        }

    @Test
    fun `rejectNeedsDeviceAuthFollowsPolicy`() =
        runTest {
            val env = approvalEnv(Role.Owner)
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.state.value.rejectNeedsDeviceAuth shouldBe DeviceAuthPolicy.requires(Action.RejectApproval)
        }
}
