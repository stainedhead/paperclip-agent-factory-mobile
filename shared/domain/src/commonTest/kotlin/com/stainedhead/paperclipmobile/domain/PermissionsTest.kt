package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PermissionsTest {
    private val ownerOnly = setOf(Action.RaiseBudgetAndResume, Action.SetAgentBudget)

    @Test
    fun `ADR-011 every role and action pair follows the PRD table`() {
        val expected = mutableMapOf<Pair<Role, Action>, Boolean>()
        Action.entries.forEach { action ->
            expected[Role.Owner to action] = true
            expected[Role.Operator to action] = action.scope == ActionScope.Local || action !in ownerOnly
            expected[Role.Viewer to action] = action.scope == ActionScope.Local
        }

        Role.entries.forEach { role ->
            Action.entries.forEach { action ->
                Permissions.allows(role, action) shouldBe expected.getValue(role to action)
            }
        }
    }

    @Test
    fun `a viewer can only use local actions`() {
        Action.entries.filter { Permissions.allows(Role.Viewer, it) }.all { it.scope == ActionScope.Local } shouldBe
            true
        Permissions.allows(Role.Viewer, Action.SignOut) shouldBe true
        Permissions.allows(Role.Viewer, Action.RemoveFactory) shouldBe true
        Permissions.allows(Role.Viewer, Action.PauseAgent) shouldBe false
    }

    @Test
    fun `operators cannot raise or set budgets but can keep an incident paused`() {
        Permissions.allows(Role.Operator, Action.RaiseBudgetAndResume) shouldBe false
        Permissions.allows(Role.Operator, Action.SetAgentBudget) shouldBe false
        Permissions.allows(Role.Operator, Action.KeepBudgetPaused) shouldBe true
    }

    @Test
    fun `ADR-011 server roles map fail closed`() {
        Role.fromServer("owner", isInstanceAdmin = false) shouldBe Role.Owner
        Role.fromServer("admin", isInstanceAdmin = false) shouldBe Role.Owner
        Role.fromServer("operator", isInstanceAdmin = false) shouldBe Role.Operator
        Role.fromServer("viewer", isInstanceAdmin = false) shouldBe Role.Viewer
        Role.fromServer("member", isInstanceAdmin = false) shouldBe Role.Viewer
        Role.fromServer("something-new", isInstanceAdmin = false) shouldBe Role.Viewer
        Role.fromServer(null, isInstanceAdmin = false) shouldBe Role.Viewer
        Role.fromServer("viewer", isInstanceAdmin = true) shouldBe Role.Owner
    }

    @Test
    fun `FR-014 exactly the listed actions need device authentication`() {
        val required =
            setOf(
                Action.PauseAgent,
                Action.CancelRun,
                Action.RejectApproval,
                Action.DecideDestructiveOption,
                Action.RaiseBudgetAndResume,
                Action.RemoveFactory,
            )

        Action.entries.forEach { DeviceAuthPolicy.requires(it) shouldBe (it in required) }
    }

    @Test
    fun `FR-014 the app lock locks after the idle period only when enabled`() {
        val settings = AppSettings(deviceName = "Phone", appLockEnabled = true, idlePeriod = IdlePeriod.FiveMinutes)
        val start = kotlin.time.Instant.fromEpochSeconds(0)
        val fiveMinutes = kotlin.time.Duration.parse("5m")

        AppLockPolicy.shouldLock(settings, null, start) shouldBe true
        AppLockPolicy.shouldLock(settings, start, start + fiveMinutes - kotlin.time.Duration.parse("1s")) shouldBe false
        AppLockPolicy.shouldLock(settings, start, start + fiveMinutes) shouldBe true
        AppLockPolicy.shouldLock(settings.copy(appLockEnabled = false), null, start) shouldBe false
        AppLockPolicy.shouldLock(settings.copy(idlePeriod = IdlePeriod.Immediately), start, start) shouldBe true
    }
}
