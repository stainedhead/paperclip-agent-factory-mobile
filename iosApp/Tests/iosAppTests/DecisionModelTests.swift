import PaperclipKit
import Testing

@testable import iosApp

/// Screen 2 through its model on the demo graph: the model mirrors `DecisionState` and forwards each intent; the rules
/// (what is enabled, when Face ID is asked, what the final state is) are the shared holder's.
@MainActor
struct DecisionModelTests {
    private struct Rig {
        let model: DecisionModel
        let observing: Task<Void, Never>
        let controls: DemoControls

        @MainActor func finish() {
            observing.cancel()
            model.close()
        }
    }

    private func makeRig(
        id: String = "dec-1182", biometrics: [BiometricResult] = [], hints: Bool = true
    ) async -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        let hint =
            hints
            ? DecisionHints(originAgentName: "CTO", decideBy: "today", dismissalKey: "decision:\(id)")
            : DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil)
        let model = DecisionModel(
            holder: graph.attention.createDecisionStateHolder(
                instanceId: "demo-home", companyId: "company-acme", decisionId: id, hints: hint))
        let rig = Rig(model: model, observing: Task { await model.observe() }, controls: controls)
        _ = await eventually { model.state.phase == .content }
        return rig
    }

    @Test func showsTheThreeOptionsWithTheirEffectsBeforeAnythingIsChosen() async {
        let rig = await makeRig()
        let state = rig.model.state
        #expect(state.title == "Ship the billing migration to production?")
        #expect(state.originAgentName == "CTO")
        #expect(state.decideBy.kind == .today)
        #expect(
            state.options.map(\.label) == [
                "Ship tonight behind a feature flag", "Wait for QA sign-off", "Cancel the migration"
            ])
        #expect(state.options.map(\.isDestructive) == [false, false, true])
        #expect(state.options[0].effects.map(\.kind) == [.updateIssueStatus, .assignIssue, .commentOnIssue])
        #expect(state.canConfirm == false, "nothing is chosen yet")
        #expect(state.canSnooze)
        #expect(state.snoozeOptions == [.oneHour, .tonight, .tomorrow, .nextWeek])
        rig.finish()
    }

    @Test func choosingANonDestructiveOptionAndConfirmingDecidesAndReportsEachEffect() async {
        let rig = await makeRig()
        rig.model.select("ship")
        #expect(await eventually { rig.model.state.selectedOptionId == "ship" })
        #expect(rig.model.state.canConfirm)
        #expect(rig.model.state.confirmNeedsDeviceAuth == false)

        rig.model.confirm()
        #expect(await eventually { rig.model.state.finalState != nil })
        let state = rig.model.state
        #expect(state.finalState?.kind == .decided)
        #expect(state.finalState?.chosenOptionLabel == "Ship tonight behind a feature flag")
        #expect(state.options.isEmpty, "no options once it is decided")
        #expect(state.execution?.results.map(\.status) == [.executed, .executed, .executed])
        #expect(rig.controls.biometricPromptCount == 0)
        rig.finish()
    }

    @Test func aDestructiveOptionAsksFaceIDAndACancelledPromptChangesNothing() async {
        let rig = await makeRig(biometrics: [.cancelled, .approved])
        rig.model.select("cancel")
        #expect(await eventually { rig.model.state.confirmNeedsDeviceAuth })

        rig.model.confirm()
        #expect(await eventually { rig.model.state.feedback?.deviceAuth == .cancelled })
        #expect(rig.model.state.finalState == nil)
        #expect(rig.model.state.options.count == 3)
        #expect(rig.controls.biometricPromptCount == 1)

        rig.model.confirm()
        #expect(await eventually { rig.model.state.finalState != nil })
        #expect(rig.model.state.finalState?.chosenOptionLabel == "Cancel the migration")
        #expect(rig.controls.biometricPromptCount == 2)
        rig.finish()
    }

    @Test func aRequiredInputIsRefusedWhenEmptyAndAcceptedOnceFilled() async {
        let rig = await makeRig(id: "dec-1183")
        let input = rig.model.state.inputs[0]
        #expect(input.isRequired && input.maxLength?.intValue == 40)
        rig.model.select("yes")
        #expect(await eventually { rig.model.state.canConfirm })
        rig.model.confirm()
        #expect(await eventually { rig.model.state.feedback?.invalid == .inputRequired })
        #expect(rig.model.state.feedback?.inputId == "reason")
        #expect(rig.model.state.finalState == nil)

        rig.model.setInput("reason", "Quarter end")
        #expect(await eventually { rig.model.state.inputs[0].value == "Quarter end" })
        rig.model.confirm()
        #expect(await eventually { rig.model.state.finalState != nil })
        rig.finish()
    }

    @Test func dismissingResolvesTheDecisionWithoutChoosingAnOption() async {
        let rig = await makeRig()
        #expect(rig.model.state.canDismiss)
        rig.model.dismiss()
        #expect(await eventually { rig.model.state.finalState?.kind == .dismissed })
        rig.finish()
    }

    @Test func aDecisionResolvedElsewhereOpensOnItsFinalStateWithNoOptions() async {
        let graph = PaperclipCore.shared.createDemoGraph()
        graph.demoControls?.resolveDecisionElsewhere(decisionId: "dec-1182", optionId: "wait")
        let model = DecisionModel(
            holder: graph.attention.createDecisionStateHolder(
                instanceId: "demo-home", companyId: "company-acme", decisionId: "dec-1182",
                hints: DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil)))
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.finalState != nil })
        #expect(model.state.finalState?.chosenOptionLabel == "Wait for QA sign-off")
        #expect(model.state.options.isEmpty)
        #expect(model.state.canConfirm == false)
        #expect(model.state.refreshIntervalSeconds == 0)
        observing.cancel()
        model.close()
    }

    @Test func snoozingForwardsThePresetAndTheStateAsksTheScreenToClose() async {
        let rig = await makeRig()
        rig.model.snooze(.tomorrow)
        #expect(await eventually { rig.model.state.isSnoozed })
        rig.finish()
    }

    @Test func goingOfflineDisablesTheActionsAndSaysWhy() async {
        let rig = await makeRig()
        rig.model.select("ship")
        #expect(await eventually { rig.model.state.canConfirm })
        rig.controls.setOnline(online: false)
        #expect(await eventually { rig.model.state.isOffline })
        #expect(rig.model.state.actionsDisabledReason == .offline)
        #expect(rig.model.state.canConfirm == false)
        #expect(rig.model.state.canDismiss == false)
        rig.finish()
    }
}
