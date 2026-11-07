import Foundation
import PaperclipKit

enum GraphMode: Equatable {
    case demo
    case live
}

/// The one seam between the app and the shared graph. Views and models never call `PaperclipCore` themselves; the
/// environment asks a provider, so switching from the demo to the real factory changes one type, not any screen.
@MainActor
protocol AppGraphProvider {
    var mode: GraphMode { get }

    /// Builds the graph. The real one can fail (the factory database is busy, the Keychain cannot be opened); the
    /// environment then shows a notice and offers a retry, and never falls back to fakes.
    func makeGraph() throws -> PaperclipGraph
}

/// The demo graph: the design mocks' data on shared fakes, no server.
struct DemoGraphProvider: AppGraphProvider {
    let mode = GraphMode.demo
    func makeGraph() throws -> PaperclipGraph {
        PaperclipCore.shared.createDemoGraph()
    }
}

/// Demo ports with no factory yet and a person who approves every pairing at once: the first-run flow on fakes, for
/// demo launches and UI tests.
struct FirstRunDemoGraphProvider: AppGraphProvider {
    let mode = GraphMode.demo
    func makeGraph() throws -> PaperclipGraph {
        PaperclipCore.shared.createFirstRunDemoGraph()
    }
}

/// Where the real graph keeps things on this phone and the sentences Face ID shows. The Keychain service holds the
/// board keys, the data directory the factory database; the reasons are localised by the app.
struct LiveSettings {
    let keychainService: String
    let dataDirectory: URL
    let unlockReason: String
    let confirmReason: String
    let keyReadReason: String

    /// The app's own: the board-key Keychain service, `Application Support/Paperclip`, the localised reasons.
    @MainActor static var app: LiveSettings {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return LiveSettings(
            keychainService: "com.stainedhead.paperclipmobile.board-keys",
            dataDirectory: support.appendingPathComponent("Paperclip", isDirectory: true),
            unlockReason: ShellLabel.biometricUnlockReason.text, confirmReason: ShellLabel.biometricConfirmReason.text,
            keyReadReason: ShellLabel.biometricKeyReadReason.text)
    }
}

/// The real graph: the Paperclip REST API, the Keychain, LocalAuthentication, the network monitor and the factory
/// database, through the shared live graph (Phase 1b). Opening can fail; that is an `Error` to show, not an abort.
struct LiveGraphProvider: AppGraphProvider {
    let mode = GraphMode.live
    let opener: ASWebAuthSessionPairingBrowser
    let settings: LiveSettings

    /// The port the live graph is given: the approval link is shown and dismissed on the main thread, and the page is
    /// closed when the pairing is cancelled (the shared code calls `dismiss`).
    var pairingBrowser: any PairingBrowser {
        let page = opener
        return PairingBrowsers.shared.onMainThread(
            show: { url, finished in
                MainActor.assumeIsolated { page.present(url: url) }
                _ = finished()
            },
            dismiss: { MainActor.assumeIsolated { page.cancelPresentedPage() } })
    }

    func makeGraph() throws -> PaperclipGraph {
        #if DEBUG
            let allowInsecureLocal = true
        #else
            let allowInsecureLocal = false
        #endif
        try FileManager.default.createDirectory(at: settings.dataDirectory, withIntermediateDirectories: true)
        return try LiveIosGraph.shared.openLiveGraph(
            allowInsecureLocal: allowInsecureLocal, pairingBrowser: pairingBrowser,
            settings: IosLiveSettings(
                keychainService: settings.keychainService, dataDirectory: settings.dataDirectory.path,
                unlockReason: settings.unlockReason, confirmReason: settings.confirmReason,
                keyReadReason: settings.keyReadReason))
    }
}
