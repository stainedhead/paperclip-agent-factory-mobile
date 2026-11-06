import PaperclipKit
import Testing

@testable import iosApp

/// Screen 1 through its model on the demo graph: the model mirrors `InboxState` and forwards each intent; the rules
/// (which actions a card offers, what a dismiss hides, when Face ID is asked) are the shared holder's.
@MainActor
struct InboxModelTests {
    private struct Rig {
        let model: InboxModel
        let observing: Task<Void, Never>
        let controls: DemoControls

        @MainActor func finish() {
            observing.cancel()
            model.close()
        }
    }

    private func makeRig(biometrics: [BiometricResult] = []) async -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        let model = InboxModel(
            holder: graph.attention.createInboxStateHolder(instanceId: "demo-home", companyId: "company-acme"))
        let rig = Rig(model: model, observing: Task { await model.observe() }, controls: controls)
        _ = await eventually { model.state.phase == .content }
        return rig
    }

    private func ids(_ model: InboxModel) -> [String] {
        model.state.sections.flatMap { $0.items.map(\.id) }
    }

    @Test func mirrorsTheMockSectionsChipsAndBadge() async {
        let rig = await makeRig()
        let state = rig.model.state
        #expect(state.phase == .content)
        #expect(state.badgeCount == 3)
        #expect(state.totalCount == 9)
        #expect(state.sections.map(\.group) == [.decideNow, .thisWeek, .whenever])
        #expect(
            state.sections[0].items.map(\.title) == [
                "Ship the billing migration to production?", "Engineer 2 hit its monthly budget",
                "Hire a QA engineer agent"
            ])
        #expect(state.filters.map(\.kind) == [.approval, .decision, .issueThreadInteraction, .failedRun, .budgetAlert])
        #expect(state.snoozeOptions == [.oneHour, .tonight, .tomorrow, .nextWeek])
        rig.finish()
    }

    @Test func choosingAFilterForwardsTheIntentAndClearingShowsEverything() async {
        let rig = await makeRig()
        rig.model.select(.decision)
        #expect(await eventually { rig.model.state.selectedFilter == .decision })
        #expect(await eventually { ids(rig.model).count == 3 })
        rig.model.select(nil)
        #expect(await eventually { rig.model.state.selectedFilter == nil })
        #expect(await eventually { ids(rig.model).count == 9 })
        rig.finish()
    }

    @Test func approvingRemovesTheCardAndLowersTheBadge() async {
        let rig = await makeRig(biometrics: [.approved])
        rig.model.approve("approval:ap-hire-qa")
        #expect(await eventually { !ids(rig.model).contains("approval:ap-hire-qa") })
        #expect(await eventually { rig.model.state.badgeCount == 2 })
        rig.finish()
    }

    @Test func rejectAsksForANoteThenFaceIDAndACancelledPromptKeepsTheCard() async {
        let rig = await makeRig(biometrics: [.cancelled, .approved])
        let id = "approval:ap-hire-qa"
        rig.model.requestReject(id)
        #expect(await eventually { rig.model.state.rejectPrompt?.itemId == id })

        rig.model.confirmReject(note: "Not this quarter")
        #expect(await eventually { rig.model.state.feedback?.deviceAuth == .cancelled })
        #expect(ids(rig.model).contains(id), "no card goes before the device owner approves")
        #expect(rig.model.state.rejectPrompt != nil)
        #expect(rig.controls.biometricPromptCount == 1)

        rig.model.confirmReject(note: "Not this quarter")
        #expect(await eventually { !ids(rig.model).contains(id) })
        #expect(rig.model.state.rejectPrompt == nil)
        #expect(rig.controls.biometricPromptCount == 2)
        rig.finish()
    }

    @Test func cancellingTheNotePromptClearsItWithoutAskingFaceID() async {
        let rig = await makeRig()
        rig.model.requestReject("approval:ap-hire-qa")
        #expect(await eventually { rig.model.state.rejectPrompt != nil })
        rig.model.cancelReject()
        #expect(await eventually { rig.model.state.rejectPrompt == nil })
        #expect(rig.controls.biometricPromptCount == 0)
        rig.finish()
    }

    @Test func dismissingHidesTheCardOffersUndoAndUndoBringsItBack() async {
        let rig = await makeRig()
        let id = "decision:dec-1183"
        #expect(ids(rig.model).contains(id))
        rig.model.dismiss(id)
        #expect(await eventually { rig.model.state.undo?.itemId == id })
        #expect(!ids(rig.model).contains(id))

        rig.model.undo()
        #expect(await eventually { ids(rig.model).contains(id) })
        #expect(rig.model.state.undo == nil)
        rig.finish()
    }

    @Test func snoozingWithAPresetHidesTheCardAndOffersUndo() async {
        let rig = await makeRig()
        let id = "decision:dec-1183"
        rig.model.snooze(id, preset: .tomorrow)
        #expect(await eventually { rig.model.state.undo?.itemId == id })
        #expect(!ids(rig.model).contains(id))
        rig.finish()
    }

    @Test func theUndoOfferEndsOnItsOwnAfterTheWindow() async {
        let rig = await makeRig()
        rig.model.dismiss("decision:dec-1183")
        #expect(await eventually { rig.model.state.undo != nil })
        #expect(await eventually(timeoutSeconds: 9) { rig.model.state.undo == nil })
        rig.finish()
    }

    @Test func refreshingStartsARefreshAndKeepsTheContent() async {
        let rig = await makeRig()
        rig.model.refresh()
        #expect(await eventually { !rig.model.state.isRefreshing })
        #expect(rig.model.state.phase == .content)
        rig.finish()
    }

    @Test func offlineDisablesActionsWithTheirReasonAndAnApproveIsIgnored() async {
        let rig = await makeRig(biometrics: [.approved])
        rig.controls.setOnline(online: false)
        #expect(await eventually { rig.model.state.isOffline })
        #expect(rig.model.state.actionsDisabledReason == .offline)
        #expect(rig.model.state.phase == .content, "the cached cards stay visible")

        rig.model.approve("approval:ap-hire-qa")
        try? await Task.sleep(for: .milliseconds(300))
        #expect(ids(rig.model).contains("approval:ap-hire-qa"))

        rig.controls.setOnline(online: true)
        #expect(await eventually { !rig.model.state.isOffline })
        #expect(rig.model.state.actionsDisabledReason == nil)
        rig.finish()
    }

    @Test func theFirstReadIsTwelveSecondsOldAndARefreshIsCurrent() async {
        let rig = await makeRig()
        let first = rig.model.state.updatedAtEpochMillis?.int64Value
        rig.model.refresh()
        #expect(await eventually { rig.model.state.updatedAtEpochMillis?.int64Value != first })
        #expect((rig.model.state.updatedAtEpochMillis?.int64Value ?? 0) - (first ?? 0) == 12_000)
        rig.finish()
    }

    @Test func theMessageCanBeDismissed() async {
        let rig = await makeRig(biometrics: [.cancelled])
        rig.model.requestReject("approval:ap-hire-qa")
        rig.model.confirmReject(note: "no")
        #expect(await eventually { rig.model.state.feedback != nil })
        rig.model.dismissMessage()
        #expect(await eventually { rig.model.state.feedback == nil })
        rig.finish()
    }
}
