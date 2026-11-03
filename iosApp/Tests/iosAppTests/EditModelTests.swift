import PaperclipKit
import Testing

@testable import iosApp

/// Screen 9 through its model on the demo graph, edit and add modes.
@MainActor
struct EditModelTests {
    private struct Rig {
        let model: EditModel
        let observing: Task<Void, Never>
        let controls: DemoControls
    }

    private func makeRig(_ id: String?, biometrics: [BiometricResult] = []) -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        let model = EditModel(holder: graph.createEditInstanceStateHolder(instanceId: id))
        return Rig(model: model, observing: Task { await model.observe() }, controls: controls)
    }

    private func finish(_ rig: Rig) {
        rig.observing.cancel()
        rig.model.close()
    }

    @Test func showsTheHomeFactoryOfTheMockWithAccessAndRoles() async {
        let rig = makeRig("demo-home")
        #expect(await eventually { rig.model.state.companies.count == 2 })
        let state = rig.model.state
        #expect(state.mode == .edit)
        #expect(state.name == "Home factory")
        #expect(state.address == "https://paperclip.tail1234.ts.net")
        #expect(state.defaultCompanyId == "company-acme")
        #expect(state.companies.map(\.name) == ["Acme Labs", "Side Project Co"])
        #expect(state.companies.map(\.role) == [.owner, .operator])
        #expect(state.profileName == "Alex Rivera")
        #expect(
            ExpiryPresentation(state.expiry ?? ExpiryUi(state: .noExpiry, daysLeft: nil)).text
                == "Board key expires in 23 days")
        #expect(state.pinningEnabled == false)
        finish(rig)
    }

    @Test func typingOverTheSavedValuesIsUnsavedUntilItMatchesAgain() async {
        let rig = makeRig("demo-home")
        #expect(await eventually { rig.model.state.name == "Home factory" })
        #expect(rig.model.state.hasUnsavedChanges == false)
        rig.model.nameChanged("Studio factory")
        #expect(rig.model.state.hasUnsavedChanges)
        rig.model.nameChanged("Home factory")
        #expect(rig.model.state.hasUnsavedChanges == false)
        finish(rig)
    }

    @Test func renamingEnablesSaveAndSavingMarksItSaved() async {
        let rig = makeRig("demo-home")
        #expect(await eventually { rig.model.state.name == "Home factory" })
        #expect(rig.model.state.canSave)
        rig.model.nameChanged("Studio")
        rig.model.save()
        #expect(await eventually { rig.model.state.saved })
        #expect(rig.model.state.name == "Studio")
        finish(rig)
    }

    @Test func aChangedAddressNeedsAFreshCheckBeforeSaveIsEnabled() async {
        let rig = makeRig("demo-home")
        #expect(await eventually { rig.model.state.name == "Home factory" })
        rig.model.addressChanged("https://other.example.com")
        #expect(rig.model.state.addressChangeClearsKey)
        #expect(rig.model.state.canSave == false)
        #expect(rig.model.state.probe == .idle)

        rig.model.checkAddress()
        #expect(await eventually { rig.model.state.probe == .accepted })
        #expect(rig.model.state.canSave)

        rig.model.addressChanged("https://third.example.com")
        #expect(rig.model.state.canSave == false, "editing the address again forgets the accepted check")
        finish(rig)
    }

    @Test func togglingThePinOpensTheEntryAndOffClearsIt() async {
        let rig = makeRig("demo-home")
        #expect(await eventually { rig.model.state.name == "Home factory" })
        rig.model.togglePinning(true)
        #expect(rig.model.state.pinningEnabled)
        #expect(rig.model.state.pinEntryOpen)
        rig.model.addPin("not a pin")
        #expect(rig.model.state.pinInvalid)
        rig.model.togglePinning(false)
        #expect(rig.model.state.pinningEnabled == false)
        #expect(rig.model.state.pinEntryOpen == false)
        finish(rig)
    }

    @Test func addModeStartsEmptyAndSavesOnlyAfterAnAcceptedCheck() async {
        let rig = makeRig(nil)
        #expect(rig.model.state.mode == .add)
        #expect(rig.model.state.canSave == false)
        rig.model.nameChanged("Studio")
        rig.model.addressChanged("https://studio.example.org")
        #expect(rig.model.state.canSave == false)
        rig.model.checkAddress()
        #expect(await eventually { rig.model.state.canSave })
        rig.model.save()
        #expect(await eventually { rig.model.state.saved })
        finish(rig)
    }

    @Test func pairingTheSignedOutCloudFactoryAgainSignsItIn() async {
        let rig = makeRig("demo-cloud")
        #expect(await eventually { rig.model.state.status == .signedOut })
        rig.model.pairAgain()
        #expect(await eventually { rig.model.state.status == .signedIn })
        #expect(rig.model.state.pairing == .idle)
        finish(rig)
    }

    @Test func signOutAsksFirstAndThenSignsOutWithoutAFaceIDPrompt() async {
        let rig = makeRig("demo-home")
        #expect(await eventually { rig.model.state.status == .signedIn })
        rig.model.signOut()
        #expect(rig.model.state.confirmation == .signOut)
        rig.model.dismissConfirmation()
        #expect(rig.model.state.confirmation == nil)
        rig.model.signOut()
        rig.model.confirm()
        #expect(await eventually { rig.model.state.status == .signedOut })
        #expect(rig.controls.biometricPromptCount == 0)
        finish(rig)
    }

    @Test func removeNeedsTheDeviceOwnerAndOnlyAnApprovedPromptRemoves() async {
        let rig = makeRig("demo-cloud", biometrics: [.cancelled, .approved])
        #expect(await eventually { rig.model.state.name == "Cloud factory" })
        rig.model.remove()
        #expect(rig.model.state.confirmation == .remove)
        rig.model.confirm()
        #expect(await eventually { rig.model.state.deviceAuthIssue == .cancelled })
        #expect(rig.model.state.removed == false)

        rig.model.confirm()
        #expect(await eventually { rig.model.state.removed })
        #expect(rig.controls.biometricPromptCount == 2)
        finish(rig)
    }
}
