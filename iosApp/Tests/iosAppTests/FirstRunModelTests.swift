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

    @Test func cancellingTheObservationLeavesTheModelWorkingAndItCanObserveAgain() async {
        let holder = PaperclipCore.shared.createGraph(allowInsecureLocal: false).createFirstRunStateHolder()
        let model = FirstRunModel(holder: holder)
        let first = Task { await model.observe() }
        await Task.yield()

        first.cancel()
        await first.value

        #expect(model.isClosed == false)
        model.addressChanged("https://paperclip.example.com")
        #expect(model.state.canContinue)

        // Changes made while nobody observed are picked up when the view's task runs again.
        holder.onIntent(intent: FirstRunIntentAddressChanged(text: "https://other.example.com"))
        let second = Task { await model.observe() }
        #expect(await eventually { model.state.normalizedAddress == "https://other.example.com" })
        holder.onIntent(intent: FirstRunIntentAddressChanged(text: "https://third.example.com"))
        #expect(await eventually { model.state.normalizedAddress == "https://third.example.com" })
        second.cancel()
        await second.value
        #expect(model.isClosed == false)
    }

    @Test func observingMirrorsStateChangesMadeOnTheHolder() async {
        let holder = PaperclipCore.shared.createGraph(allowInsecureLocal: false).createFirstRunStateHolder()
        let model = FirstRunModel(holder: holder)
        let observing = Task { await model.observe() }

        holder.onIntent(intent: FirstRunIntentAddressChanged(text: "https://paperclip.example.com"))

        #expect(await eventually { model.state.canContinue })
        observing.cancel()
        await observing.value
    }

    @Test func closingTheModelClosesTheHolder() async {
        let graph = PaperclipCore.shared.createDemoGraph()
        let open = graph.createFirstRunStateHolder()
        let closing = graph.createFirstRunStateHolder()
        let model = FirstRunModel(holder: closing)
        for holder in [open, closing] {
            holder.onIntent(intent: FirstRunIntentAddressChanged(text: "https://paperclip.example.com"))
        }

        model.close()
        model.close()  // closing twice is harmless
        for holder in [open, closing] {
            holder.onIntent(intent: FirstRunIntentProbeRequested.shared)
        }

        // A holder that is still open finishes its probe; the closed one has no scope left to run it in.
        #expect(await eventually { open.state.value.probe != .probing })
        #expect(closing.state.value.probe == .probing)
        try? await Task.sleep(for: .milliseconds(300))
        #expect(closing.state.value.probe == .probing)
        #expect(model.isClosed)
    }
}
