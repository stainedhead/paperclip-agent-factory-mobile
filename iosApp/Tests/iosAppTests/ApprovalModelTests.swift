import PaperclipKit
import Testing

@testable import iosApp

/// Screen 3 through its model on the demo graph: the model mirrors `ApprovalState` and forwards each intent; what is
/// offered, when a note is needed and when Face ID is asked are the shared holder's.
@MainActor
struct ApprovalModelTests {
    private struct Rig {
        let model: ApprovalModel
        let observing: Task<Void, Never>
        let controls: DemoControls

        @MainActor func finish() {
            observing.cancel()
            model.close()
        }
    }

    private func makeRig(
        id: String = "ap-hire-qa", biometrics: [BiometricResult] = [], resolved: Bool = false
    ) async -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        if resolved { controls.resolveApprovalElsewhere(approvalId: id) }
        let model = ApprovalModel(
            holder: graph.attention.createApprovalStateHolder(
                instanceId: "demo-home", companyId: "company-acme", approvalId: id))
        let rig = Rig(model: model, observing: Task { await model.observe() }, controls: controls)
        _ = await eventually { model.state.phase == .content }
        return rig
    }

    @Test func theHireApprovalCarriesTheMockValues() async {
        let rig = await makeRig()
        let state = rig.model.state
        #expect(state.type == .hireAgent)
        #expect(state.status == .pending)
        #expect(state.requestedByAgentName == "CEO")
        guard let hire = state.payload as? ApprovalPayloadUiHireAgent else {
            Issue.record("the payload is not a hire")
            return rig.finish()
        }
        #expect(hire.role == "qa")
        #expect(hire.reportsToName == "CTO")
        #expect(hire.adapterType == "claude_local")
        #expect(hire.monthlyBudget?.minorUnits == 15_000)
        #expect(state.issues.map(\.identifier) == ["PAP-1190"])
        #expect(state.comments.count == 1)
        #expect(state.actions == [.approve, .reject, .requestRevision, .comment])
        rig.finish()
    }

    @Test func approvingResolvesItWithoutFaceID() async {
        let rig = await makeRig()
        rig.model.approve()
        #expect(await eventually { rig.model.state.status == .approved })
        #expect(rig.model.state.isResolved)
        #expect(rig.model.state.actions.isEmpty || rig.model.state.actions == [.comment])
        #expect(rig.controls.biometricPromptCount == 0)
        rig.finish()
    }

    @Test func rejectingNeedsANoteAndFaceIDAndACancelledPromptKeepsTheSheetOpen() async {
        let rig = await makeRig(biometrics: [.cancelled, .approved])
        rig.model.requestReject()
        #expect(await eventually { rig.model.state.notePrompt == .reject })
        #expect(rig.model.state.rejectNeedsDeviceAuth)

        rig.model.confirmNote("")
        #expect(await eventually { rig.model.state.feedback?.invalid == .noteRequired })
        #expect(rig.controls.biometricPromptCount == 0, "no note, so no Face ID")

        rig.model.confirmNote("Not this quarter")
        #expect(await eventually { rig.model.state.feedback?.deviceAuth == .cancelled })
        #expect(rig.model.state.notePrompt == .reject, "the sheet stays after a cancelled prompt")
        #expect(rig.model.state.status == .pending)

        rig.model.confirmNote("Not this quarter")
        #expect(await eventually { rig.model.state.status == .rejected })
        #expect(rig.model.state.notePrompt == nil)
        #expect(rig.controls.biometricPromptCount == 2)
        rig.finish()
    }

    @Test func requestingRevisionNeedsANoteAndMovesTheApprovalToRevisionRequested() async {
        let rig = await makeRig()
        rig.model.requestRevision()
        #expect(await eventually { rig.model.state.notePrompt == .requestRevision })
        rig.model.confirmNote("")
        #expect(await eventually { rig.model.state.feedback?.invalid == .noteRequired })
        rig.model.confirmNote("Lower the budget")
        #expect(await eventually { rig.model.state.status == .revisionRequested })
        #expect(rig.model.state.actions.contains(.requestRevision) == false, "offered only from pending")
        #expect(rig.controls.biometricPromptCount == 0)
        rig.finish()
    }

    @Test func cancellingTheNoteClosesTheSheetAndChangesNothing() async {
        let rig = await makeRig()
        rig.model.requestReject()
        #expect(await eventually { rig.model.state.notePrompt == .reject })
        rig.model.cancelNote()
        #expect(await eventually { rig.model.state.notePrompt == nil })
        #expect(rig.model.state.status == .pending)
        rig.finish()
    }

    @Test func aCommentIsAddedToTheDiscussion() async {
        let rig = await makeRig()
        rig.model.comment("Looks right to me")
        #expect(await eventually { rig.model.state.comments.count == 2 })
        #expect(rig.model.state.comments.last?.body == "Looks right to me")
        #expect(rig.model.state.comments.last?.isFromAgent == false)
        rig.finish()
    }

    @Test func anApprovalResolvedElsewhereOpensResolvedWithNoActions() async {
        let rig = await makeRig(resolved: true)
        #expect(rig.model.state.status == .approved)
        #expect(rig.model.state.isResolved)
        #expect(rig.model.state.actions.contains(.approve) == false)
        #expect(rig.model.state.refreshIntervalSeconds == 0)
        rig.finish()
    }

    @Test func goingOfflineLeavesNoActionsAndSaysWhy() async {
        let rig = await makeRig()
        rig.controls.setOnline(online: false)
        #expect(await eventually { rig.model.state.isOffline })
        #expect(rig.model.state.actions.isEmpty)
        #expect(rig.model.state.actionsDisabledReason == .offline)
        rig.finish()
    }
}
