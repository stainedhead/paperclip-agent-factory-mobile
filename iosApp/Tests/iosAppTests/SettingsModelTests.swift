import PaperclipKit
import Testing

@testable import iosApp

/// Screen 13 through its model on the demo graph.
@MainActor
struct SettingsModelTests {
    private struct Rig {
        let model: SettingsModel
        let observing: Task<Void, Never>
        let controls: DemoControls
    }

    private func makeRig() -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        let model = SettingsModel(holder: graph.createSettingsStateHolder())
        return Rig(model: model, observing: Task { await model.observe() }, controls: controls)
    }

    private func finish(_ rig: Rig) {
        rig.observing.cancel()
        rig.model.close()
    }

    @Test func showsThePhoneOfTheMock() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.factories.count == 3 })
        let state = rig.model.state
        #expect(state.deviceName == "Alex's iPhone")
        #expect(state.appLockEnabled)
        #expect(state.idlePeriod == .fiveMinutes)
        #expect(state.destructiveActionAuthAlwaysOn)
        #expect(state.idleOptions.count == 6)
        finish(rig)
    }

    @Test func deviceNameLockAndIdlePeriodAreChangedThroughTheSharedSettings() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.deviceName == "Alex's iPhone" })
        rig.model.commitDeviceName("Studio phone")
        #expect(await eventually { rig.model.state.deviceName == "Studio phone" })
        rig.model.toggleAppLock(false)
        #expect(await eventually { rig.model.state.appLockEnabled == false })
        rig.model.chooseIdle(.fifteenMinutes)
        #expect(await eventually { rig.model.state.idlePeriod == .fifteenMinutes })
        rig.model.commitDeviceName("   ")
        #expect(await eventually { rig.model.state.deviceNameInvalid })
        #expect(rig.model.state.deviceName == "Studio phone")
        finish(rig)
    }

    @Test func pairedDevicesListThePhoneAndTheCommandLineKey() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.factories.count == 3 })
        rig.model.loadDevices("demo-home")
        #expect(await eventually { rig.model.state.pairedDevices?.status == .loaded })
        let keys = rig.model.state.pairedDevices?.keys ?? []
        #expect(keys.map(\.name) == ["Alex's iPhone", "paperclipai cli"])
        #expect(keys.map(\.isCurrentDevice) == [true, false])
        #expect(rig.model.state.profileName == "Alex Rivera")
        finish(rig)
    }

    @Test func revokingTheCommandLineKeyAsksFirstAndThenRemovesIt() async {
        let rig = makeRig()
        rig.model.loadDevices("demo-home")
        #expect(await eventually { rig.model.state.pairedDevices?.status == .loaded })

        rig.model.requestRevoke(instanceId: "demo-home", keyId: "bk-demo-cli")
        guard case let revoke as SettingsConfirmationRevokeKey = rig.model.state.confirmation else {
            Issue.record("expected a revoke confirmation")
            return
        }
        #expect(revoke.name == "paperclipai cli")
        #expect(revoke.isCurrentDevice == false)

        rig.model.dismissConfirmation()
        #expect(rig.model.state.confirmation == nil)
        rig.model.requestRevoke(instanceId: "demo-home", keyId: "bk-demo-cli")
        rig.model.confirm()
        #expect(await eventually { rig.model.state.pairedDevices?.keys.map(\.id) == ["bk-demo-a91f"] })
        finish(rig)
    }

    @Test func signOutEverywhereAsksFirstThenSignsEveryFactoryOutWithoutFaceID() async {
        let rig = makeRig()
        #expect(await eventually { rig.model.state.factories.count == 3 })
        rig.model.signOutEverywhere()
        #expect(rig.model.state.confirmation is SettingsConfirmationSignOutEverywhere)
        #expect(rig.model.state.factories[0].status == .signedIn)

        rig.model.confirm()
        #expect(await eventually { rig.model.state.factories.allSatisfy { $0.status == .signedOut } })
        #expect(rig.controls.biometricPromptCount == 0)
        finish(rig)
    }
}
