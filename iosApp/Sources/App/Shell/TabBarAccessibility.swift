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
        // The bar comes back after the pop transition; once it has settled the items are the final ones.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak controller] in controller?.applyValues() }
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
        // detail screen's bar coming back). Looking again twice a second is cheap: nothing changes unless an item has
        // lost its value.
        private var timer: Timer?

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            timer?.invalidate()
            timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
                MainActor.assumeIsolated { self?.applyValues() }
            }
        }

        override func viewDidDisappear(_ animated: Bool) {
            super.viewDidDisappear(animated)
            timer?.invalidate()
            timer = nil
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
            TabBarAccessibility.apply(values, to: tabs)
        }
    }
}
