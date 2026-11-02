import PaperclipKit
import Testing

@testable import iosApp

/// UI-007: every key a shared state holder can hand the app, and every shell label, resolves to text.
struct LocalizationTests {
    private func check<K: CatalogKey>(_ keys: [K], sourceLocation: SourceLocation = #_sourceLocation) {
        #expect(!keys.isEmpty, "no keys were enumerated for \(K.catalogNamespace)", sourceLocation: sourceLocation)
        for key in keys {
            let text = L10n.text(key)
            #expect(!text.isEmpty, "\(key.catalogKey) is empty", sourceLocation: sourceLocation)
            #expect(
                text != key.catalogKey, "\(key.catalogKey) is not in Localizable.xcstrings",
                sourceLocation: sourceLocation)
        }
    }

    @Test func everyErrorKeyHasMessageText() { check(ErrorKey.allCases) }
    @Test func everyActionsDisabledReasonHasText() { check(ActionsDisabledReason.allCases) }
    @Test func everyAddressIssueHasText() { check(AddressIssue.allCases) }
    @Test func everyDeviceAuthIssueHasText() { check(DeviceAuthIssue.allCases) }
    @Test func everyEffectKindHasText() { check(EffectKindUi.allCases) }
    @Test func everyFeedbackKindHasText() { check(FeedbackKind.allCases) }
    @Test func everyInvalidKeyHasText() { check(InvalidKey.allCases) }
    @Test func everyShellLabelHasText() { check(ShellLabel.allCases) }

    @Test func keysFollowTheNamespaceDotCaseNameScheme() {
        #expect(ErrorKey.certificateInvalid.catalogKey == "error.certificateInvalid")
        #expect(ActionsDisabledReason.viewerRole.catalogKey == "disabled.viewerRole")
        #expect(AddressIssue.mustUseHttps.catalogKey == "address.mustUseHttps")
        #expect(ShellLabel.tabInbox.catalogKey == "label.tabInbox")
    }

    @Test func textTakesFormatArgumentsForParameterisedKeys() {
        let text = L10n.text(EffectKindUi.createIssue, "Add retries")
        #expect(text.contains("Add retries"))
        #expect(L10n.text(ShellLabel.firstRunWillConnect, "https://x.example.com").contains("https://x.example.com"))
    }

    @Test func anUnknownKeyIsVisibleAsItsKeyNotAsEmptyText() {
        #expect(L10n.resolve("no.such.key") == "no.such.key")
    }
}
