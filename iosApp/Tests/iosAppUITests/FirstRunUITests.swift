import XCTest

final class FirstRunUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testTypingAnAddressShowsValidationFromTheSharedCore() {
        let app = XCUIApplication()
        app.launch()

        let field = app.textFields["address-field"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["continue-button"].isEnabled)

        field.tap()
        field.typeText("paperclip.example.com")
        XCTAssertTrue(app.staticTexts["address-issue"].waitForExistence(timeout: 5))

        field.clearAndTypeText("https://paperclip.example.com")
        XCTAssertTrue(app.staticTexts["address-normalized"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["continue-button"].isEnabled)
    }
}

extension XCUIElement {
    func clearAndTypeText(_ text: String) {
        guard let current = value as? String else { return }
        tap()
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        typeText(text)
    }
}
