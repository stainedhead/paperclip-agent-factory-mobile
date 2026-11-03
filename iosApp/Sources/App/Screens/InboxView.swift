import PaperclipKit
import SwiftUI

/// Screen 1, the root of the Inbox tab. It renders `InboxState` and forwards intents; which actions a card offers,
/// what a dismiss hides and when Face ID is asked are all decided in the shared holder.
///
/// There is one Inbox holder for the app (`AppEnvironment.inbox`): it feeds the tab badge, owns the refresh session
/// (foreground, background, offline are the shared policy's) and is the same model this screen shows. The screen
/// asks for one more refresh when it comes back into view, never starts a second session.
struct InboxView: View {
    let environment: AppEnvironment

    var body: some View {
        Group {
            if let model = environment.inbox {
                InboxScreen(model: model, environment: environment)
            } else {
                LoadingState()
            }
        }
        .navigationTitle(ShellLabel.tabInbox.text)
        .accessibilityIdentifier("inbox-root")
    }
}

/// "Home factory · Acme Labs": which Inbox this is. Tapping goes to the Factories tab, where factories and companies
/// are chosen.
struct FactorySwitcher: View {
    let environment: AppEnvironment
    var identifier = "inbox-switcher"

    var body: some View {
        let state = environment.app.state
        let factories = environment.factoriesModel()
        let factory = state.selectedInstanceName
        let company = factories.state.rows
            .first { $0.id == state.selectedInstanceId }
            .flatMap { row in row.companies.first { $0.id == row.selectedCompanyId }?.name }
        let label = InboxHeader.factoryLabel(factory: factory, company: company)
        let selected = factories.state.rows.first { $0.id == state.selectedInstanceId }
        let dot = SwitcherDot.tone(
            status: selected?.status, expiry: selected?.expiry, isOffline: factories.state.isOffline)
        Button {
            environment.router.select(.factories)
        } label: {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.small) {
                Circle().fill(dot.foreground.color).frame(width: 8, height: 8).accessibilityHidden(true)
                Text(label).font(Typography.secondary).multilineTextAlignment(.leading)
                Image(systemName: "chevron.down").font(Typography.micro).accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(Palette.textPrimary.color)
        .accessibilityLabel(label)
        .accessibilityHint(InboxLabel.switcherHint.text)
        .accessibilityIdentifier(identifier)
        .task {
            factories.refresh()
            await factories.observe()
        }
    }
}

private struct InboxScreen: View {
    let model: InboxModel
    let environment: AppEnvironment
    @State private var snoozeTarget: String?
    @State private var web: WebLink?
    @State private var appeared = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var state: InboxState { model.state }
    private var isDisabled: Bool { state.actionsDisabledReason != nil || state.isMutating }

    var body: some View {
        List {
            header
            switch state.phase {
            case .loading: skeleton
            case .failed: failure
            case .offlineEmpty: placeholder(offline: true)
            case .empty: placeholder(offline: false)
            case .content: content
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Palette.background.color)
        .refreshable { await refresh() }
        .safeAreaInset(edge: .bottom) {
            if state.undo != nil { UndoSnackbar { model.undo() }.padding(.bottom, Spacing.medium) }
        }
        .animation(reduceMotion ? nil : .default, value: state.undo != nil)
        .sensoryFeedback(.success, trigger: state.totalCount)
        .confirmationDialog(InboxLabel.snoozeTitle.text, isPresented: snoozeBinding, presenting: snoozeTarget) { id in
            ForEach(state.snoozeOptions, id: \.self) { preset in
                Button(L10n.text(preset)) { model.snooze(id, preset: preset) }
                    .accessibilityIdentifier("inbox-snooze-\(preset)")
            }
            Button(ShellLabel.commonCancel.text, role: .cancel) {}
        }
        .sheet(isPresented: rejectBinding) { rejectSheet }
        .sheet(item: $web) { SafariView(url: $0.url).ignoresSafeArea() }
        .onAppear {
            if appeared { model.refresh() }
            appeared = true
        }
    }

    // MARK: - Header

    @ViewBuilder private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.extraLarge) {
            FactorySwitcher(environment: environment)
            if state.isOffline {
                OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                countLine
            }
            if let reason = state.actionsDisabledReason, state.phase == .content {
                DisabledReasonView(reason: reason)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let feedback = state.feedback, !(feedback.kind == .deviceAuth && state.rejectPrompt != nil) {
                FeedbackBanner(feedback: feedback) { model.dismissMessage() }
            }
            if let error = state.error {
                ErrorBanner(
                    error: error, onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) }
                )
            }
            if state.phase == .content || state.phase == .empty {
                InboxFilterChips(
                    total: Int(state.totalCount), filters: state.filters, selected: state.selectedFilter,
                    onSelect: { model.select($0) }
                )
                .padding(.horizontal, -Spacing.page)
            }
        }
        .listRowInsets(EdgeInsets(top: 0, leading: Spacing.page, bottom: Spacing.medium, trailing: Spacing.page))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var countLine: some View {
        FlowLayout(spacing: Spacing.small) {
            Text(
                state.badgeCount > 0
                    ? InboxLabel.dueToday.text(Int(state.badgeCount)) : InboxLabel.dueTodayNone.text
            )
            .font(Typography.secondary.weight(.semibold))
            .foregroundStyle((state.badgeCount > 0 ? Palette.destructive : Palette.textSecondary).color)
            .accessibilityIdentifier("inbox-due-count")
            UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
        }
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        ForEach(state.sections, id: \.group) { section in
            Section {
                ForEach(section.items, id: \.id) { item in
                    row(item)
                }
            } header: {
                SectionHeader(title: L10n.text(section.group))
                    .accessibilityIdentifier("inbox-section-\(section.group)")
            }
            .listSectionSeparator(.hidden)
        }
        if state.canLoadMore {
            Button(InboxLabel.loadMore.text) { model.loadMore() }
                .buttonStyle(.app(.secondary))
                .disabled(state.isLoadingMore)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                .accessibilityIdentifier("inbox-load-more")
        }
    }

    private func row(_ item: InboxItemUi) -> some View {
        InboxCardView(
            item: item, isDisabled: isDisabled,
            onOpen: { open(item) },
            onApprove: { model.approve(item.id) },
            onReject: { model.requestReject(item.id) },
            onKeepPaused: { model.keepPaused(item.id) }
        )
        .listRowInsets(
            EdgeInsets(top: Spacing.small, leading: Spacing.page, bottom: Spacing.small, trailing: Spacing.page)
        )
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if state.actionsDisabledReason == nil {
                Button(role: .destructive) {
                    model.dismiss(item.id)
                } label: {
                    Label(InboxLabel.swipeDismiss.text, systemImage: "xmark.bin")
                }
                .accessibilityIdentifier("inbox-dismiss-\(item.id)")
                Button {
                    snoozeTarget = item.id
                } label: {
                    Label(InboxLabel.swipeSnooze.text, systemImage: "clock")
                }
                .tint(Palette.accent.color)
                .accessibilityIdentifier("inbox-snooze-\(item.id)")
            }
        }
        .accessibilityAction(named: InboxLabel.swipeDismiss.text) {
            if state.actionsDisabledReason == nil { model.dismiss(item.id) }
        }
        .accessibilityAction(named: InboxLabel.swipeSnooze.text) {
            if state.actionsDisabledReason == nil { snoozeTarget = item.id }
        }
    }

    /// Redacted anyway and hidden from VoiceOver: a shape of text, not words in any language.
    private static let placeholder = "\u{2022}\u{2022}\u{2022}\u{2022}\u{2022}\u{2022}\u{2022}\u{2022}"

    private var skeleton: some View {
        ForEach(0..<3, id: \.self) { _ in
            AttentionCard(
                severity: .medium, kind: Self.placeholder, age: Self.placeholder, title: Self.placeholder,
                subtitle: Self.placeholder
            ) {
                EmptyView()
            }
            .redacted(reason: .placeholder)
            .listRowInsets(
                EdgeInsets(top: Spacing.small, leading: Spacing.page, bottom: Spacing.small, trailing: Spacing.page)
            )
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(ShellLabel.commonLoading.text)
        .accessibilityIdentifier("inbox-loading")
    }

    @ViewBuilder private var failure: some View {
        if state.error == nil {
            Button(ShellLabel.commonRetry.text) { model.refresh() }
                .buttonStyle(.app(.secondary))
                .listRowBackground(Color.clear)
                .accessibilityIdentifier("inbox-retry")
        }
    }

    private func placeholder(offline: Bool) -> some View {
        EmptyState(
            symbol: offline ? "wifi.slash" : "tray",
            title: offline ? InboxLabel.offlineEmptyTitle.text : InboxLabel.emptyTitle.text,
            message: offline ? InboxLabel.offlineEmptyMessage.text : InboxLabel.emptyMessage.text
        )
        .frame(minHeight: 320)
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    // MARK: - Navigation

    private func open(_ item: InboxItemUi) {
        if item.open.kind == .decision {
            environment.rememberDecisionHints(
                DecisionHintMapping.hints(item), for: item.open.id, identifier: item.identifier)
        }
        switch InboxDestination(target: item.open, webUrl: item.webUrl) {
        case .route(let route): environment.router.push(route, in: .inbox)
        case .sheet(let sheet): environment.router.present(sheet)
        case .web(let url): web = WebLink(url: url)
        case .none: break
        }
    }

    private func refresh() async {
        model.refresh()
        try? await Task.sleep(for: .milliseconds(300))
        while model.state.isRefreshing { try? await Task.sleep(for: .milliseconds(100)) }
    }

    // MARK: - Sheets

    private var snoozeBinding: Binding<Bool> {
        Binding(get: { snoozeTarget != nil }, set: { if !$0 { snoozeTarget = nil } })
    }

    private var rejectBinding: Binding<Bool> {
        Binding(get: { state.rejectPrompt != nil }, set: { if !$0 { model.cancelReject() } })
    }

    @ViewBuilder private var rejectSheet: some View {
        let title = state.sections.flatMap(\.items).first { $0.id == state.rejectPrompt?.itemId }?.title ?? ""
        RejectNoteSheet(
            title: title, isWorking: state.isMutating, issue: state.feedback?.deviceAuth,
            onConfirm: { model.confirmReject(note: $0) }, onCancel: { model.cancelReject() })
    }
}

/// The outcome of the last action, in words: the role refused it, Face ID was cancelled, the server rejected it...
struct FeedbackBanner: View {
    let feedback: FeedbackUi
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.medium) {
            Text(message)
                .font(Typography.body)
                .foregroundStyle(Palette.warn.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("feedback-message")
            Button(ShellLabel.commonDismiss.text, action: onDismiss)
                .font(Typography.button)
                .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
                .accessibilityIdentifier("feedback-dismiss")
        }
        .padding(.horizontal, Spacing.card)
        .background(Palette.warnSoft.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("feedback-banner")
    }

    private var message: String { FeedbackPresentation.text(feedback) }
}
