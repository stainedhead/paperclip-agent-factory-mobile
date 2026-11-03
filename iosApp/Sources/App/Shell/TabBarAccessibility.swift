import SwiftUI
import UIKit

/// Gives the tab bar's buttons their accessibility values (the Inbox badge count).
///
/// SwiftUI paints `.badge` on the tab bar but, on iOS 26, neither the badge nor `accessibilityValue` modifiers on a
/// `tabItem` reach the tab button's accessibility element: VoiceOver and UI tests saw only "Inbox". The values are
/// therefore set on the underlying `UITabBarItem`s, in tab order.
enum TabBarAccessibility {
    /// Sets item `i`'s accessibility value to `values[i]` (empty clears it); extra values or items are ignored.
    @MainActor
    static func apply(_ values: [String], to items: [UITabBarItem]) {
        for (value, item) in zip(values, items) {
            item.accessibilityValue = value.isEmpty ? nil : value
        }
    }
}

/// An invisible view that finds the enclosing `UITabBarController` and applies `values` to its tab items.
struct TabBarAccessibilityValues: UIViewControllerRepresentable {
    let values: [String]

    func makeUIViewController(context: Context) -> Applier { Applier(values: values) }

    func updateUIViewController(_ controller: Applier, context: Context) {
        controller.values = values
        controller.applyValues()
    }

    final class Applier: UIViewController {
        var values: [String]

        init(values: [String]) {
            self.values = values
            super.init(nibName: nil, bundle: nil)
            view.isUserInteractionEnabled = false
            view.isAccessibilityElement = false
            view.accessibilityElementsHidden = true
        }

        @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            applyValues()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            applyValues()
        }

        func applyValues() {
            var controller: UIViewController? = self
            while let current = controller, !(current is UITabBarController) { controller = current.parent }
            guard let tabs = (controller as? UITabBarController) ?? tabBarController else { return }
            TabBarAccessibility.apply(values, to: tabs.tabBar.items ?? [])
        }
    }
}
