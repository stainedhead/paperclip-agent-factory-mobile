import SwiftUI
import UIKit

/// Covers everything the app shows while it is not active, sheets and the in-app browser included, so the app-switcher
/// snapshot has nothing readable in it. A view inside the root cannot do this: sheets are presented above the root.
/// The shield is its own window above alerts, made when the scene stops being active and removed when it is back.
@MainActor
final class PrivacyShieldController {
    static let level = UIWindow.Level.alert + 1

    private var window: UIWindow?

    func setShown(_ shown: Bool) {
        if shown { show() } else { hide() }
    }

    private func show() {
        guard window == nil, let scene = Self.activeScene() else { return }
        let shield = UIWindow(windowScene: scene)
        shield.windowLevel = Self.level
        let host = UIHostingController(rootView: PrivacyShield())
        host.view.backgroundColor = .clear
        shield.rootViewController = host
        shield.isHidden = false
        window = shield
    }

    private func hide() {
        window?.isHidden = true
        window = nil
    }

    private static func activeScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }
}
