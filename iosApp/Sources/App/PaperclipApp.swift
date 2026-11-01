import PaperclipKit
import SwiftUI

@main
struct PaperclipApp: App {
    private let graph: PaperclipGraph

    init() {
        #if DEBUG
            let allowInsecureLocal = true
        #else
            let allowInsecureLocal = false
        #endif
        graph = PaperclipCore.shared.createGraph(allowInsecureLocal: allowInsecureLocal)
    }

    var body: some Scene {
        WindowGroup {
            FirstRunView(model: FirstRunModel(holder: graph.createFirstRunStateHolder()))
        }
    }
}
