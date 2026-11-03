import Testing
import UIKit

@testable import iosApp

@MainActor
struct TabBarAccessibilityTests {
    @Test func eachItemGetsItsAccessibilityValueAndAnEmptyOneClearsIt() {
        let items = [UITabBarItem(), UITabBarItem(), UITabBarItem()]
        items[2].accessibilityValue = "stale"

        TabBarAccessibility.apply(["3 waiting", "", ""], to: items)

        #expect(items[0].accessibilityValue == "3 waiting")
        #expect(items[1].accessibilityValue == nil)
        #expect(items[2].accessibilityValue == nil)
    }

    @Test func extraValuesOrItemsAreIgnored() {
        let items = [UITabBarItem()]
        TabBarAccessibility.apply(["1 waiting", "2 waiting"], to: items)
        #expect(items[0].accessibilityValue == "1 waiting")
        TabBarAccessibility.apply([], to: items)
        #expect(items[0].accessibilityValue == "1 waiting")
    }
}
