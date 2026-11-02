import PaperclipKit
import Testing

@testable import iosApp

@MainActor
struct FirstRunModelTests {
    private func makeModel(allowInsecureLocal: Bool = false) -> FirstRunModel {
        let graph = PaperclipCore.shared.createGraph(allowInsecureLocal: allowInsecureLocal)
        return FirstRunModel(holder: graph.createFirstRunStateHolder())
    }

    @Test func startsEmptyWithContinueDisabled() {
        let model = makeModel()
        #expect(model.state.address.isEmpty)
        #expect(model.state.canContinue == false)
        #expect(model.state.issue == nil)
    }

    @Test func validAddressEnablesContinue() {
        let model = makeModel()
        model.addressChanged("HTTPS://Paperclip.example.com/")
        #expect(model.state.canContinue)
        #expect(model.state.normalizedAddress == "https://paperclip.example.com")
    }

    @Test func missingSchemeShowsIssue() {
        let model = makeModel()
        model.addressChanged("paperclip.example.com")
        #expect(model.state.issue == .missingScheme)
        #expect(model.state.canContinue == false)
    }

    @Test func releaseGraphRefusesLocalHttpAndDebugGraphAllowsIt() {
        let release = makeModel(allowInsecureLocal: false)
        let debug = makeModel(allowInsecureLocal: true)
        release.addressChanged("http://localhost:3100")
        debug.addressChanged("http://localhost:3100")
        #expect(release.state.issue == .mustUseHttps)
        #expect(debug.state.canContinue)
    }
}
