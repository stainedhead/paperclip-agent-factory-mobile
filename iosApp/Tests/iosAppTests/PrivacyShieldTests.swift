import SwiftUI
import Testing
import UIKit

@testable import iosApp

/// A8: the app-switcher shield is a window above everything, so sheets and the in-app browser are covered too.
@MainActor
struct PrivacyShieldTests {
    private func shieldWindows() -> [UIWindow] {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
            .filter { $0.windowLevel == PrivacyShieldController.level && !$0.isHidden }
    }

    @Test func theShieldIsAWindowAboveAlertsAndComesAndGoesWithTheScenePhase() async {
        let controller = PrivacyShieldController()
        #expect(await eventually { UIApplication.shared.connectedScenes.contains { $0 is UIWindowScene } })
        #expect(PrivacyShieldController.level.rawValue > UIWindow.Level.alert.rawValue)

        controller.setShown(true)
        #expect(shieldWindows().count == 1, "a window above sheets covers them; a view in the hierarchy cannot")
        controller.setShown(true)
        #expect(shieldWindows().count == 1, "showing twice does not stack windows")

        controller.setShown(false)
        #expect(shieldWindows().isEmpty)
    }

    @Test func theEnvironmentRaisesTheShieldWhenTheAppIsNotActive() async {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo"]))
        environment.start()
        #expect(await eventually { UIApplication.shared.connectedScenes.contains { $0 is UIWindowScene } })
        environment.scenePhaseChanged(.inactive)
        #expect(shieldWindows().count == 1)
        environment.scenePhaseChanged(.active)
        #expect(shieldWindows().isEmpty)
        environment.shutdown()
    }
}
