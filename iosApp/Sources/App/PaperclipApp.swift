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
        // Created once for the life of the app, not each time SwiftUI evaluates the scene. The app is iPhone-only with
        // a single scene (TARGETED_DEVICE_FAMILY 1, no multiple-scene support), so the view that closes the model
        // in `.task` lives as long as the model. A multi-scene build must create the model per scene instead.
        _model = State(initialValue: FirstRunModel(holder: graph.createFirstRunStateHolder()))
    }

    var body: some Scene {
        WindowGroup {
            FirstRunView(model: model)
        }
    }
}
