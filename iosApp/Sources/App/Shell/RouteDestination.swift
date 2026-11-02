import SwiftUI

/// The screen a route pushes. Routes whose screen is not built yet show the labelled placeholder.
struct RouteDestination: View {
    let route: AppRoute
    let environment: AppEnvironment

    var body: some View {
        switch route {
        case .manageFactories: ManageFactoriesView(environment: environment)
        default: RoutePlaceholderView(route: route)
        }
    }
}
