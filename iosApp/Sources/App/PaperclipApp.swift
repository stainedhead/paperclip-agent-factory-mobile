import SwiftUI

@main
struct PaperclipApp: App {
    /// The graph and every long-lived model belong to this scene's environment. It is created once here, not each
    /// time SwiftUI evaluates `body`, and no view closes anything it renders: teardown is the environment's.
    @State private var environment = AppEnvironment(configuration: .current)
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(environment: environment)
                .task { environment.start() }
                .onChange(of: scenePhase, initial: true) { _, phase in environment.scenePhaseChanged(phase) }
        }
    }
}
