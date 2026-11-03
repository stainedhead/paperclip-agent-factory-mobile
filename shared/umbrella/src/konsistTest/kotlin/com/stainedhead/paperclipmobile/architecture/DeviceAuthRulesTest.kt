package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Device authentication is a security rule (PRD FR-014, AGENTS.md "Security Rules"): the use cases that take the
 * [DeviceAuthGuard] are exactly the destructive ones. A new use case that asks for a prompt, or a destructive one that
 * forgets to, changes this list and fails here until it is reviewed.
 */
class DeviceAuthRulesTest {
    private val destructive =
        setOf(
            "RemoveInstance",
            "RemoveInstanceHereOnly",
            "SignOutInstanceHereOnly",
            "DecideDecision",
            "RejectApproval",
            "ResolveBudgetIncident",
            "PauseAgent",
            "CancelRun",
        )

    @Test
    fun `exactly the destructive use cases depend on the device authentication guard`() {
        val users =
            scope("application")
                .classes()
                .filter { cls -> cls.primaryConstructor?.parameters?.any { it.type.name == "DeviceAuthGuard" } == true }
                .map { it.name }
                .toSet()

        Assertions.assertEquals(destructive, users, "Use cases that need device authentication changed")
    }
}
