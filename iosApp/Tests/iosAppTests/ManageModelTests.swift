import PaperclipKit
import Testing

@testable import iosApp

/// Screen 16 through its model on the demo graph: reorder, and removal that needs the device owner.
@MainActor
struct ManageModelTests {
    private struct Rig {
        let model: ManageModel
        let observing: Task<Void, Never>
        let controls: DemoControls
    }

    private func makeRig(biometrics: [BiometricResult] = []) -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        let model = ManageModel(holder: graph.createManageFactoriesStateHolder())
        return Rig(model: model, observing: Task { await model.observe() }, controls: controls)
    }

    @Test func listsTheThreeFactoriesWithStatus() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.rows.count == 3 })
        #expect(rig.model.state.rows.map(\.name) == ["Home factory", "Cloud factory", "Test factory"])
        #expect(rig.model.state.rows[2].address == "http://localhost:3100")
        rig.observing.cancel()
        rig.model.close()
    }

    @Test func reorderingChangesTheOrderThroughTheSharedUseCase() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.rows.count == 3 })
        rig.model.reorder(["demo-cloud", "demo-home", "demo-test"])
        #expect(await eventually { rig.model.state.rows.first?.id == "demo-cloud" })
        #expect(rig.model.state.reorderRejected == false)
        rig.observing.cancel()
        rig.model.close()
    }

    @Test func askingToRemoveStatesWhatWillBeDeletedAndKeepIsHarmless() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.rows.count == 3 })
        rig.model.requestRemove("demo-cloud")
        #expect(rig.model.state.confirmation?.name == "Cloud factory")
        #expect(rig.model.state.confirmation?.effects == [.configuration, .cachedData, .storedKey])
        rig.model.requestRemove("demo-home")
        #expect(rig.model.state.confirmation?.effects.contains(.serverKey) == true)

        rig.model.cancelRemove()
        #expect(rig.model.state.confirmation == nil)
        #expect(rig.model.state.rows.count == 3)
        #expect(rig.controls.biometricPromptCount == 0)
        rig.observing.cancel()
        rig.model.close()
    }

    @Test func aCancelledFaceIDRemovesNothingAndAnApprovedOneRemovesTheFactory() async {
        let rig = makeRig(biometrics: [.cancelled, .approved])
        #expect(await eventually { rig.model.state.rows.count == 3 })
        rig.model.requestRemove("demo-cloud")

        rig.model.confirmRemove()
        #expect(await eventually { rig.model.state.deviceAuthIssue == .cancelled })
        #expect(rig.model.state.rows.count == 3, "no factory goes before the device owner approves")
        #expect(rig.model.state.confirmation != nil)
        #expect(rig.controls.biometricPromptCount == 1)

        rig.model.confirmRemove()
        #expect(await eventually { rig.model.state.rows.count == 2 })
        #expect(rig.model.state.rows.map(\.name) == ["Home factory", "Test factory"])
        #expect(rig.model.state.confirmation == nil)
        #expect(rig.controls.biometricPromptCount == 2)
        rig.model.dismissMessage()
        rig.observing.cancel()
        rig.model.close()
    }
}
