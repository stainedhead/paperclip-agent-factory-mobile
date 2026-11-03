import PaperclipKit
import SwiftUI

/// Screen 5, the root of the Factory tab. It renders `FactoryHomeState` and forwards a refresh: the tiles, the spend
/// windows and the runs in progress are the shared holder's; the screen lays them out and links onward.
struct FactoryHomeView: View {
    let environment: AppEnvironment

    var body: some View {
        FactoryHomeScreen(model: environment.factoryHomeModel(), environment: environment)
            .navigationTitle(ShellLabel.tabFactory.text)
    }
}

private struct FactoryHomeScreen: View {
    let model: FactoryHomeModel
    let environment: AppEnvironment
    @Environment(\.dynamicTypeSize) private var typeSize

    private var state: FactoryHomeState { model.state }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                FactorySwitcher(environment: environment, identifier: "factory-switcher")
                if state.tiles != nil { UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value) }
                if state.isOffline, state.tiles != nil {
                    OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                }
                if let error = state.error, state.tiles != nil {
                    ErrorBanner(
                        error: error, onRetry: { model.refresh() },
                        onSignInAgain: { environment.router.select(.factories) })
                }
                if let tiles = state.tiles {
                    spendCard(tiles)
                    tileGrid(tiles)
                    runningNow
                } else {
                    DetailUnavailableView(
                        phase: state.phase, error: state.error, isOffline: state.isOffline,
                        onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
                }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
        }
        .background(Palette.background.color)
        .refreshable {
            model.refresh()
            await settleRefresh { model.state.isRefreshing }
        }
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .accessibilityIdentifier("factory-root")
    }

    // MARK: - Spend

    private func spendCard(_ tiles: FactoryTilesUi) -> some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            AdaptiveStack(alignment: .firstTextBaseline, spacing: Spacing.medium) {
                Text(FactoryLabel.monthSpend.text.uppercased()).font(Typography.label)
                    .foregroundStyle(Palette.textSecondary.color)
                if !typeSize.isAccessibilitySize { Spacer(minLength: Spacing.medium) }
                Text(FactoryLabel.utcMonth.text).font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
            }
            .accessibilityElement(children: .combine)
            Text(
                tiles.hasBudget
                    ? FactoryLabel.spendOf.text(MoneyFormat.text(tiles.monthSpend), MoneyFormat.text(tiles.monthBudget))
                    : MoneyFormat.text(tiles.monthSpend)
            )
            .font(Typography.largeTitle)
            .minimumScaleFactor(0.7)
            .accessibilityIdentifier("factory-spend")
            if tiles.hasBudget {
                SpendMeter(percent: Int(tiles.monthPercentUsed))
                Text(FactoryLabel.spendPercent.text(Int(tiles.monthPercentUsed)))
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("factory-spend-percent")
            }
            windows
        }
        .cardSurface()
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("factory-spend-card")
    }

    @ViewBuilder private var windows: some View {
        if state.spend.isEmpty {
            if state.spendUnavailable {
                Text(FactoryLabel.spendUnavailable.text).font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("factory-spend-unavailable")
            }
        } else {
            FlowLayout(spacing: Spacing.medium) {
                ForEach(state.spend, id: \.window) { spend in
                    HStack(spacing: Spacing.small) {
                        Text(L10n.text(spend.window)).font(Typography.secondary)
                            .foregroundStyle(Palette.textSecondary.color)
                        Text(MoneyFormat.text(spend.cost)).font(Typography.button)
                    }
                    .padding(.horizontal, Spacing.large)
                    .frame(minHeight: 32)
                    .background(Palette.muted.color, in: Capsule())
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(L10n.text(spend.window)) \(MoneyFormat.text(spend.cost))")
                    .accessibilityIdentifier("factory-window-\(spend.window)")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(FactoryLabel.spendWindowsLabel.text)
        }
    }

    // MARK: - Tiles

    private func tileGrid(_ tiles: FactoryTilesUi) -> some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 160), spacing: Spacing.large, alignment: .top)],
            spacing: Spacing.large
        ) {
            tile(
                FactoryLabel.tileAgents.text, value: FactoryLabel.agentsRunning.text(Int(tiles.agentsRunning)),
                detail: FactoryLabel.agentsErrorPaused.text(Int(tiles.agentsError), Int(tiles.agentsPaused)),
                id: "agents", tab: .agents)
            tile(
                FactoryLabel.tileWork.text, value: FactoryLabel.workOpen.text(Int(tiles.tasksOpen)),
                detail: FactoryLabel.workBlockedInProgress.text(Int(tiles.tasksBlocked), Int(tiles.tasksInProgress)),
                id: "work", tab: .work)
            tile(
                FactoryLabel.tileApprovals.text, value: FactoryLabel.approvalsPending.text(Int(tiles.pendingApprovals)),
                detail: nil, id: "approvals", tab: .inbox)
            tile(
                FactoryLabel.tileBudget.text, value: FactoryLabel.incidentsOpen.text(Int(tiles.budgetIncidents)),
                detail: nil, id: "budget", tab: .inbox)
        }
    }

    private func tile(_ title: String, value: String, detail: String?, id: String, tab: AppTab) -> some View {
        Button {
            environment.router.select(tab)
        } label: {
            VStack(alignment: .leading, spacing: Spacing.small) {
                Text(title.uppercased()).font(Typography.label)
                    .foregroundStyle(Palette.textSecondary.color)
                Text(value).font(Typography.title).foregroundStyle(Palette.textPrimary.color)
                if let detail {
                    Text(detail).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                        .multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .topLeading)
            .cardSurface()
        }
        .buttonStyle(.plain)
        .accessibilityLabel([title, value, detail].compactMap { $0 }.joined(separator: ". "))
        .accessibilityIdentifier("factory-tile-\(id)")
    }

    // MARK: - Running now

    private var runningNow: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: FactoryLabel.runningNow.text)
            if state.liveRuns.isEmpty {
                Text(FactoryLabel.nothingRunning.text).font(Typography.body)
                    .foregroundStyle(Palette.textSecondary.color)
                    .cardSurface()
                    .accessibilityIdentifier("factory-nothing-running")
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(state.liveRuns.enumerated()), id: \.element.runId) { index, run in
                        if index > 0 { HairlineDivider() }
                        RunListRow(
                            row: run,
                            identifier: "factory-run-\(run.runId)"
                        ) {
                            environment.router.push(.run(id: run.runId), in: .factory)
                        }
                    }
                }
                .padding(.horizontal, Spacing.card)
                .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
                .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("factory-running-now")
    }
}
