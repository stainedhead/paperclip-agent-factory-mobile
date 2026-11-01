package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway

/**
 * Behaviour every [DecisionGateway] must have (research Q4 and Q8: `routes/decisions.ts`, `svc/decisions.ts:690-711`).
 * The gateway holds at least three open decisions; [validOptionId] is an option of each of them.
 */
abstract class DecisionContract {
    abstract fun create(): DecisionGateway

    abstract val scope: CompanyScope
    abstract val openIds: List<DecisionId>
    abstract val validOptionId: String

    suspend fun runAll() {
        anUnknownDecisionIsNotFound()
        decidingNeedsAnOpenDecisionAndAKnownOption()
        aSecondDecisionIsAConflictButTheSameKeyReplays()
        dismissingEndsAnOpenDecisionOnlyOnce()
    }

    private suspend fun anUnknownDecisionIsNotFound() {
        create().get(scope, DecisionId("no-such-decision")).mustFailWith(DomainError.NotFound, "an unknown decision")
    }

    private suspend fun decidingNeedsAnOpenDecisionAndAKnownOption() {
        val gateway = create()
        val id = openIds[0]
        check(gateway.get(scope, id).mustSucceed("get").status == DecisionStatus.Open) { "the decision starts open" }
        gateway.decide(scope, id, "no-such-option", emptyMap(), "key-bad").mustBeRejected("an unknown option")
        val decided = gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("decide")
        check(decided.status == DecisionStatus.Decided && decided.chosenOptionId == validOptionId) {
            "the response is the decided decision"
        }
        check(decided.outcome != null) { "the response carries the execution outcome" }
    }

    private suspend fun aSecondDecisionIsAConflictButTheSameKeyReplays() {
        val gateway = create()
        val id = openIds[1]
        gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("decide")
        gateway
            .decide(
                scope,
                id,
                validOptionId,
                emptyMap(),
                "key-2",
            ).mustFailWith(DomainError.Conflict, "a second decide")
        gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("a replay with the same key")
    }

    private suspend fun dismissingEndsAnOpenDecisionOnlyOnce() {
        val gateway = create()
        val id = openIds[2]
        val dismissed = gateway.dismiss(scope, id, "not now").mustSucceed("dismiss")
        check(dismissed.status == DecisionStatus.Decided && dismissed.dismissed) { "a dismissed decision is final" }
        gateway.dismiss(scope, id, null).mustFailWith(DomainError.Conflict, "dismissing twice")
    }
}
