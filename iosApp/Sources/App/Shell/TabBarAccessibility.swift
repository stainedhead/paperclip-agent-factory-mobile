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
    static func apply(_ values: [String], to controller: UITabBarController) {
        // The view controllers' own items are what UIKit rebuilds the bar from (it does so when a detail screen
        // hides and shows the bar), the bar's items are what is on screen now: set both.
        apply(values, to: (controller.viewControllers ?? []).map(\.tabBarItem))
        apply(values, to: controller.tabBar.items ?? [])
    }

    @MainActor
    static func apply(_ values: [String], to items: [UITabBarItem]) {
        for (value, item) in zip(values, items) {
            let wanted = value.isEmpty ? nil : value
            guard item.accessibilityValue != wanted else { continue }
            item.accessibilityValue = wanted
            // A button reads its item's accessibility value when the item's badge changes, not when the value does:
            // re-assigning the badge (same text, no redraw in between) makes the live button pick the value up.
            let badge = item.badgeValue
            item.badgeValue = nil
            item.badgeValue = badge
        }
    }
}

/// An invisible view that finds the enclosing `UITabBarController` and applies `values` to its tab items.
struct TabBarAccessibilityValues: UIViewControllerRepresentable {
    let values: [String]
    /// Changes whenever a tab's navigation path does, so the values are set again once a detail screen has gone and
    /// the tab bar is back (UIKit may have rebuilt its items meanwhile).
    let pathDepth: Int

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

        // UIKit rebuilds the bar's items at moments no SwiftUI state announces (a sheet over the tabs closing, a
        // detail screen's bar coming back). Those moments are observed, not polled: the bar's `items`, its hidden
        // state and the controller's view controllers are key-value observed, and the bar's layer position changes
        // when it slides back in. Each change re-applies the values once.
        private var observations: [NSKeyValueObservation] = []
        private weak var observedBar: UITabBar?

        private func observeBar(of tabs: UITabBarController) {
            guard observedBar !== tabs.tabBar else { return }
            observedBar = tabs.tabBar
            let reapply: @Sendable () -> Void = { [weak self] in
                Task { @MainActor in self?.applyValues() }
            }
            observations = [
                tabs.tabBar.observe(\.items, options: [.new]) { _, _ in reapply() },
                tabs.tabBar.observe(\.isHidden, options: [.new]) { _, _ in reapply() },
                tabs.tabBar.layer.observe(\.position, options: [.new]) { _, _ in reapply() },
                tabs.observe(\.viewControllers, options: [.new]) { _, _ in reapply() }
            ]
        }

        override func viewDidDisappear(_ animated: Bool) {
            super.viewDidDisappear(animated)
            observations = []
            observedBar = nil
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
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
            observeBar(of: tabs)
            TabBarAccessibility.apply(values, to: tabs)
        }
    }
}
