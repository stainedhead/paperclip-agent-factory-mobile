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
    @Test func everyFactoryStatusHasText() { check(FactoryStatusUi.allCases) }
    @Test func everyProbeStatusHasText() { check(ProbeStatus.allCases) }
    @Test func everyProbeRefusalHasText() { check(ProbeRefusal.allCases) }
    @Test func everyPairingStatusHasText() { check(PairingStatus.allCases) }
    @Test func everyInstanceNameIssueHasText() { check(InstanceNameIssue.allCases) }
    @Test func everyExposureHasText() { check(Exposure.allCases) }
    @Test func everyRoleHasText() { check(RoleUi.allCases) }
    @Test func everyRemovalEffectHasText() { check(RemovalEffect.allCases) }
    @Test func everyServerKeyOutcomeHasText() { check(ServerKeyOutcome.allCases) }
    @Test func everyIdlePeriodHasText() { check(IdlePeriod.allCases) }
    @Test func everyPairedDevicesStatusHasText() { check(PairedDevicesStatus.allCases) }

    @Test func everySourceKindHasACardAndAChipLabel() {
        check(SourceKindUi.allCases)
        for kind in SourceKindUi.allCases {
            #expect(L10n.resolve("kindPlural.\(kind)") != "kindPlural.\(kind)", "no chip label for \(kind)")
        }
    }
    @Test func everySnoozePresetHasText() { check(SnoozePresetUi.allCases) }
    @Test func everyInboxGroupHasText() { check(InboxGroupUi.allCases) }
    @Test func everyInlineActionHasText() { check(InlineActionUi.allCases) }
    @Test func everyInboxLabelHasText() { check(InboxLabel.allCases) }

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
