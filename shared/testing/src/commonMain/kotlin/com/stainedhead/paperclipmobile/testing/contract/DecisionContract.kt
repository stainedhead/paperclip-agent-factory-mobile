package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway

/**
 * Behaviour every [DecisionGateway] must have, taken from the pinned server (research Q4 and Q8:
 * `routes/decisions.ts`, `services/decisions.ts:683-711` and `dismiss` at 735-746).
 *
 * The gateway holds open decisions, by index in [openIds]: 0 and 1 have [validOptionId] and [otherOptionId] (both
 * with effects); 2 has an optional input [inputId] and [validOptionId]; 3 has an option with no effects
 * ([effectLessOptionId]); 4 has only options with effects.
 */
abstract class DecisionContract {
    abstract fun create(): DecisionGateway

    abstract val scope: CompanyScope
    abstract val openIds: List<DecisionId>
    abstract val validOptionId: String
    abstract val otherOptionId: String
    abstract val inputId: String
    abstract val effectLessOptionId: String

    suspend fun runAll() {
        anUnknownDecisionIsNotFound()
        decidingNeedsAnOpenDecisionAndAKnownOption()
        decidingAgainWithTheSameOptionAndInputsReplaysWhateverTheKey()
        decidingAgainWithAnotherOptionIsAConflict()
        decidingAgainWithOtherInputsIsAConflict()
        dismissingWithAnEffectLessOptionReplaysTheSecondTime()
        dismissingWithoutAnEffectLessOptionIsAConflictTheSecondTime()
        dismissingAfterAnotherOptionWasChosenIsAConflict()
    }

    private suspend fun anUnknownDecisionIsNotFound() {
        create().get(scope, DecisionId("no-such-decision")).mustFailWith(DomainError.NotFound, "an unknown decision")
    }

    private suspend fun decidingNeedsAnOpenDecisionAndAKnownOption() {
        val gateway = create()
        val id = openIds[PLAIN]
        check(gateway.get(scope, id).mustSucceed("get").status == DecisionStatus.Open) { "the decision starts open" }
        gateway.decide(scope, id, "no-such-option", emptyMap(), "key-bad").mustBeRejected("an unknown option")
        val decided = gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("decide")
        check(decided.status == DecisionStatus.Decided && decided.chosenOptionId == validOptionId) {
            "the response is the decided decision"
        }
        check(decided.outcome != null) { "the response carries the execution outcome" }
    }

    /** `decisions.ts:686-689`: same user, same option, same inputs is a success whatever the idempotency key. */
    private suspend fun decidingAgainWithTheSameOptionAndInputsReplaysWhateverTheKey() {
        val gateway = create()
        val id = openIds[PLAIN]
        val first = gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("decide")
        val sameKey = gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("a replay, same key")
        val newKey = gateway.decide(scope, id, validOptionId, emptyMap(), "key-2").mustSucceed("a replay, new key")
        check(sameKey.chosenOptionId == first.chosenOptionId && newKey.chosenOptionId == first.chosenOptionId) {
            "a replay returns the stored decision"
        }
    }

    /** `decisions.ts:691`: another option on a decided decision is `decision_already_resolved`. */
    private suspend fun decidingAgainWithAnotherOptionIsAConflict() {
        val gateway = create()
        val id = openIds[TWO_OPTIONS]
        gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("decide")
        gateway
            .decide(
                scope,
                id,
                otherOptionId,
                emptyMap(),
                "key-2",
            ).mustFailWith(DomainError.Conflict, "another option")
        check(gateway.get(scope, id).mustSucceed("get").chosenOptionId == validOptionId) { "the first choice stands" }
    }

    private suspend fun decidingAgainWithOtherInputsIsAConflict() {
        val gateway = create()
        val id = openIds[WITH_INPUT]
        gateway.decide(scope, id, validOptionId, mapOf(inputId to "go"), "key-1").mustSucceed("decide")
        gateway
            .decide(scope, id, validOptionId, mapOf(inputId to "stop"), "key-2")
            .mustFailWith(DomainError.Conflict, "other inputs")
        gateway.decide(scope, id, validOptionId, mapOf(inputId to "go"), "key-3").mustSucceed("the same inputs")
    }

    /** `decisions.ts:738-739`: dismiss is `decide` on the effect-less option, so a second dismiss replays. */
    private suspend fun dismissingWithAnEffectLessOptionReplaysTheSecondTime() {
        val gateway = create()
        val id = openIds[EFFECT_LESS]
        val dismissed = gateway.dismiss(scope, id, "not now").mustSucceed("dismiss")
        check(dismissed.status == DecisionStatus.Decided && dismissed.dismissed) { "a dismissed decision is final" }
        val again = gateway.dismiss(scope, id, null).mustSucceed("dismissing twice")
        check(again.status == DecisionStatus.Decided && again.chosenOptionId == dismissed.chosenOptionId) {
            "the second dismiss returns the stored decision"
        }
    }

    /** `decisions.ts:740-743`: with no effect-less option the direct update only matches an open decision. */
    private suspend fun dismissingWithoutAnEffectLessOptionIsAConflictTheSecondTime() {
        val gateway = create()
        val id = openIds[ALL_EFFECTS]
        val dismissed = gateway.dismiss(scope, id, "not now").mustSucceed("dismiss")
        check(dismissed.status == DecisionStatus.Decided && dismissed.dismissed) { "a dismissed decision is final" }
        gateway.dismiss(scope, id, null).mustFailWith(DomainError.Conflict, "dismissing twice")
    }

    private suspend fun dismissingAfterAnotherOptionWasChosenIsAConflict() {
        val gateway = create()
        val id = openIds[EFFECT_LESS]
        gateway.decide(scope, id, validOptionId, emptyMap(), "key-1").mustSucceed("decide")
        check(validOptionId != effectLessOptionId) { "the fixture chooses an option that is not the effect-less one" }
        gateway.dismiss(scope, id, null).mustFailWith(DomainError.Conflict, "dismissing after another choice")
    }

    private companion object {
        const val PLAIN = 0
        const val TWO_OPTIONS = 1
        const val WITH_INPUT = 2
        const val EFFECT_LESS = 3
        const val ALL_EFFECTS = 4
    }
}
