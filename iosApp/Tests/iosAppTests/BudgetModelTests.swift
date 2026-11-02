import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// Screen 12 through its model on the demo graph: the model mirrors `BudgetIncidentState` and forwards each intent;
/// what may be submitted, when Face ID is asked and what the server refused are the shared holder's.
@MainActor
struct BudgetModelTests {
    private struct Rig {
        let model: BudgetModel
        let observing: Task<Void, Never>
        let controls: DemoControls

        @MainActor func finish() {
            observing.cancel()
            model.close()
        }
    }

    private func makeRig(
        biometrics: [BiometricResult] = [], serverSpend: Int64? = nil, resolved: Bool = false
    ) async -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        if let serverSpend { controls.setBudgetServerSpend(minorUnits: serverSpend) }
        if resolved { controls.resolveIncidentElsewhere(incidentId: "inc-eng-2") }
        let model = BudgetModel(
            holder: graph.attention.createBudgetIncidentStateHolder(
                instanceId: "demo-home", companyId: "company-acme", incidentId: "inc-eng-2"))
        let rig = Rig(model: model, observing: Task { await model.observe() }, controls: controls)
        _ = await eventually { model.state.phase == .content }
        return rig
    }

    @Test func showsTheEngineerTwoHardStopWithObservedSpendAndLimit() async {
        let rig = await makeRig()
        let state = rig.model.state
        #expect(state.scopeName == "Engineer 2")
        #expect(state.threshold == .hard)
        #expect(state.observed?.minorUnits == 20_000)
        #expect(state.limit?.minorUnits == 20_000)
        #expect(state.observedPercent == 100)
        #expect(state.canKeepPaused && state.canRaise)
        #expect(state.outcome == nil)
        rig.finish()
    }

    @Test func keepingPausedNeedsNoAmountAndEndsTheIncident() async {
        let rig = await makeRig()
        rig.model.choose(.keepPaused)
        #expect(await eventually { rig.model.state.canSubmit })
        rig.model.submit()
        #expect(await eventually { rig.model.state.outcome == .keptPaused })
        rig.finish()
    }

    @Test func anAmountAtOrBelowTheObservedSpendIsRefusedBeforeAnythingIsSent() async {
        let rig = await makeRig()
        rig.model.choose(.raiseAndResume)
        rig.model.setAmount(15_000)
        #expect(await eventually { rig.model.state.amountIssue == .amountMustExceedObserved })
        #expect(rig.model.state.canSubmit == false)
        rig.model.setAmount(20_000)
        #expect(await eventually { rig.model.state.amountIssue == .amountMustExceedObserved }, "equal is not enough")
        #expect(rig.controls.biometricPromptCount == 0)
        rig.finish()
    }

    @Test func aValidRaiseAsksFaceIDAndResolvesTheIncident() async {
        let rig = await makeRig(biometrics: [.cancelled, .approved])
        rig.model.choose(.raiseAndResume)
        rig.model.setAmount(25_000)
        #expect(await eventually { rig.model.state.canSubmit })
        #expect(rig.model.state.submitNeedsDeviceAuth)

        rig.model.submit()
        #expect(await eventually { rig.model.state.feedback?.deviceAuth == .cancelled })
        #expect(rig.model.state.outcome == nil, "a cancelled prompt changes nothing")

        rig.model.submit()
        #expect(await eventually { rig.model.state.outcome == .raised })
        #expect(rig.controls.biometricPromptCount == 2)
        rig.finish()
    }

    @Test func theServersReasonForRefusingARaiseIsKeptInTheState() async {
        let rig = await makeRig(serverSpend: 21_000)
        rig.model.choose(.raiseAndResume)
        rig.model.setAmount(20_500)
        #expect(await eventually { rig.model.state.canSubmit })
        rig.model.submit()
        #expect(await eventually { rig.model.state.feedback?.error?.key == .rejected })
        #expect(rig.model.state.feedback?.error?.detail == "New budget must exceed current observed spend")
        #expect(rig.model.state.outcome == nil)
        rig.finish()
    }

    @Test func anIncidentResolvedElsewhereOpensAsNoLongerActive() async {
        let graph = PaperclipCore.shared.createDemoGraph()
        graph.demoControls?.resolveIncidentElsewhere(incidentId: "inc-eng-2")
        let model = BudgetModel(
            holder: graph.attention.createBudgetIncidentStateHolder(
                instanceId: "demo-home", companyId: "company-acme", incidentId: "inc-eng-2"))
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.outcome == .noLongerActive })
        #expect(model.state.canKeepPaused == false)
        #expect(model.state.refreshIntervalSeconds == 0)
        observing.cancel()
        model.close()
    }

    @Test func goingOfflineDisablesTheChoicesAndSaysWhy() async {
        let rig = await makeRig()
        rig.controls.setOnline(online: false)
        #expect(await eventually { rig.model.state.isOffline })
        #expect(rig.model.state.canKeepPaused == false)
        #expect(rig.model.state.actionsDisabledReason == .offline)
        rig.finish()
    }
}

@MainActor
struct BudgetPresentationTests {
    private let enUS = Locale(identifier: "en_US")

    @Test func theUsageLineNamesSpendLimitAndPercent() {
        let line = BudgetPresentation.usage(
            observed: MoneyUi(minorUnits: 20_000, currency: "USD"), limit: MoneyUi(minorUnits: 20_000, currency: "USD"),
            percent: 100, locale: enUS)
        #expect(line == "$200 of $200 monthly budget \u{00B7} 100% used")
    }

    @Test func theAmountHelpNamesWhatHasBeenSpent() {
        let help = BudgetPresentation.amountHelp(observed: MoneyUi(minorUnits: 20_000, currency: "USD"), locale: enUS)
        #expect(help == "Must be more than $200, what has been spent.")
    }

    @Test func theGaugeFractionIsClampedToTheBar() {
        #expect(BudgetPresentation.gauge(percent: 100) == 1)
        #expect(BudgetPresentation.gauge(percent: 140) == 1)
        #expect(BudgetPresentation.gauge(percent: 40) == 0.4)
        #expect(BudgetPresentation.gauge(percent: -3) == 0)
    }

    @Test func aServerRefusalShowsTheServersReasonAfterTheMessage() {
        let feedback = FeedbackUi(
            kind: .failed, invalid: nil, inputId: nil, deviceAuth: nil,
            error: ErrorUi(key: .rejected, detail: "New budget must exceed current observed spend"))
        let text = FeedbackPresentation.text(feedback)
        #expect(text.contains("The factory rejected the request."))
        #expect(text.contains("New budget must exceed current observed spend"))
    }

    @Test func otherFeedbackKeepsItsPlainMessage() {
        let cancelled = FeedbackUi(
            kind: .deviceAuth, invalid: nil, inputId: nil, deviceAuth: .cancelled, error: nil)
        #expect(FeedbackPresentation.text(cancelled) == L10n.text(DeviceAuthIssue.cancelled))
    }
}
