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

        field.focus()
        field.typeText("paperclip.example.com")
        XCTAssertTrue(app.staticTexts["address-issue"].waitForExistence(timeout: 5))

        field.clearAndTypeText("https://paperclip.example.com")
        XCTAssertTrue(app.staticTexts["address-normalized"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["continue-button"].isEnabled)
    }
}

extension XCUIElement {
    /// Taps until the element really has keyboard focus. On a slow or freshly booted CI simulator the first tap can
    /// land before the field is ready, and `typeText` then fails with "Neither element nor any descendant has keyboard
    /// focus".
    func focus(attempts: Int = 5) {
        for _ in 0..<attempts {
            tap()
            if hasKeyboardFocus { return }
            _ = XCUIApplication().keyboards.firstMatch.waitForExistence(timeout: 2)
            if hasKeyboardFocus { return }
        }
        XCTFail("The element never received keyboard focus after \(attempts) taps")
    }

    var hasKeyboardFocus: Bool {
        (value(forKey: "hasKeyboardFocus") as? Bool) ?? false
    }

    func clearAndTypeText(_ text: String) {
        guard let current = value as? String else { return }
        focus()
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        typeText(text)
    }
}
