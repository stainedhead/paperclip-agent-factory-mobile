package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.ActionScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Role
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Acceptance 8 and 10 over every mutation use case in [mutationMatrix] (the Konsist rule keeps that list complete). */
class MutationRoleMatrixTest {
    @Test
    fun `every company scoped action is covered by a row of the matrix`() {
        val covered = mutationMatrix.flatMap { it.actions }.toSet()
        val missing = Action.entries.filter { it.scope == ActionScope.Company && it !in covered }
        missing shouldBe emptyList()
        mutationMatrix.flatMap { it.actions }.size shouldBe covered.size
    }

    @Test
    fun `acceptance 8 a viewer never reaches a gateway and is never asked to authenticate`() =
        runTest {
            mutationMatrix.forEach { case ->
                val env = Env()

                case.run(env, Role.Viewer) shouldBe MutationResult.Denied
                (case.toString() to env.allGatewayCalls()) shouldBe (case.toString() to 0)
                (case.toString() to env.biometrics.calls) shouldBe (case.toString() to 0)
            }
        }

    @Test
    fun `acceptance 8 owners reach every gateway and operators everything except the budget raises`() =
        runTest {
            mutationMatrix.forEach { case ->
                listOf(Role.Owner, Role.Operator).forEach { role ->
                    val env = Env()

                    val result = case.run(env, role)

                    val label = "$case as $role"
                    if (role in case.allowedRoles()) {
                        (label to (result is MutationResult.Done<*>)) shouldBe (label to true)
                        (label to case.calls(env)[case.method]) shouldBe (label to 1)
                    } else {
                        (label to result) shouldBe (label to MutationResult.Denied)
                        (label to env.allGatewayCalls()) shouldBe (label to 0)
                        (label to env.biometrics.calls) shouldBe (label to 0)
                    }
                }
            }
        }

    @Test
    fun `acceptance 10 a timeout on any mutation reaches the gateway exactly once`() =
        runTest {
            mutationMatrix.forEach { case ->
                val env = Env()
                case.failures(env)[case.method] = DomainError.Timeout

                val result = case.run(env, Role.Owner)

                val label = case.toString()
                val settled =
                    result is MutationResult.Reconciled<*> ||
                        result is MutationResult.Unknown ||
                        result == MutationResult.Failed(DomainError.Timeout)
                (label to settled) shouldBe (label to true)
                (label to case.calls(env)[case.method]) shouldBe (label to 1)
            }
        }
}
