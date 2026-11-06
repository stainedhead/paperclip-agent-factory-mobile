import SwiftUI
import Testing
import UIKit

@testable import iosApp

/// A8: the app-switcher shield is a window above everything, so sheets and the in-app browser are covered too.
@MainActor
struct PrivacyShieldTests {
    /// Only the controller's own window is checked: the app hosting these tests runs its own shield, which appears
    /// whenever the host scene is momentarily inactive, so counting every window above alerts is not deterministic.
    private func isUp(_ window: UIWindow?) -> Bool {
        guard let window else { return false }
        return !window.isHidden && window.windowLevel == PrivacyShieldController.level
    }

    @Test func theShieldIsAWindowAboveAlertsAndComesAndGoesWithTheScenePhase() async {
        let controller = PrivacyShieldController()
        #expect(await eventually { UIApplication.shared.connectedScenes.contains { $0 is UIWindowScene } })
        #expect(PrivacyShieldController.level.rawValue > UIWindow.Level.alert.rawValue)

        controller.setShown(true)
        let shield = controller.window
        #expect(isUp(shield), "a window above sheets covers them; a view in the hierarchy cannot")
        controller.setShown(true)
        #expect(controller.window === shield, "showing twice does not stack windows")

        controller.setShown(false)
        #expect(controller.window == nil)
        #expect(shield?.isHidden == true)
    }

    @Test func theEnvironmentRaisesTheShieldWhenTheAppIsNotActive() async {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo"]))
        environment.start()
        #expect(await eventually { UIApplication.shared.connectedScenes.contains { $0 is UIWindowScene } })
        environment.scenePhaseChanged(.inactive)
        let shield = environment.privacyShield.window
        #expect(isUp(shield))
        environment.scenePhaseChanged(.active)
        #expect(environment.privacyShield.window == nil)
        #expect(shield?.isHidden == true)
        environment.shutdown()
    }
}
