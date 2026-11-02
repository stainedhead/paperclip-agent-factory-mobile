import AuthenticationServices
import PaperclipKit
import UIKit

/// How a web authentication session ended, as the app cares about it.
enum WebAuthSessionOutcome: Equatable {
    /// The page redirected back to the app.
    case completed
    /// The person closed the sheet. Normal after approving in the page: the shared use case polls for the result.
    case cancelled
    case failed

    init(callbackURL: URL?, error: Error?) {
        if let error {
            let cancelled = (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin
            self = cancelled ? .cancelled : .failed
        } else {
            self = callbackURL == nil ? .failed : .completed
        }
    }
}

/// What the browser tells the app about the page. No case carries the link: it is a secret.
enum PairingBrowserEvent: Equatable {
    case presented
    case dismissed
    case couldNotPresent
}

/// The one thing the browser needs from `ASWebAuthenticationSession`, so tests can stand in for the system.
@MainActor
protocol WebAuthSessionHandle: AnyObject {
    func start() -> Bool
    func cancel()
}

typealias WebAuthSessionMaker =
    @MainActor (
        _ url: URL,
        _ provider: any ASWebAuthenticationPresentationContextProviding,
        _ completion: @escaping @MainActor (WebAuthSessionOutcome) -> Void
    ) -> any WebAuthSessionHandle

/// The system implementation of `WebAuthSessionHandle`.
@MainActor
private final class SystemWebAuthSession: WebAuthSessionHandle {
    private let session: ASWebAuthenticationSession

    init(
        url: URL, provider: any ASWebAuthenticationPresentationContextProviding,
        completion: @escaping @MainActor (WebAuthSessionOutcome) -> Void
    ) {
        // No callback scheme: the approval page does not redirect back to the app, so the only way the sheet ends is
        // the person closing it. The shared use case polls the server to learn whether they approved.
        session = ASWebAuthenticationSession(url: url, callbackURLScheme: nil) { callbackURL, error in
            let outcome = WebAuthSessionOutcome(callbackURL: callbackURL, error: error)
            Task { @MainActor in completion(outcome) }
        }
        session.presentationContextProvider = provider
    }

    func start() -> Bool { session.start() }
    func cancel() { session.cancel() }
}

/// Shows the pairing approval page in a system browser session (PRD FR-020) for the shared pairing use case.
///
/// - The link arrives as text (Swift cannot read a `Secret`). It opens one session and is then forgotten: it is never
///   logged, stored, attached or put in an event.
/// - `open` calls back once the page is on screen (or could not be). It never throws: an error thrown across the
///   Kotlin boundary would end the app. Whether the person approved is found by the use case polling the server.
/// - Only `https` links open, plus plain `http` to a local host in debug builds (the same rule as factory addresses).
@MainActor
final class ASWebAuthSessionPairingBrowser: NSObject, ApprovalPageOpener {
    private static let localHosts: Set<String> = ["localhost", "127.0.0.1", "10.0.2.2"]

    fileprivate let anchor: @MainActor () -> ASPresentationAnchor?
    private let allowsInsecureLocal: Bool
    private let makeSession: WebAuthSessionMaker
    private var current: (any WebAuthSessionHandle)?
    /// Where events go; the app environment sets it.
    var onEvent: @MainActor (PairingBrowserEvent) -> Void

    init(
        anchor: @escaping @MainActor () -> ASPresentationAnchor? = ASWebAuthSessionPairingBrowser.keyWindow,
        allowsInsecureLocal: Bool = ASWebAuthSessionPairingBrowser.debugBuild,
        makeSession: @escaping WebAuthSessionMaker = { url, provider, completion in
            SystemWebAuthSession(url: url, provider: provider, completion: completion)
        },
        onEvent: @escaping @MainActor (PairingBrowserEvent) -> Void = { _ in }
    ) {
        self.anchor = anchor
        self.allowsInsecureLocal = allowsInsecureLocal
        self.makeSession = makeSession
        self.onEvent = onEvent
    }

    static var debugBuild: Bool {
        #if DEBUG
            true
        #else
            false
        #endif
    }

    static func keyWindow() -> ASPresentationAnchor? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
    }

    /// The Kotlin side's entry point: show the page, then call `onShown`. It never throws and always calls back.
    nonisolated func open(url: String, onShown: @escaping () -> Void) {
        nonisolated(unsafe) let done = onShown
        Task { @MainActor in
            self.present(url: url)
            done()
        }
    }

    func present(url: String) {
        guard let page = acceptableURL(url) else {
            onEvent(.couldNotPresent)
            return
        }
        current?.cancel()
        let session = makeSession(page, self) { [weak self] _ in self?.onEvent(.dismissed) }
        current = session
        onEvent(session.start() ? .presented : .couldNotPresent)
    }

    /// Closes the page when the person cancels pairing in the app.
    func cancelPresentedPage() {
        current?.cancel()
    }

    private func acceptableURL(_ text: String) -> URL? {
        guard let url = URL(string: text), let host = url.host, !host.isEmpty else { return nil }
        switch url.scheme?.lowercased() {
        case "https": return url
        case "http": return allowsInsecureLocal && Self.localHosts.contains(host) ? url : nil
        default: return nil
        }
    }
}

extension ASWebAuthSessionPairingBrowser: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        anchor() ?? ASPresentationAnchor()
    }
}
