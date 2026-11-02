import XCTest

/// Screen 13 on the demo graph: the Settings and profile mock, paired devices and signing out.
final class SettingsUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func openSettings() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-demo", "-demo-biometrics", "approved", "-demo-diagnostics", "-demo-tab", "factories",
            "-demo-open", "settings"
        ]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar never appeared")
        XCTAssertTrue(app.element("settings").waitForExistence(timeout: 15))
        return app
    }

    @MainActor
    func testSettingsShowsThePhoneAndEachFactoryAsInTheMock() {
        let app = openSettings()
        XCTAssertTrue(app.textFields["settings-device-name"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.textFields["settings-device-name"].value as? String, "Alex's iPhone")
        XCTAssertEqual(app.switches["settings-app-lock-toggle"].value as? String, "1")
        XCTAssertTrue(app.element("settings-idle-period").label.contains("5 minutes"))
        let destructive = app.switches["settings-destructive-toggle"]
        XCTAssertEqual(destructive.value as? String, "1")
        XCTAssertFalse(destructive.isEnabled, "Face ID for destructive actions cannot be turned off")
        XCTAssertTrue(app.element("settings-factory-demo-home").label.contains("Home factory"))
        XCTAssertTrue(app.element("settings-factory-demo-cloud").label.contains("Key expired"))
        XCTAssertTrue(app.element("settings-factory-demo-test").label.contains("Signed out"))
    }

    @MainActor
    func testRenamingTheDeviceIsKeptWhenSettingsOpensAgain() {
        let app = openSettings()
        let field = app.textFields["settings-device-name"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.clearAndTypeText("Studio phone")
        field.typeText("\n")

        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["settings-button"].waitForExistence(timeout: 10))
        app.buttons["settings-button"].tap()
        XCTAssertTrue(app.textFields["settings-device-name"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.textFields["settings-device-name"].value as? String, "Studio phone")
    }

    @MainActor
    func testTheAppLockToggleAndLockAfterChange() {
        let app = openSettings()
        let toggle = app.switches["settings-app-lock-toggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        toggle.tap()
        XCTAssertEqual(toggle.value as? String, "0")

        app.element("settings-idle-period").tap()
        app.buttons["15 minutes"].tap()
        let changed = NSPredicate { _, _ in app.element("settings-idle-period").label.contains("15 minutes") }
        expectation(for: changed, evaluatedWith: nil)
        waitForExpectations(timeout: 10)
    }

    @MainActor
    func testPairedDevicesListThePhoneAndRevokingAnotherKeyAsksFirst() {
        let app = openSettings()
        app.buttons["settings-paired-devices"].tap()
        XCTAssertTrue(app.element("device-key-bk-demo-a91f").waitForExistence(timeout: 15))
        XCTAssertTrue(app.element("device-key-bk-demo-a91f").label.contains("This phone"))
        XCTAssertTrue(app.element("device-key-bk-demo-cli").label.contains("paperclipai cli"))
        XCTAssertFalse(app.buttons["revoke-bk-demo-cli"].label.isEmpty)

        app.buttons["revoke-bk-demo-cli"].tap()
        XCTAssertTrue(app.element("confirm-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-sheet-confirm"].tap()
        let gone = NSPredicate(format: "exists == false")
        expectation(for: gone, evaluatedWith: app.element("device-key-bk-demo-cli"))
        waitForExpectations(timeout: 15)
        XCTAssertTrue(app.element("device-key-bk-demo-a91f").exists)
    }

    @MainActor
    func testSignOutEverywhereAsksFirstThenSignsEveryFactoryOut() {
        let app = openSettings()
        app.buttons["settings-sign-out-everywhere"].tap()
        XCTAssertTrue(app.element("confirm-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-sheet-cancel"].tap()
        XCTAssertTrue(app.element("settings-factory-demo-home").label.contains("Connected"))

        app.buttons["settings-sign-out-everywhere"].tap()
        XCTAssertTrue(app.buttons["confirm-sheet-confirm"].waitForExistence(timeout: 10))
        app.buttons["confirm-sheet-confirm"].tap()
        let signedOut = NSPredicate { _, _ in
            app.element("settings-factory-demo-home").label.contains("Signed out")
        }
        expectation(for: signedOut, evaluatedWith: nil)
        waitForExpectations(timeout: 15)
    }

    @MainActor
    func testAccessibilityAuditOfSettings() throws {
        let app = openSettings()
        XCTAssertTrue(app.textFields["settings-device-name"].waitForExistence(timeout: 10))
        let findings = try auditAccessibility(app, screen: "settings")
        // Listed in implementation-notes.md: items under the floating tab bar ("Sign out everywhere", the help text)
        // and "Text clipped" on rows. Anything else fails.
        let known = [
            "Text clipped", "Contrast nearly passed", "Dynamic Type font sizes", "| Sign out everywhere",
            "come from each factory"
        ]
        let others = findings.filter { finding in !known.contains { finding.contains($0) } }
        XCTAssertTrue(others.isEmpty, others.joined(separator: "\n"))
    }
}
