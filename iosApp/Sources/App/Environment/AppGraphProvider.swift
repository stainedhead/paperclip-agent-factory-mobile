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

    /// False while the provider cannot yet reach a real factory.
    var isAvailable: Bool { get }

    func makeGraph() -> PaperclipGraph
}

/// The demo graph: the design mocks' data on shared fakes, no server.
struct DemoGraphProvider: AppGraphProvider {
    let mode = GraphMode.demo
    let isAvailable = true

    func makeGraph() -> PaperclipGraph {
        PaperclipCore.shared.createDemoGraph()
    }
}

/// Demo ports with no factory yet and a person who approves every pairing at once: the first-run flow on fakes, for
/// demo launches and UI tests.
struct FirstRunDemoGraphProvider: AppGraphProvider {
    let mode = GraphMode.demo
    let isAvailable = true

    func makeGraph() -> PaperclipGraph {
        PaperclipCore.shared.createFirstRunDemoGraph()
    }
}

/// PLACEHOLDER until Phase 1b merges: the real-factory graph does not exist yet. This provider is marked unavailable
/// and hands back the shared graph that today runs on in-memory fakes (ADR-037), which starts with no factory, so the
/// first-run flow is what a launch without `-demo` shows.
///
/// It already takes the Swift side of pairing, so Phase 1b changes only the factory call in `makeGraph()` (to
/// `createLiveGraph(allowInsecureLocal:pairingBrowser:)`, passing `pairingBrowser`) and flips `isAvailable`; no view
/// changes. Until then the fakes graph keeps its own scripted browser and `pairingBrowser` is built but not used.
struct LiveGraphProvider: AppGraphProvider {
    let mode = GraphMode.live
    let isAvailable = false
    let opener: any ApprovalPageOpener

    /// The shared port the live graph will be given: the approval link goes from the shared `Secret` to the opener.
    var pairingBrowser: any PairingBrowser { OpenerPairingBrowser(opener: opener) }

    func makeGraph() -> PaperclipGraph {
        #if DEBUG
            let allowInsecureLocal = true
        #else
            let allowInsecureLocal = false
        #endif
        return PaperclipCore.shared.createGraph(allowInsecureLocal: allowInsecureLocal)
    }
}
