import XCTest

/// Every control on the main screens that is on screen between the navigation bar and the tab bar is at least 44 x 44
/// pt (UI-060; 0.1 pt of rounding tolerance). The check reads the frames of the buttons, switches and text fields of
/// each tab root and fails if it measured too few to mean anything. Controls below the fold, under the bars or off
/// screen are counted as not visible and are NOT covered by this test; the system tab bar and the navigation bar are
/// the platform's own.
final class HitTargetUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// What one screen's check looked at: how many controls it measured, which were too small, and how many it could
    /// not measure because they are off screen, under the bars or below the fold (those are not covered by this test).
    private struct Measurement {
        var examined = 0
        var notVisible = 0
        var small: [String] = []
    }

    @MainActor
    private func measure(tab: String, root: String, extra: [String] = []) -> Measurement {
        let app = launchOperations(tab: tab, root: root, extra: extra)
        XCTAssertTrue(app.element(root).waitForExistence(timeout: 30), "the \(tab) screen never appeared")
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 10), "the tab bar never appeared")
        // The bar's own frame can be reported as infinite on iOS 26; its buttons carry the real position.
        var barTop = app.tabBars.firstMatch.frame.minY
        if !barTop.isFinite {
            let firstTab = app.tabBars.firstMatch.buttons.firstMatch
            XCTAssertTrue(firstTab.waitForExistence(timeout: 10), "no tab bar button to place the bar by")
            barTop = firstTab.frame.minY
        }
        let navBottom = app.navigationBars.firstMatch.exists ? app.navigationBars.firstMatch.frame.maxY : 0
        XCTAssertTrue(barTop.isFinite && barTop > 0, "the tab bar has no usable position")

        var result = Measurement()
        for type in [XCUIElement.ElementType.button, .switch, .textField, .searchField] {
            for element in app.descendants(matching: type).allElementsBoundByIndex where element.exists {
                let frame = element.frame
                guard frame.width > 0, frame.height > 0, frame.maxY < barTop, frame.minY > navBottom else {
                    result.notVisible += 1
                    continue
                }
                result.examined += 1
                if frame.width < 43.9 || frame.height < 43.9 {
                    result.small.append("\(element.identifier) \(element.label) \(frame.size)")
                }
            }
        }
        let counts = "examined=\(result.examined) not-visible=\(result.notVisible) small=\(result.small.count)"
        print("HIT-TARGET tab=\(tab) \(counts)")
        return result
    }

    @MainActor
    private func assertAllLargeEnough(
        tab: String, root: String, atLeast minimum: Int, file: StaticString = #filePath, line: UInt = #line
    ) {
        let result = measure(tab: tab, root: root)
        XCTAssertGreaterThanOrEqual(
            result.examined, minimum, "only \(result.examined) controls were measured on \(tab); the check is vacuous",
            file: file, line: line)
        XCTAssertEqual(result.small, [], file: file, line: line)
    }

    @MainActor
    func testInboxControlsAreAtLeast44Points() {
        assertAllLargeEnough(tab: "inbox", root: "inbox-root", atLeast: 3)
    }

    @MainActor
    func testFactoryHomeControlsAreAtLeast44Points() {
        assertAllLargeEnough(tab: "factory", root: "factory-root", atLeast: 2)
    }

    @MainActor
    func testWorkControlsAreAtLeast44Points() {
        assertAllLargeEnough(tab: "work", root: "work-root", atLeast: 2)
    }

    @MainActor
    func testAgentsControlsAreAtLeast44Points() {
        assertAllLargeEnough(tab: "agents", root: "agents-root", atLeast: 2)
    }

    @MainActor
    func testFactoriesControlsAreAtLeast44Points() {
        assertAllLargeEnough(tab: "factories", root: "factories-root", atLeast: 2)
    }
}
