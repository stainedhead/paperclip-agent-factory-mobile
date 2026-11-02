import PaperclipKit
import SwiftUI

/// Screen 7. It renders `RunViewerState` and forwards intents. The events, the log tail (from a byte offset, never
/// re-read from the start), whether the run can be cancelled, the confirmation and Face ID on Cancel, and the five
/// second refresh while it runs are the shared holder's; the view lays them out.
struct RunViewerView: View {
    let environment: AppEnvironment
    let id: String

    var body: some View {
        RunViewerScreen(model: environment.runModel(id: id), environment: environment)
    }
}

private enum LogColors {
    static let background = Palette.Token(light: 0x15171C, dark: 0x0B0C0E)
    static let text = Palette.Token(light: 0xE6E8EC, dark: 0xE6E8EC)
    static let muted = Palette.Token(light: 0xB4B9C4, dark: 0xB4B9C4)
}

private struct RunViewerScreen: View {
    let model: RunViewerModel
    let environment: AppEnvironment
    @State private var dragging = false
    @Environment(\.dynamicTypeSize) private var typeSize

    private var state: RunViewerState { model.state }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if state.isOffline && state.phase == .content {
                    OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                }
                if state.phase == .content, let run = state.run { content(run) } else { unavailable }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("run-screen")
        }
        .background(Palette.background.color)
        .navigationTitle(RunLabel.navTitle.text)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .safeAreaInset(edge: .bottom) { cancelBar }
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .sensoryFeedback(trigger: state.isCancelling) { old, new in
            guard old, !new else { return nil }
            return state.feedback == nil ? .success : .error
        }
        .sheet(isPresented: cancelBinding) { cancelSheet }
    }

    // MARK: - Content

    @ViewBuilder private func content(_ run: RunRowUi) -> some View {
        header(run)
        if let feedback = state.feedback { FeedbackBanner(feedback: feedback) { model.dismissMessage() } }
        if let error = state.error {
            ErrorBanner(
                error: error, onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
        }
        events(run)
        logTail
    }

    private func header(_ run: RunRowUi) -> some View {
        let presentation = RunRowPresentation(row: run, referenceDate: environment.referenceDate)
        let status = [L10n.text(run.status), presentation.elapsed].compactMap { $0 }.joined(separator: " \u{00B7} ")
        return VStack(alignment: .leading, spacing: Spacing.medium) {
            StatusPill(text: status, tone: pillTone(RunRowPresentation.tone(run.status)))
            Text(RunLabel.heartbeat.text(run.agentName ?? ""))
                .font(Typography.title).accessibilityAddTraits(.isHeader).accessibilityIdentifier("run-title")
            if let line = presentation.issueLine {
                Button {
                    if let issue = run.issueId {
                        environment.router.push(.issue(id: issue), in: environment.router.selectedTab)
                    }
                } label: {
                    Text(line).font(Typography.body).foregroundStyle(Palette.accent.color)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint(RunLabel.openIssue.text)
                .accessibilityIdentifier("run-issue")
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

    // MARK: - Events

    private func events(_ run: RunRowUi) -> some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: RunLabel.events.text)
            if state.events.isEmpty {
                Text(RunLabel.noEvents.text).font(Typography.body)
                    .foregroundStyle(Palette.textSecondary.color).cardSurface()
                    .accessibilityIdentifier("run-no-events")
            } else {
                VStack(alignment: .leading, spacing: Spacing.extraLarge) {
                    ForEach(state.events, id: \.seq) { event in
                        eventRow(event, started: run.startedAtEpochMillis?.int64Value)
                    }
                }
                .cardSurface()
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("run-events")
            }
        }
    }

    private func eventRow(_ event: RunEventUi, started: Int64?) -> some View {
        let offset = started.map { RunTime.offset(seconds: Double(event.createdAtEpochMillis - $0) / 1000) }
        let message = event.message ?? event.type
        return HStack(alignment: .firstTextBaseline, spacing: Spacing.extraLarge) {
            Text(offset ?? "").font(Typography.secondary.monospacedDigit())
                .foregroundStyle(Palette.textSecondary.color)
                .frame(minWidth: 44, alignment: .leading)
            Text(message).font(Typography.body).multilineTextAlignment(.leading)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([offset, message].compactMap { $0 }.joined(separator: ", "))
        .accessibilityIdentifier("run-event-\(event.seq)")
    }

    // MARK: - Log tail

    private var logTail: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            HStack {
                SectionHeader(title: RunLabel.logTail.text)
                Spacer(minLength: Spacing.medium)
                autoScrollToggle
            }
            logPanel
            if !state.autoScroll {
                Button(RunLabel.jumpToLatest.text) { model.jumpToLatest() }
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("run-jump-to-latest")
            }
        }
    }

    private var autoScrollToggle: some View {
        Button {
            if state.autoScroll { model.userScrolled(atBottom: false) } else { model.jumpToLatest() }
        } label: {
            Text(state.autoScroll ? RunLabel.autoScrollOn.text : RunLabel.autoScrollOff.text)
                .font(Typography.secondary)
                .foregroundStyle(Palette.accent.color)
                .frame(minHeight: Spacing.minimumTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(state.autoScroll ? RunLabel.autoScrollOn.text : RunLabel.autoScrollOff.text)
        .accessibilityHint(RunLabel.autoScrollHint.text)
        .accessibilityIdentifier("run-autoscroll")
    }

    private var logPanel: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.small) {
                    if state.logDroppedStart {
                        Text(RunLabel.logDropped.text).font(Typography.secondary)
                            .foregroundStyle(LogColors.muted.color)
                    }
                    if state.logText.isEmpty {
                        Text(state.logUnavailable ? RunLabel.logUnavailable.text : RunLabel.logEmpty.text)
                            .font(.system(.footnote, design: .monospaced))
                            .foregroundStyle(LogColors.muted.color)
                            .accessibilityIdentifier("run-log-empty")
                    } else {
                        Text(state.logText)
                            .font(.system(.footnote, design: .monospaced))
                            .foregroundStyle(LogColors.text.color)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                            .accessibilityIdentifier("run-log")
                    }
                    Color.clear.frame(height: 1).id("run-log-end")
                        .onAppear { if dragging || !state.autoScroll { model.userScrolled(atBottom: true) } }
                        .onDisappear { if dragging { model.userScrolled(atBottom: false) } }
                }
                .padding(Spacing.card)
            }
            .frame(height: typeSize.isAccessibilitySize ? 360 : 240)
            .background(LogColors.background.color, in: RoundedRectangle(cornerRadius: Radius.card))
            .simultaneousGesture(
                DragGesture(minimumDistance: 4)
                    .onChanged { _ in dragging = true }
                    .onEnded { _ in dragging = false }
            )
            .onChange(of: state.logText) { _, _ in follow(proxy) }
            .onChange(of: state.autoScroll) { _, _ in follow(proxy) }
            .onAppear { follow(proxy) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("run-log-panel")
    }

    private func follow(_ proxy: ScrollViewProxy) {
        guard state.autoScroll else { return }
        proxy.scrollTo("run-log-end", anchor: .bottom)
    }

    // MARK: - Cancel

    @ViewBuilder private var cancelBar: some View {
        if state.phase == .content, state.run != nil, state.isLive || state.isCancelling {
            VStack(spacing: Spacing.medium) {
                if let reason = state.actionsDisabledReason, !state.canCancel, !state.isCancelling {
                    DisabledReasonView(reason: reason)
                }
                Button {
                    model.cancel()
                } label: {
                    Label(
                        state.isCancelling ? RunLabel.cancelling.text : RunLabel.cancelRun.text,
                        systemImage: "lock.fill")
                }
                .buttonStyle(.app(.destructive))
                .disabled(!state.canCancel)
                .accessibilityIdentifier("run-cancel")
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.medium)
            .background(.bar)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("run-cancel-bar")
        }
    }

    private var cancelBinding: Binding<Bool> {
        Binding(get: { state.confirmCancel }, set: { if !$0 { model.dismissCancel() } })
    }

    @ViewBuilder private var cancelSheet: some View {
        ConfirmDestructiveSheet(
            title: RunLabel.cancelTitle.text, message: RunLabel.cancelMessage.text,
            confirmTitle: RunLabel.cancelConfirm.text, cancelTitle: RunLabel.cancelKeep.text,
            onConfirm: { model.confirmCancel() }, onCancel: { model.dismissCancel() })
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }
}
