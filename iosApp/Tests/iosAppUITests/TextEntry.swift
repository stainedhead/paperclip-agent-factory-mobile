import XCTest

extension XCUIElement {
    /// Taps the field until it really has keyboard focus, waiting for the software keyboard between taps. On a slow
    /// or freshly booted simulator the first tap can land before the field is ready, and `typeText` then fails with
    /// "Neither element nor any descendant has keyboard focus".
    @MainActor
    func focus(attempts: Int = 6, file: StaticString = #filePath, line: UInt = #line) {
        guard waitForExistence(timeout: 15) else {
            XCTFail("The field \(identifier) never appeared", file: file, line: line)
            return
        }
        for _ in 0..<attempts {
            if !hasKeyboardFocus { tap() }
            if hasKeyboardFocus { return }
            _ = XCUIApplication().keyboards.firstMatch.waitForExistence(timeout: 2)
            if hasKeyboardFocus { return }
            Thread.sleep(forTimeInterval: 0.5)
        }
        XCTFail("The field \(identifier) never received keyboard focus after \(attempts) taps", file: file, line: line)
    }

    /// Whether the element's accessibility value becomes `expected` within `timeout` seconds.
    @MainActor
    func waitForValue(_ expected: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "value == %@", expected)
        return XCTWaiter().wait(
            for: [XCTNSPredicateExpectation(predicate: predicate, object: self)], timeout: timeout) == .completed
    }

    var hasKeyboardFocus: Bool {
        (value(forKey: "hasKeyboardFocus") as? Bool) ?? false
    }

    /// What the field shows now. An empty field reports its placeholder as its value, so a value equal to the
    /// placeholder is ambiguous: it may be placeholder or real text that happens to match it.
    var shownText: String { (value as? String) ?? "" }

    /// Gives a slow runner up to two seconds to show the effect of the deletions just sent: the value is read from
    /// the accessibility tree, which can trail the keyboard, so reading it at once can see "http" left of a cleared
    /// address.
    @MainActor
    private func awaitCleared() {
        let deadline = Date().addingTimeInterval(2)
        while Date() < deadline && !shownText.isEmpty && shownText != placeholderValue {
            Thread.sleep(forTimeInterval: 0.25)
        }
    }

    /// Gives a slow runner up to `timeout` seconds to show exactly `text`. The value is read from the accessibility
    /// tree, which trails the keyboard, so a read right after `typeText` can see a half-typed value that is later
    /// correct. Returns whether the field showed the text; it never passes when the text never arrives.
    @MainActor
    func awaitShownText(_ text: String, timeout: TimeInterval = 4) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while shownText != text && Date() < deadline {
            Thread.sleep(forTimeInterval: 0.25)
        }
        return shownText == text
    }

    /// Focuses the field, types the text and checks the field now holds exactly the intended text. A slow runner can
    /// drop a keystroke ("fctory"), so a mismatch that persists for a few seconds is cleared and typed again up to
    /// twice before it fails.
    @MainActor
    func enterText(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        focus(file: file, line: line)
        typeText(text)
        for _ in 0..<2 where !awaitShownText(text) {
            clearText(file: file, line: line)
            typeText(text)
        }
        _ = awaitShownText(text)
        XCTAssertEqual(shownText, text, "Typing into \(identifier) produced the wrong text", file: file, line: line)
    }

    /// Replaces whatever the field holds.
    @MainActor
    func clearAndTypeText(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        clearText(file: file, line: line)
        enterText(text, file: file, line: line)
    }

    /// Empties the field. The caret is first moved to the end (the focus tap may land mid-text), then more delete keys
    /// than characters are sent. Deleting is repeated until the field is empty or shows only its placeholder; the
    /// placeholder case is sent one deletion pass regardless, because it may be real text.
    @MainActor
    func clearText(file: StaticString = #filePath, line: UInt = #line) {
        focus(file: file, line: line)
        var passes = 0
        repeat {
            passes += 1
            let length = shownText.count
            coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
            typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: length + 5))
            awaitCleared()
        } while passes < 6 && !shownText.isEmpty && shownText != placeholderValue
        let left = shownText
        XCTAssertTrue(
            left.isEmpty || left == placeholderValue, "Clearing \(identifier) left \"\(left)\" behind", file: file,
            line: line)
    }
}
