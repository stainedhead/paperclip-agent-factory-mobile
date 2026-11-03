import PaperclipKit
import SwiftUI

/// Screen 11. It renders `AgentDetailState` and forwards intents. The offered actions, the owner-only budget and the
/// Face ID on Pause are the shared holder's.
struct AgentDetailView: View {
    let environment: AppEnvironment
    let id: String

    var body: some View {
        AgentDetailScreen(model: environment.agentDetailModel(id: id), environment: environment)
    }
}

private struct AgentDetailScreen: View {
    let model: AgentDetailModel
    let environment: AppEnvironment
    @State private var showsBudgetForm = false

    private var state: AgentDetailState { model.state }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if state.isOffline && state.phase == .content {
                    OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                }
                if state.phase == .content, let agent = state.agent { content(agent) } else { unavailable }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("agent-detail-screen")
        }
        .background(Palette.background.color)
        .navigationTitle(state.agent?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .refreshable {
            model.refresh()
            await settleRefresh { false }
        }
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .sensoryFeedback(trigger: state.isBusy) { old, new in
            guard old, !new else { return nil }
            return state.feedback == nil ? .success : .error
        }
        .sheet(isPresented: pauseBinding) { pauseSheet }
        .sheet(isPresented: $showsBudgetForm) { BudgetForm(model: model) }
    }

    // MARK: - Content

    @ViewBuilder private func content(_ agent: AgentRowUi) -> some View {
        let presentation = AgentRowPresentation(row: agent, referenceDate: environment.referenceDate)
        header(agent, presentation)
        if let feedback = state.feedback { FeedbackBanner(feedback: feedback) { model.dismissMessage() } }
        if let error = state.error {
            ErrorBanner(
                error: error, onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
        }
        actions
        work
        spend
        recentRuns
    }

    private func header(_ agent: AgentRowUi, _ presentation: AgentRowPresentation) -> some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            VStack(alignment: .leading, spacing: Spacing.hairline) {
                Text(agent.name).font(Typography.title).accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("agent-name")
                Text(AgentDetailLabel.role.text + ": " + agent.role.capitalized)
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("agent-role")
            }
            StatusPill(text: presentation.statusLine, tone: pillTone(presentation.tone))
            if let manager = state.reportsToName {
                Text(AgentDetailLabel.reportsTo.text + ": " + manager)
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("agent-reports-to")
            }
            UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
        }
    }

    private func pillTone(_ tone: StatusTone) -> StatusPill.Tone {
        switch tone {
        case .success: .success
        case .warn: .warn
        case .critical: .critical
        case .neutral: .neutral
        }
    }

    // MARK: - Actions

    @ViewBuilder private var actions: some View {
        if !state.actions.isEmpty {
            HStack(spacing: Spacing.medium) {
                ForEach(state.actions, id: \.self) { action in
                    Button {
                        perform(action)
                    } label: {
                        HStack(spacing: Spacing.small) {
                            Text(L10n.text(action))
                            if action == .pause { Image(systemName: "lock.fill").accessibilityHidden(true) }
                        }
                    }
                    .buttonStyle(.app(action == .pause ? .destructive : .secondary))
                    .accessibilityLabel(L10n.text(action))
                    .accessibilityIdentifier("agent-detail-\(action)")
                }
            }
        } else if let reason = state.actionsDisabledReason {
            DisabledReasonView(reason: reason)
        }
    }

    private func perform(_ action: AgentActionUi) {
        switch action {
        case .pause: model.pause()
        case .resume: model.resume()
        case .runNow: model.runNow()
        }
    }

    // MARK: - Work, spend, runs

    private var work: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: AgentDetailLabel.currentIssue.text)
            if let issue = state.currentIssue {
                Button {
                    environment.router.push(.issue(id: issue.id), in: environment.router.selectedTab)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: Spacing.hairline) {
                            Text(issue.identifier).font(Typography.label)
                                .foregroundStyle(Palette.textSecondary.color)
                            Text(issue.title).font(Typography.body).foregroundStyle(Palette.textPrimary.color)
                                .multilineTextAlignment(.leading)
                        }
                        Spacer(minLength: Spacing.medium)
                        Image(systemName: "chevron.right").font(Typography.micro)
                            .foregroundStyle(Palette.textSecondary.color).accessibilityHidden(true)
                    }
                    .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                    .cardSurface()
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(issue.identifier). \(issue.title)")
                .accessibilityIdentifier("agent-current-issue")
            } else {
                Text(AgentDetailLabel.idleNow.text).font(Typography.body)
                    .foregroundStyle(Palette.textSecondary.color).cardSurface()
                    .accessibilityIdentifier("agent-idle-note")
            }
            if let run = state.currentRun {
                RunListRow(
                    row: run, referenceDate: environment.referenceDate, identifier: "agent-current-run"
                ) { environment.router.push(.run(id: run.runId), in: environment.router.selectedTab) }
                .padding(.horizontal, Spacing.card)
                .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
                .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
            }
        }
    }

    private var spend: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: AgentDetailLabel.monthSpend.text)
            VStack(alignment: .leading, spacing: Spacing.large) {
                if let spent = state.monthSpend {
                    Text(MoneyFormat.text(spent)).font(Typography.title).accessibilityIdentifier("agent-month-spend")
                }
                if let budget = state.monthlyBudget, budget.minorUnits > 0 {
                    SpendMeter(percent: Int(state.budgetPercentUsed))
                    Text(
                        AgentDetailLabel.budget.text + ": " + MoneyFormat.text(budget) + " \u{00B7} "
                            + AgentDetailLabel.budgetUsed.text(Int(state.budgetPercentUsed))
                    )
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("agent-budget")
                } else {
                    Text(AgentDetailLabel.noBudget.text).font(Typography.secondary)
                        .foregroundStyle(Palette.textSecondary.color).accessibilityIdentifier("agent-budget")
                }
                budgetButton
            }
            .cardSurface()
        }
    }

    @ViewBuilder private var budgetButton: some View {
        if state.canSetBudget {
            Button(AgentDetailLabel.setBudget.text) { showsBudgetForm = true }
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("agent-set-budget")
        } else if let reason = state.budgetDisabledReason {
            DisabledReasonView(reason: reason)
        }
    }

    private var recentRuns: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: AgentDetailLabel.recentRuns.text)
            if state.recentRuns.isEmpty {
                Text(AgentDetailLabel.noRuns.text).font(Typography.body)
                    .foregroundStyle(Palette.textSecondary.color).cardSurface()
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(state.recentRuns.enumerated()), id: \.element.runId) { index, run in
                        if index > 0 { HairlineDivider() }
                        RunListRow(
                            row: run, referenceDate: environment.referenceDate, identifier: "agent-run-\(run.runId)"
                        ) { environment.router.push(.run(id: run.runId), in: environment.router.selectedTab) }
                    }
                }
                .padding(.horizontal, Spacing.card)
                .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
                .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("agent-recent-runs")
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }

    // MARK: - Pause confirmation

    private var pauseBinding: Binding<Bool> {
        Binding(get: { state.confirmPause }, set: { if !$0 { model.cancelPause() } })
    }

    @ViewBuilder private var pauseSheet: some View {
        ConfirmDestructiveSheet(
            title: AgentsLabel.pauseTitle.text(state.agent?.name ?? ""), message: AgentsLabel.pauseMessage.text,
            confirmTitle: AgentsLabel.pauseConfirm.text, cancelTitle: AgentsLabel.pauseKeep.text,
            onConfirm: { model.confirmPause() }, onCancel: { model.cancelPause() })
    }
}

/// "Set monthly budget" for an owner: one amount in dollars. The shared holder validates and sends it.
private struct BudgetForm: View {
    let model: AgentDetailModel
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""

    private var state: AgentDetailState { model.state }

    private var currency: String { state.monthlyBudget?.currency ?? "USD" }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            Text(AgentDetailLabel.budgetTitle.text(state.agent?.name ?? "")).font(Typography.title)
                .accessibilityAddTraits(.isHeader)
            Text(AgentDetailLabel.budgetHelp.text).font(Typography.body)
                .foregroundStyle(Palette.textSecondary.color)
            FormField(
                title: AgentDetailLabel.budgetField.text, placeholder: AgentDetailLabel.budgetPlaceholder.text,
                text: $text, issue: state.budgetAmountIssue.map { L10n.text($0) },
                keyboard: .decimalPad, capitalization: .never, identifier: "agent-budget-amount")
            Button(AgentDetailLabel.budgetSave.text) { model.saveBudget() }
                .buttonStyle(.app(.primary))
                .disabled(!state.canSaveBudget)
                .accessibilityIdentifier("agent-budget-save")
            Button(AgentDetailLabel.budgetCancel.text) { dismiss() }
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("agent-budget-cancel")
            Spacer(minLength: 0)
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("agent-budget-form")
        // Nothing typed is lost on a failure: the form closes only when shared code says the budget was saved.
        .onChange(of: text) { _, new in model.typeBudget(text: new, currency: currency) }
        .onChange(of: state.budgetSavedCount) { _, _ in dismiss() }
        .onAppear { model.typeBudget(text: "", currency: currency) }
    }
}
