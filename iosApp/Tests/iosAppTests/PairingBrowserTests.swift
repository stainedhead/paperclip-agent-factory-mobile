import AuthenticationServices
import PaperclipKit
import Testing

@testable import iosApp

@MainActor
final class FakeWebAuthSession: WebAuthSessionHandle {
    let url: URL
    let complete: @MainActor (WebAuthSessionOutcome) -> Void
    var startResult = true
    private(set) var startCount = 0
    private(set) var cancelCount = 0

    init(url: URL, complete: @escaping @MainActor (WebAuthSessionOutcome) -> Void) {
        self.url = url
        self.complete = complete
    }

    func start() -> Bool {
        startCount += 1
        return startResult
    }

    func cancel() { cancelCount += 1 }
}

/// Kotlin objects are not `Sendable`; this test hands them to the Kotlin-facing async call on purpose.
private struct Unchecked<Value>: @unchecked Sendable {
    let value: Value
}

private func openLink(_ port: Unchecked<any PairingBrowser>, _ link: Unchecked<Secret>) async {
    try? await port.value.open(approvalUrl: link.value)
}

private func ignoreCompletion(_ url: URL?, _ error: Error?) {}

@MainActor
final class Harness {
    var sessions: [FakeWebAuthSession] = []
    var events: [PairingBrowserEvent] = []
    var startResult = true
    lazy var browser = ASWebAuthSessionPairingBrowser(
        anchor: { nil },
        allowsInsecureLocal: false,
        makeSession: { [unowned self] url, _, complete in
            let session = FakeWebAuthSession(url: url, complete: complete)
            session.startResult = startResult
            sessions.append(session)
            return session
        },
        onEvent: { [unowned self] in events.append($0) })
}

/// UI-010: the Swift side of pairing. The approval link is a secret: it is used to open one session and never kept,
/// logged or reported.
@MainActor
struct PairingBrowserTests {
    private let link = "https://home.example.com/cli-auth/challenge-TESTONLY?token=challenge-TESTONLY-secret"

    @Test func openingTheApprovalLinkStartsOneSessionForExactlyThatLink() {
        let harness = Harness()
        harness.browser.present(url: link)

        #expect(harness.sessions.count == 1)
        #expect(harness.sessions.first?.url.absoluteString == link)
        #expect(harness.sessions.first?.startCount == 1)
        #expect(harness.events == [.presented])
    }

    @Test func aLinkThatIsNotHttpsIsNeverOpened() {
        let harness = Harness()
        for bad in ["http://home.example.com/x", "javascript:alert(1)", "not a url", "", "file:///etc/passwd"] {
            harness.browser.present(url: bad)
        }
        #expect(harness.sessions.isEmpty)
        #expect(harness.events == Array(repeating: .couldNotPresent, count: 5))
    }

    @Test func plainHttpIsAllowedOnlyForLocalHostsAndOnlyWhenTheBuildAllowsIt() {
        var made: [URL] = []
        let strict = ASWebAuthSessionPairingBrowser(
            anchor: { nil }, allowsInsecureLocal: false,
            makeSession: { url, _, complete in
                made.append(url)
                return FakeWebAuthSession(url: url, complete: complete)
            }, onEvent: { _ in })
        strict.present(url: "http://localhost:3100/cli-auth/x")
        #expect(made.isEmpty)

        let debug = ASWebAuthSessionPairingBrowser(
            anchor: { nil }, allowsInsecureLocal: true,
            makeSession: { url, _, complete in
                made.append(url)
                return FakeWebAuthSession(url: url, complete: complete)
            }, onEvent: { _ in })
        debug.present(url: "http://localhost:3100/cli-auth/x")
        debug.present(url: "http://evil.example.com/cli-auth/x")
        #expect(made.map(\.host) == ["localhost"])
    }

    @Test func aSessionThatCannotStartIsReportedAsNotPresented() {
        let harness = Harness()
        harness.startResult = false
        harness.browser.present(url: link)
        #expect(harness.events == [.couldNotPresent])
    }

    @Test func theSheetClosingIsReportedButDoesNotMeanTheApprovalFailed() {
        let harness = Harness()
        harness.browser.present(url: link)
        harness.sessions[0].complete(.cancelled)
        harness.sessions[0].complete(.completed)
        // Closing the sheet is how a person finishes approving; the shared use case finds out by polling.
        #expect(harness.events == [.presented, .dismissed, .dismissed])
    }

    @Test func openingAgainCancelsTheSessionStillOnScreen() {
        let harness = Harness()
        harness.browser.present(url: link)
        harness.browser.present(url: link)
        #expect(harness.sessions.count == 2)
        #expect(harness.sessions[0].cancelCount == 1)
        #expect(harness.sessions[1].cancelCount == 0)
    }

    @Test func cancelPresentedPageCancelsTheSession() {
        let harness = Harness()
        harness.browser.present(url: link)
        harness.browser.cancelPresentedPage()
        #expect(harness.sessions[0].cancelCount == 1)
    }

    @Test func platformCompletionsMapToOutcomes() {
        #expect(WebAuthSessionOutcome(callbackURL: URL(string: "paperclip://x"), error: nil) == .completed)
        let cancelled = ASWebAuthenticationSessionError(.canceledLogin)
        #expect(WebAuthSessionOutcome(callbackURL: nil, error: cancelled) == .cancelled)
        let other = ASWebAuthenticationSessionError(.presentationContextNotProvided)
        #expect(WebAuthSessionOutcome(callbackURL: nil, error: other) == .failed)
        #expect(WebAuthSessionOutcome(callbackURL: nil, error: nil) == .failed)
    }

    @Test func eventsCarryNoLinkAndSoCannotLeakIt() {
        for event in [PairingBrowserEvent.presented, .dismissed, .couldNotPresent] {
            #expect(!String(describing: event).contains("http"))
        }
    }

    @Test func theAnchorIsTheWindowTheAppGaveAndAFallbackOtherwise() {
        let window = UIWindow()
        let session = ASWebAuthenticationSession(
            url: URL(string: "https://a.example")!, callbackURLScheme: nil, completionHandler: ignoreCompletion)
        let given = ASWebAuthSessionPairingBrowser(
            anchor: { window }, allowsInsecureLocal: false, makeSession: { _, _, _ in fatalError() }, onEvent: { _ in })
        #expect(given.presentationAnchor(for: session) === window)
        let none = ASWebAuthSessionPairingBrowser(
            anchor: { nil }, allowsInsecureLocal: false, makeSession: { _, _, _ in fatalError() }, onEvent: { _ in })
        #expect(none.presentationAnchor(for: session) !== window)
    }

    @Test func theLiveProviderTakesTheSwiftBrowserAndHandsTheLinkToItAsAPairingBrowser() async {
        let harness = Harness()
        let provider = LiveGraphProvider(opener: harness.browser, settings: .app)

        let port = Unchecked(value: provider.pairingBrowser)
        await openLink(port, Unchecked(value: Secret.companion.of(value: link)))
        #expect(harness.sessions.first?.url.absoluteString == link)
    }
}
