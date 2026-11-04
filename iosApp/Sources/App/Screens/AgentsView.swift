import PaperclipKit
import SwiftUI

/// Screen 6, the root of the Agents tab. It renders `AgentsState` and forwards intents. Which buttons a row offers
/// (by status, role and connection), that Pause asks for confirmation and Face ID, and that a budget-paused agent
/// cannot simply be resumed are all the shared holder's; the view lays the rows out and links onward.
struct AgentsView: View {
    let environment: AppEnvironment

    var body: some View {
        AgentsScreen(model: environment.agentsModel(), environment: environment)
            .navigationTitle(ShellLabel.tabAgents.text)
    }
}

private enum AgentFilter {
    case all
    case running
    case attention

    func includes(_ group: AgentGroupUi) -> Bool {
        switch self {
        case .all: true
        case .running: group == .running
        case .attention: group == .needsAttention
        }
    }
}

private struct AgentsScreen: View {
    let model: AgentsModel
    let environment: AppEnvironment
    @State private var filter = AgentFilter.all

    private var state: AgentsState { model.state }
    private var rows: [AgentRowUi] { state.sections.flatMap { $0.rows } }

    var body: some View {
        ScrollView {
            // Not lazy: lazy rows are realised and dropped as the text size changes (audit: unsupported Dynamic Type).
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                header
                if state.phase == .content { content } else { unavailable }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
        }
        .background(Palette.background.color)
        .refreshable {
            model.refresh()
            await settleRefresh { false }
        }
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .sensoryFeedback(trigger: state.busyAgentId) { old, new in
            guard old != nil, new == nil else { return nil }
            return state.feedback == nil ? .success : .error
        }
        .sheet(isPresented: pauseBinding) { pauseSheet }
        .accessibilityIdentifier("agents-root")
    }

    // MARK: - Header

    @ViewBuilder private var header: some View {
        FactorySwitcher(environment: environment, identifier: "agents-switcher")
        if state.phase == .content {
            HStack(spacing: Spacing.medium) {
                Text(AgentsLabel.count.plural(rows.count))
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("agents-count")
                UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
            }
            if state.isOffline {
                OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
            }
            if let reason = state.actionsDisabledReason {
                DisabledReasonView(reason: reason)
            }
            if let feedback = state.feedback {
                FeedbackBanner(feedback: feedback) { model.dismissMessage() }
            }
            if let error = state.error {
                ErrorBanner(
                    error: error, onRetry: { model.refresh() },
                    onSignInAgain: { environment.router.select(.factories) })
            }
            chips
        }
    }

    private var chips: some View {
        let running = state.sections.first { $0.group == .running }?.rows.count ?? 0
        let attention = state.sections.first { $0.group == .needsAttention }?.rows.count ?? 0
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.medium) {
                FilterChip(
                    text: AgentsLabel.filterAll.text, isSelected: filter == .all, identifier: "agents-filter-all"
                ) { filter = .all }
                FilterChip(
                    text: AgentsLabel.filterRunning.text(running), isSelected: filter == .running,
                    identifier: "agents-filter-running"
                ) { filter = .running }
                FilterChip(
                    text: AgentsLabel.filterAttention.text(attention), isSelected: filter == .attention,
                    identifier: "agents-filter-attention"
                ) { filter = .attention }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(AgentsLabel.filtersLabel.text)
        .accessibilityIdentifier("agents-filters")
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        let sections = state.sections.filter { filter.includes($0.group) }
        if rows.isEmpty {
            EmptyState(
                symbol: "person.2", title: AgentsLabel.emptyTitle.text, message: AgentsLabel.emptyMessage.text
            ).frame(minHeight: 280)
        } else if sections.isEmpty {
            Text(AgentsLabel.filterEmpty.text).font(Typography.body)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityIdentifier("agents-filter-empty")
        }
        ForEach(sections, id: \.group) { section in
            VStack(alignment: .leading, spacing: Spacing.medium) {
                SectionHeader(title: L10n.text(section.group))
                    .accessibilityIdentifier("agents-section-\(section.group)")
                ForEach(section.rows, id: \.id) { row in
                    AgentRowCard(
                        row: row, state: state,
                        onOpen: { environment.router.push(.agent(id: row.id), in: .agents) },
                        onViewRun: { run in environment.router.push(.run(id: run), in: .agents) },
                        model: model)
                }
            }
        }
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }

    // MARK: - Pause confirmation

    private var pauseBinding: Binding<Bool> {
        Binding(get: { state.confirmPauseAgentId != nil }, set: { if !$0 { model.cancelPause() } })
    }

    @ViewBuilder private var pauseSheet: some View {
        let name = rows.first { $0.id == state.confirmPauseAgentId }?.name ?? ""
        ConfirmDestructiveSheet(
            title: AgentsLabel.pauseTitle.text(name), message: AgentsLabel.pauseMessage.text,
            confirmTitle: AgentsLabel.pauseConfirm.text, cancelTitle: AgentsLabel.pauseKeep.text,
            onConfirm: { model.confirmPause() }, onCancel: { model.cancelPause() })
    }
}

/// One agent: who, in what state, what it is doing, and the buttons this person may use right now.
private struct AgentRowCard: View {
    let row: AgentRowUi
    let state: AgentsState
    let onOpen: () -> Void
    let onViewRun: (String) -> Void
    let model: AgentsModel

    var body: some View {
        TickingReference(every: LiveClock.ageTick) { now in
            card(AgentRowPresentation(row: row, referenceDate: now))
        }
    }

    private func card(_ presentation: AgentRowPresentation) -> some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            Button(action: onOpen) {
                HStack(alignment: .top, spacing: Spacing.extraLarge) {
                    AgentAvatar(name: row.name)
                    VStack(alignment: .leading, spacing: Spacing.hairline) {
                        Text(row.name).font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
                        Text(presentation.statusLine).font(Typography.secondary)
                            .foregroundStyle(
                                (presentation.tone == .neutral ? Palette.textSecondary : presentation.tone.foreground)
                                    .color
                            )
                            .multilineTextAlignment(.leading)
                    }
                    Spacer(minLength: Spacing.medium)
                    if state.busyAgentId == row.id {
                        ProgressView().accessibilityLabel(AgentsLabel.working.text)
                    } else {
                        Image(systemName: "chevron.right").font(Typography.micro)
                            .foregroundStyle(Palette.textSecondary.color).accessibilityHidden(true)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(row.name). \(presentation.statusLine)")
            .accessibilityHint(AgentsLabel.openHint.text)
            .accessibilityIdentifier("agent-row-\(row.id)")
            buttons
        }
        .cardSurface()
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("agent-card-\(row.id)")
    }

    /// The buttons: the state's actions, View run when it is running, Budget for an owner. While the agent is running a
    /// run, View run takes the place of Run now (the mock's CTO row).
    @ViewBuilder private var buttons: some View {
        let live = row.currentRun.flatMap { $0.status.isLive ? $0 : nil }
        let actions = row.visibleActions
        let showsBudget = state.canSetBudget && row.hasBudget
        if !actions.isEmpty || live != nil || showsBudget {
            FlowLayout(spacing: Spacing.medium) {
                ForEach(actions.filter { $0 != .pause }, id: \.self) { action in
                    RowButton(
                        title: L10n.text(action), symbol: nil, identifier: "agent-\(row.id)-\(action)"
                    ) { perform(action) }
                }
                if let live {
                    RowButton(title: AgentsLabel.viewRun.text, symbol: nil, identifier: "agent-\(row.id)-viewRun") {
                        onViewRun(live.runId)
                    }
                }
                if showsBudget {
                    RowButton(title: AgentsLabel.budget.text, symbol: nil, identifier: "agent-\(row.id)-budget") {
                        onOpen()
                    }
                }
                if actions.contains(.pause) {
                    RowButton(
                        title: L10n.text(AgentActionUi.pause), symbol: "lock.fill", identifier: "agent-\(row.id)-pause"
                    ) { perform(.pause) }
                }
            }
        }
    }

    private func perform(_ action: AgentActionUi) {
        switch action {
        case .pause: model.pause(row.id)
        case .resume: model.resume(row.id)
        case .runNow: model.runNow(row.id)
        }
    }
}

/// A 44 pt pill button for a row. The lock marks the one that asks Face ID.
struct RowButton: View {
    let title: String
    let symbol: String?
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.small) {
                Text(title)
                if let symbol { Image(systemName: symbol).font(Typography.micro).accessibilityHidden(true) }
            }
            .font(Typography.button)
            .padding(.horizontal, Spacing.roomy)
            .frame(minHeight: Spacing.minimumTarget)
            .foregroundStyle(Palette.textPrimary.color)
            .background(Palette.surface.color, in: Capsule())
            .overlay(Capsule().strokeBorder(Palette.border.color))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityIdentifier(identifier)
    }
}
