import XCTest

/// Every control on the main screens is at least 44 x 44 pt (UI-060; 0.1 pt of rounding tolerance).
/// The check reads the frames of the buttons, switches and text fields of each tab root; the system tab bar and the
/// navigation bar are the platform's own.
final class HitTargetUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func smallTargets(tab: String, root: String, extra: [String] = []) -> [String] {
        let app = launchOperations(tab: tab, root: root, extra: extra)
        _ = app.element(root).waitForExistence(timeout: 30)
        let bar = app.tabBars.firstMatch.frame
        let nav = app.navigationBars.firstMatch.frame
        var small: [String] = []
        for type in [XCUIElement.ElementType.button, .switch, .textField, .searchField] {
            for element in app.descendants(matching: type).allElementsBoundByIndex where element.exists {
                let frame = element.frame
                guard frame.width > 0, frame.height > 0, frame.maxY < bar.minY, frame.minY > nav.maxY else { continue }
                if frame.width < 43.9 || frame.height < 43.9 {
                    small.append("\(element.identifier) \(element.label) \(frame.size)")
                }
            }
        }
        return small
    }

    @MainActor
    func testInboxControlsAreAtLeast44Points() {
        XCTAssertEqual(smallTargets(tab: "inbox", root: "inbox-root"), [])
    }

    @MainActor
    func testFactoryHomeControlsAreAtLeast44Points() {
        XCTAssertEqual(smallTargets(tab: "factory", root: "factory-root"), [])
    }

    @MainActor
    func testWorkControlsAreAtLeast44Points() {
        XCTAssertEqual(smallTargets(tab: "work", root: "work-root"), [])
    }

    @MainActor
    func testAgentsControlsAreAtLeast44Points() {
        XCTAssertEqual(smallTargets(tab: "agents", root: "agents-root"), [])
    }

    @MainActor
    func testFactoriesControlsAreAtLeast44Points() {
        XCTAssertEqual(smallTargets(tab: "factories", root: "factories-root"), [])
    }
}
