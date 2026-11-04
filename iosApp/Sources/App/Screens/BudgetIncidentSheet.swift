import PaperclipKit
import SwiftUI

/// Screen 12, presented as a sheet from the Inbox card's "Raise and resume" (or Keep paused's details). It renders
/// `BudgetIncidentState` and forwards intents. Who may keep paused or raise, the amount rule (it must exceed the
/// observed spend), Face ID for a raise (asked inside the shared use case) and the server's own refusal reason are
/// the shared holder's; the view only lays them out.
struct BudgetIncidentSheet: View {
    let environment: AppEnvironment
    let id: String

    var body: some View {
        NavigationStack {
            BudgetScreen(model: environment.budgetModel(id: id), environment: environment)
                .navigationTitle(BudgetLabel.navTitle.text)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            environment.router.dismissSheet()
                        } label: {
                            Label(BudgetLabel.close.text, systemImage: "xmark")
                        }
                        .accessibilityIdentifier("budget-close")
                    }
                }
        }
        .presentationDetents([.large])
    }
}

private struct BudgetScreen: View {
    let model: BudgetModel
    let environment: AppEnvironment
    @State private var amountText = ""
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var state: BudgetIncidentState { model.state }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                scroll
                    .onChange(of: amountText) { _, _ in
                        withAnimation(Motion.animation(reduceMotion: reduceMotion)) {
                            proxy.scrollTo("budget-amount-anchor", anchor: .bottom)
                        }
                    }
            }
            submitBar
        }
        .background(Palette.background.color)
        .sensoryFeedback(.success, trigger: state.outcome)
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
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
            .accessibilityIdentifier("budget-screen")
        }
        .scrollDismissesKeyboard(.interactively)
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        header
        if let feedback = state.feedback { FeedbackBanner(feedback: feedback) { model.dismissMessage() } }
        if let error = state.error {
            ErrorBanner(
                error: error, onRetry: { model.refresh() }, onSignInAgain: signInAgain)
        }
        if let outcome = state.outcome {
            ResultCard(
                title: L10n.text(outcome), tone: outcome == .noLongerActive ? .neutral : .success,
                identifier: "budget-outcome"
            ) {
                EmptyView()
            }
        } else {
            choices
            if state.choice == .raiseAndResume { amountField.id("budget-amount-anchor") }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            HStack(spacing: Spacing.medium) {
                if let threshold = state.threshold {
                    StatusPill(text: L10n.text(threshold), tone: threshold == .hard ? .critical : .warn)
                }
            }
            Text(state.scopeName)
                .font(Typography.title)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("budget-scope")
            if !state.scopeType.isEmpty {
                Text(DecisionPresentation.prettify(state.scopeType).capitalized)
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
            }
            if let observed = state.observed, let limit = state.limit {
                ProgressView(value: BudgetPresentation.gauge(percent: Int(state.observedPercent)))
                    .tint((state.observedPercent >= 100 ? Palette.destructive : Palette.accent).color)
                    .accessibilityHidden(true)
                Text(
                    BudgetPresentation.usage(
                        observed: observed, limit: limit, percent: Int(state.observedPercent))
                )
                .font(Typography.body)
                .accessibilityIdentifier("budget-usage")
            }
            UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
        }
    }

    private var choices: some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            SectionHeader(title: BudgetLabel.choiceHeader.text)
            choiceCard(
                .keepPaused, help: BudgetLabel.keepPausedHelp.text, enabled: state.canKeepPaused, locked: false,
                identifier: "budget-choice-keep")
            choiceCard(
                .raiseAndResume, help: BudgetLabel.raiseHelp.text, enabled: state.canRaise, locked: true,
                identifier: "budget-choice-raise")
            if let reason = state.raiseDisabledReason, !state.canRaise { DisabledReasonView(reason: reason) }
        }
    }

    private func choiceCard(
        _ choice: BudgetChoice, help: String, enabled: Bool, locked: Bool, identifier: String
    ) -> some View {
        let selected = state.choice == choice
        return Button {
            model.choose(choice)
        } label: {
            HStack(alignment: .top, spacing: Spacing.extraLarge) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.title3)
                    .foregroundStyle((selected ? Palette.accent : Palette.textSecondary).color)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.small) {
                    HStack(spacing: Spacing.medium) {
                        Text(L10n.text(choice)).font(Typography.cardTitle)
                            .foregroundStyle((enabled ? Palette.textPrimary : Palette.textSecondary).color)
                        if locked {
                            Image(systemName: "lock.fill").font(Typography.secondary).accessibilityHidden(true)
                        }
                    }
                    Text(help).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
            }
            .padding(Spacing.card)
            .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
            .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card)
                    .strokeBorder((selected ? Palette.accent : Palette.hairline).color, lineWidth: selected ? 2 : 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.cardSelect)
        .accessibilityElement(children: .ignore)
        .disabled(!enabled)
        .accessibilityLabel("\(L10n.text(choice)). \(help)")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier(identifier)
    }

    private var amountField: some View {
        FormField(
            title: BudgetLabel.amountLabel.text, placeholder: BudgetLabel.amountPlaceholder.text,
            text: amountBinding,
            help: state.observed.map { BudgetPresentation.amountHelp(observed: $0) },
            issue: state.amountIssue.map { L10n.text($0) }, keyboard: .decimalPad, capitalization: .never,
            identifier: "budget-amount")
    }

    /// The text lives here while it is typed; every change goes to the shared holder, which reads it and validates it.
    private var amountBinding: Binding<String> {
        Binding(
            get: { amountText },
            set: {
                amountText = $0
                model.setAmount(text: $0)
            })
    }

    /// What Raise says on the button: the amount that will be sent, once there is one (it is also what Face ID is for).
    private var raiseTitle: String {
        guard let minor = state.amountMinorUnits?.int64Value, let currency = state.observed?.currency else {
            return BudgetLabel.submitRaise.text
        }
        return BudgetLabel.submitRaiseAmount.text(MoneyFormat.text(MoneyUi(minorUnits: minor, currency: currency)))
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: signInAgain)
    }

    /// The sheet sits over the tab bar: it closes first, then the Factories tab shows where to sign in again.
    private func signInAgain() {
        environment.router.dismissSheet()
        environment.router.select(.factories)
    }

    // MARK: - Submit

    @ViewBuilder private var submitBar: some View {
        if state.phase == .content && state.outcome == nil {
            VStack(spacing: Spacing.large) {
                if let reason = state.actionsDisabledReason { DisabledReasonView(reason: reason) }
                Button {
                    model.submit()
                } label: {
                    if state.isSubmitting {
                        Label(BudgetLabel.working.text, systemImage: "hourglass")
                    } else if state.submitNeedsDeviceAuth {
                        Label(raiseTitle, systemImage: "lock.fill")
                    } else if state.choice == nil {
                        Text(BudgetLabel.submitChoose.text)
                    } else {
                        Text(BudgetLabel.submitKeep.text)
                    }
                }
                .buttonStyle(.app(.primary))
                .disabled(!state.canSubmit)
                .accessibilityIdentifier("budget-submit")
            }
            .padding(.horizontal, Spacing.page)
            .padding(.top, Spacing.extraLarge)
            .padding(.bottom, Spacing.medium)
            .background(Palette.surface.color)
            .overlay(alignment: .top) { HairlineDivider() }
        }
    }
}
