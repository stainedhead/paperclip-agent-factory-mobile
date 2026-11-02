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

/// PLACEHOLDER until Phase 1b merges: the real-factory graph does not exist yet. This provider is marked unavailable
/// and hands back the shared graph that today runs on in-memory fakes (ADR-037), which starts with no factory, so the
/// first-run flow is what a launch without `-demo` shows. Phase 1b's `createLiveGraph(...)` replaces the body of
/// `makeGraph()` and flips `isAvailable`; no view changes.
struct LiveGraphProvider: AppGraphProvider {
    let mode = GraphMode.live
    let isAvailable = false

    func makeGraph() -> PaperclipGraph {
        #if DEBUG
            let allowInsecureLocal = true
        #else
            let allowInsecureLocal = false
        #endif
        return PaperclipCore.shared.createGraph(allowInsecureLocal: allowInsecureLocal)
    }
}
