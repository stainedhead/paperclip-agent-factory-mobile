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
        // Created once for the life of the app, not each time SwiftUI evaluates the scene, and owned here: a view's
        // `.task` only mirrors it and never closes it. The app is iPhone-only with a single scene, so the model lives
        // until the process ends. A multi-scene build must create the model per scene and close it on scene teardown.
        _model = State(initialValue: FirstRunModel(holder: graph.createFirstRunStateHolder()))
    }

    var body: some Scene {
        WindowGroup {
            FirstRunView(model: model)
        }
    }
}
