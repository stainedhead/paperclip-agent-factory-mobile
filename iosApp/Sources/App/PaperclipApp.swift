import PaperclipKit
import SwiftUI

@main
struct PaperclipApp: App {
    @State private var model: FirstRunModel

    init() {
        #if DEBUG
            let allowInsecureLocal = true
        #else
            let allowInsecureLocal = false
        #endif
        let graph = PaperclipCore.shared.createGraph(allowInsecureLocal: allowInsecureLocal)
        // Created once for the life of the app, not each time SwiftUI evaluates the scene.
        _model = State(initialValue: FirstRunModel(holder: graph.createFirstRunStateHolder()))
    }

    var body: some Scene {
        WindowGroup {
            FirstRunView(model: model)
        }
    }
}
