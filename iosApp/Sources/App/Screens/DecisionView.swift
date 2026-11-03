import PaperclipKit
import SwiftUI

/// Screen 2. It renders `DecisionState` and forwards intents. Which options exist, what is enabled, when Face ID is
/// asked (inside the shared use case, so a cancelled prompt arrives as `feedback.deviceAuth`) and what the final state
/// is are all the shared holder's; the view only lays them out.
struct DecisionView: View {
    let environment: AppEnvironment
    let id: String

    var body: some View {
        DecisionScreen(model: environment.decisionModel(id: id), environment: environment, id: id)
    }
}

private struct DecisionScreen: View {
    let model: DecisionModel
    let environment: AppEnvironment
    let id: String
    @State private var typed: [String: String] = [:]

    private var state: DecisionState { model.state }

    var body: some View {
        VStack(spacing: 0) {
            scroll
            actionBar
        }
        .background(Palette.background.color)
        .navigationTitle(DecisionLabel.navTitle.text)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { snoozeItem }
        .toolbar(.hidden, for: .tabBar)
        .sensoryFeedback(.success, trigger: state.finalState != nil)
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .onChange(of: state.isSnoozed) { _, snoozed in
            if snoozed { environment.router.pop(environment.router.selectedTab) }
        }
    }

    private var scroll: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if state.isOffline && state.phase == .content {
                    OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                }
                if state.phase == .content { content } else { unavailable }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("decision-screen")
        }
        .scrollDismissesKeyboard(.interactively)
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        header
        if let feedback = state.feedback, feedback.inputId == nil {
            FeedbackBanner(feedback: feedback) { model.dismissMessage() }
        }
        if let error = state.error {
            ErrorBanner(
                error: error, onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
        }
        if let final = state.finalState {
            finalCard(final)
        } else {
            optionsSection
            inputsSection
        }
        if let execution = state.execution { DecisionExecutionSection(execution: execution) }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.small) {
            Text(
                [L10n.text(SourceKindUi.decision), deadline].compactMap { $0 }.joined(separator: " \u{00B7} ")
                    .uppercased()
            )
            .font(Typography.label)
            .foregroundStyle(Palette.destructive.color)
            .accessibilityIdentifier("decision-kind-line")
            Text(state.title)
                .font(Typography.title)
                .multilineTextAlignment(.leading)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("decision-title")
            if let meta {
                Text(meta)
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("decision-meta")
            }
            if let expiry = state.expiresAtEpochMillis?.int64Value, state.finalState == nil {
                Text(DecisionPresentation.expiryText(epochMillis: expiry))
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.warn.color)
                    .accessibilityIdentifier("decision-expires")
            }
            if !state.body.isEmpty {
                Text(state.body)
                    .font(Typography.body)
                    .padding(.top, Spacing.small)
                    .accessibilityIdentifier("decision-body")
            }
            UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
        }
    }

    private var deadline: String? { DecisionPresentation.decideByText(state.decideBy) }

    private var meta: String? {
        let proposed = state.originAgentName.map { DecisionLabel.proposedBy.text($0) }
        return [proposed, environment.decisionIdentifier(for: id)].compactMap { $0 }.joined(separator: " \u{00B7} ")
            .nonEmpty
    }

    // MARK: - Options

    private var optionsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            SectionHeader(title: DecisionLabel.options.text)
            ForEach(state.options, id: \.id) { option in
                DecisionOptionCard(
                    option: option, isSelected: state.selectedOptionId == option.id, isDisabled: state.isSubmitting,
                    onSelect: { model.select(option.id) })
            }
        }
    }

    // MARK: - Inputs

    @ViewBuilder private var inputsSection: some View {
        ForEach(state.inputs, id: \.id) { input in
            let issue = inputIssue(input)
            VStack(alignment: .leading, spacing: Spacing.small) {
                FormField(
                    title: input.label, placeholder: input.placeholder ?? "",
                    text: binding(for: input), help: DecisionPresentation.counter(input), issue: issue,
                    capitalization: .sentences, isDisabled: state.isSubmitting || state.actionsDisabledReason != nil,
                    identifier: "decision-input-\(input.id)")
            }
        }
    }

    private func inputIssue(_ input: InputUi) -> String? {
        guard let feedback = state.feedback, feedback.inputId == input.id, let invalid = feedback.invalid else {
            return nil
        }
        return L10n.text(invalid)
    }

    /// The text lives here while it is typed, so a keystroke never waits for the shared state to come round.
    private func binding(for input: InputUi) -> Binding<String> {
        Binding(
            get: { typed[input.id] ?? input.value },
            set: {
                typed[input.id] = $0
                model.setInput(input.id, $0)
            })
    }

    // MARK: - Result

    private func finalCard(_ final: DecisionFinalUi) -> some View {
        ResultCard(
            title: DecisionLabel.chosen.text, tone: final.kind == .decided ? .success : .neutral,
            identifier: "decision-final"
        ) {
            Text(DecisionPresentation.finalText(final))
        }
    }

    // MARK: - Bars

    @ToolbarContentBuilder private var snoozeItem: some ToolbarContent {
        if state.canSnooze {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    ForEach(state.snoozeOptions, id: \.self) { preset in
                        Button(L10n.text(preset)) { model.snooze(preset) }
                            .accessibilityIdentifier("decision-snooze-\(preset)")
                    }
                } label: {
                    Label(DecisionLabel.snooze.text, systemImage: "clock")
                }
                .accessibilityIdentifier("decision-snooze")
            }
        }
    }

    private var confirmSpokenLabel: String {
        if state.isSubmitting { return DecisionLabel.working.text }
        let base = DecisionLabel.confirm.text
        return state.confirmNeedsDeviceAuth ? "\(base). \(DecisionLabel.asksFaceID.text)" : base
    }

    @ViewBuilder private var actionBar: some View {
        if state.phase == .content && state.finalState == nil {
            VStack(spacing: Spacing.large) {
                if let reason = state.actionsDisabledReason {
                    DisabledReasonView(reason: reason)
                }
                if let chosen = state.options.first(where: { $0.id == state.selectedOptionId }) {
                    // The bar restates what the tap will do, so the choice is in front of the button that confirms it.
                    Text("\(DecisionLabel.selected.text): \(chosen.label)")
                        .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("decision-chosen")
                }
                AdaptiveStack(spacing: Spacing.large) {
                    Button(DecisionLabel.dismiss.text) { model.dismiss() }
                        .buttonStyle(.app(.secondary))
                        .disabled(!state.canDismiss)
                        .accessibilityIdentifier("decision-dismiss")
                    Button {
                        model.confirm()
                    } label: {
                        if state.isSubmitting {
                            Label(DecisionLabel.working.text, systemImage: "hourglass")
                        } else if state.confirmNeedsDeviceAuth {
                            Label(DecisionLabel.confirmDestructive.text, systemImage: "lock.fill")
                        } else {
                            Text(DecisionLabel.confirm.text)
                        }
                    }
                    .buttonStyle(.app(.primary))
                    .disabled(!state.canConfirm)
                    .accessibilityLabel(confirmSpokenLabel)
                    .accessibilityIdentifier("decision-confirm")
                }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.top, Spacing.extraLarge)
            .padding(.bottom, Spacing.medium)
            .background(Palette.surface.color)
            .overlay(alignment: .top) { HairlineDivider() }
        }
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }
}

extension String {
    /// `nil` for an empty string, so an empty line is left out instead of drawn.
    var nonEmpty: String? { isEmpty ? nil : self }
}
