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
    @Test func everyDecisionLabelHasText() { check(DecisionLabel.allCases) }
    @Test func everyDecisionFinalKindHasText() { check(DecisionFinalKind.allCases) }
    @Test func everyEffectStatusHasText() { check(EffectStatusUi.allCases) }
    @Test func everyExecutionStatusHasText() { check(ExecutionStatusUi.allCases) }
    @Test func everyApprovalLabelHasText() { check(ApprovalLabel.allCases) }
    @Test func everyDetailLabelHasText() { check(DetailLabel.allCases) }
    @Test func everyApprovalTypeHasText() { check(ApprovalTypeUi.allCases) }
    @Test func everyApprovalStatusHasText() { check(ApprovalStatusUi.allCases) }
    @Test func everyApprovalActionHasText() { check(ApprovalActionUi.allCases) }
    @Test func everyNotePromptHasText() { check(NotePromptKind.allCases) }
    @MainActor @Test func everyApprovalStatusHasADoneSentence() {
        for status in ApprovalStatusUi.allCases {
            #expect(ApprovalPresentation.doneText(status) != "approvalDone.\(status)", "no sentence for \(status)")
        }
    }
    @Test func everyBudgetLabelHasText() { check(BudgetLabel.allCases) }
    @Test func everyBudgetThresholdHasText() { check(BudgetThresholdUi.allCases) }
    @Test func everyBudgetChoiceHasText() { check(BudgetChoice.allCases) }
    @Test func everyBudgetOutcomeHasText() { check(BudgetOutcomeUi.allCases) }
    @Test func everyAgentStatusHasText() { check(AgentStatusUi.allCases) }
    @Test func everyRunStatusHasText() { check(RunStatusUi.allCases) }
    @Test func everyAgentGroupHasText() { check(AgentGroupUi.allCases) }
    @Test func everyAgentActionHasText() { check(AgentActionUi.allCases) }
    @Test func everySpendWindowHasText() { check(SpendWindowUi.allCases) }
    @Test func everyFactoryLabelHasText() { check(FactoryLabel.allCases) }
    @Test func everyAgentsLabelHasText() { check(AgentsLabel.allCases) }
    @Test func everyAgentDetailLabelHasText() { check(AgentDetailLabel.allCases) }
    @Test func everyRunLabelHasText() { check(RunLabel.allCases) }
    @Test func everyDecideByKindHasText() { check(DecideByKindUi.allCases) }
    @Test func everyIssueStatusHasText() { check(IssueStatusUi.allCases) }
    @Test func everyIssuePriorityHasText() { check(IssuePriorityUi.allCases) }
    @Test func everyIssueFilterHasText() { check(IssueFilterUi.allCases) }
    @Test func everyIssueTabHasText() { check(IssueTab.allCases) }
    @Test func everyCommentAuthorHasText() { check(CommentAuthorUi.allCases) }
    @Test func everyInteractionKindHasText() { check(InteractionKindUi.allCases) }
    @Test func everyInteractionStatusHasText() { check(InteractionStatusUi.allCases) }
    @Test func everyInteractionReadOnlyReasonHasText() { check(InteractionReadOnlyUi.allCases) }
    @Test func everyInteractionPrimaryHasText() { check(InteractionPrimaryUi.allCases) }
    @Test func everyVerdictHasText() { check(VerdictUi.allCases) }
    @Test func everyWorkLabelHasText() { check(WorkLabel.allCases) }
    @Test func everyIssueLabelHasText() { check(IssueLabel.allCases) }

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

    /// B-08: a count reads right at one and at many, from the String Catalog's plural variations (never "1 keys").
    @Test func countsReadInTheSingularAndThePluralFromTheCatalog() {
        #expect(ShellLabel.settingsUnconfirmedRevokes.plural(1) == "1 key could not be confirmed as revoked on its server.")
        #expect(
            ShellLabel.settingsUnconfirmedRevokes.plural(2)
                == "2 keys could not be confirmed as revoked on their servers.")
        #expect(ShellLabel.settingsSignOutFailures.plural(1) == "1 factory could not be signed out on this phone.")
        #expect(ShellLabel.settingsSignOutFailures.plural(3) == "3 factories could not be signed out on this phone.")
        #expect(IssueLabel.questionsMore.plural(1) == "1 question")
        #expect(IssueLabel.questionsMore.plural(4) == "4 questions")
        #expect(ShellLabel.expiryDays.plural(1) == "Board key expires in 1 day")
        #expect(ShellLabel.expiryDays.plural(23) == "Board key expires in 23 days")
        #expect(ShellLabel.manageCount.plural(1) == "1 factory")
        #expect(ShellLabel.manageCount.plural(2) == "2 factories")
        #expect(WorkLabel.count.plural(1) == "1 issue")
        #expect(WorkLabel.count.plural(12) == "12 issues")
        #expect(AgentsLabel.count.plural(1) == "1 agent")
        #expect(AgentsLabel.count.plural(5) == "5 agents")
    }

    @Test func anUnknownKeyIsVisibleAsItsKeyNotAsEmptyText() {
        #expect(L10n.resolve("no.such.key") == "no.such.key")
    }
}
