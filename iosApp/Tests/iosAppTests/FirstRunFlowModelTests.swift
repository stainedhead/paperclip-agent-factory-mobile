import PaperclipKit
import Testing

@testable import iosApp

/// Screen 15: the whole flow through the model on the first-run demo graph (fakes with a scripted approval).
@MainActor
struct FirstRunFlowModelTests {
    private func makeModel() -> (FirstRunModel, Task<Void, Never>) {
        let graph = PaperclipCore.shared.createFirstRunDemoGraph()
        let model = FirstRunModel(holder: graph.createFirstRunStateHolder())
        return (model, Task { await model.observe() })
    }

    @Test func theFlowGoesAddressProbePairSecureDone() async {
        let (model, observing) = makeModel()
        #expect(model.state.step == .address)
        model.addressChanged("https://paperclip.example.org")
        #expect(model.state.probe == .idle)

        model.checkAddress()
        #expect(await eventually { model.state.probe == .accepted })
        model.nameChanged("Studio")
        model.continueToPairing()
        #expect(await eventually { model.state.step == .pair })
        #expect(model.state.name == "Studio")

        model.pair()
        #expect(model.state.pairing == .waiting)
        #expect(await eventually { model.state.step == .secure })
        #expect(model.state.companies.map(\.name) == ["Acme Labs", "Side Project Co"])

        model.chooseCompany("company-acme")
        model.setAppLock(false)
        #expect(model.state.defaultCompanyId == "company-acme")
        #expect(model.state.appLockEnabled == false)
        model.finish()
        #expect(await eventually { model.state.step == .done })
        observing.cancel()
        model.close()
    }

    @Test func cancellingAWaitingPairingShowsCancelledAndBackReturnsToTheAddress() async {
        let (model, observing) = makeModel()
        model.addressChanged("https://paperclip.example.org")
        model.checkAddress()
        #expect(await eventually { model.state.probe == .accepted })
        model.continueToPairing()
        #expect(await eventually { model.state.step == .pair })
        model.pair()
        model.cancelPairing()
        #expect(await eventually { model.state.pairing == .cancelled })

        model.back()
        #expect(model.state.step == .address)
        observing.cancel()
        model.close()
    }

    @Test func aRefusedServerKeepsContinueOutOfReach() async {
        let graph = PaperclipCore.shared.createFirstRunDemoGraph()
        let model = FirstRunModel(holder: graph.createFirstRunStateHolder())
        // An invalid address never reaches the probe.
        model.addressChanged("ftp://paperclip.example.org")
        model.checkAddress()
        #expect(model.state.probe == .idle)
        #expect(model.state.issue == .mustUseHttps)
        model.continueToPairing()
        #expect(model.state.step == .address)
        model.close()
    }
}

/// The presentation helpers that turn state fields into label keys (no rule: a lookup).
struct FactoryPresentationTests {
    @Test func theMockStatusesAreConnectedKeyExpiredAndSignedOut() {
        let valid = ExpiryUi(state: .valid, daysLeft: 23)
        let expired = ExpiryUi(state: .expired, daysLeft: nil)
        let none = ExpiryUi(state: .noExpiry, daysLeft: nil)
        #expect(FactoryStatusPresentation(status: .signedIn, expiry: valid).label == .connected)
        #expect(FactoryStatusPresentation(status: .signedOut, expiry: expired).label == .keyExpired)
        #expect(FactoryStatusPresentation(status: .signedOut, expiry: none).label == .signedOut)
        #expect(FactoryStatusPresentation(status: .needsRePairing, expiry: none).label == .needsRePairing)
        #expect(FactoryStatusPresentation(status: .signedIn, expiry: valid).tone == .success)
        #expect(FactoryStatusPresentation(status: .signedOut, expiry: expired).tone == .warn)
        #expect(FactoryStatusPresentation(status: .signedOut, expiry: none).tone == .neutral)
    }

    @Test func pairAgainIsOfferedWhenTheFactoryNeedsAKey() {
        let none = ExpiryUi(state: .noExpiry, daysLeft: nil)
        #expect(
            FactoryStatusPresentation(status: .signedIn, expiry: ExpiryUi(state: .valid, daysLeft: 2)).offersPairing
                == false)
        #expect(FactoryStatusPresentation(status: .signedOut, expiry: none).offersPairing)
        #expect(FactoryStatusPresentation(status: .needsRePairing, expiry: none).offersPairing)
    }

    @Test func expiryTextCountsDaysAndHandlesOneNoneAndExpired() {
        #expect(ExpiryPresentation(ExpiryUi(state: .valid, daysLeft: 23)).text == "Board key expires in 23 days")
        #expect(ExpiryPresentation(ExpiryUi(state: .expiringSoon, daysLeft: 1)).text == "Board key expires in 1 day")
        #expect(ExpiryPresentation(ExpiryUi(state: .noExpiry, daysLeft: nil)).text == nil)
        #expect(ExpiryPresentation(ExpiryUi(state: .expired, daysLeft: nil)).text == "Board key expired")
        #expect(ExpiryPresentation(ExpiryUi(state: .expiringSoon, daysLeft: 3)).tone == .warn)
    }
}
