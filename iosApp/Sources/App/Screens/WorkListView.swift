import PaperclipKit
import SwiftUI

/// Screen 10, the root of the Work tab. It renders `WorkListState` and forwards intents. The saved filters, what a
/// search matches, paging, who may create an issue and what a created issue is are the shared holder's; the view
/// lays the rows out and links onward. There is no mock for this screen: it is designed from the state and the
/// tokens of the other tabs.
struct WorkListView: View {
    let environment: AppEnvironment

    var body: some View {
        WorkListScreen(model: environment.workModel(), environment: environment)
            .navigationTitle(ShellLabel.tabWork.text)
    }
}

private struct WorkListScreen: View {
    let model: WorkListModel
    let environment: AppEnvironment
    @State private var searchText = ""
    @State private var showsCreate = false

    private var state: WorkListState { model.state }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.roomy) {
                header
                if state.phase == .content { content } else { unavailable }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
        }
        .background(Palette.background.color)
        .scrollDismissesKeyboard(.interactively)
        .refreshable {
            model.refresh()
            await settleRefresh { false }
        }
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .sensoryFeedback(trigger: state.isCreating) { old, new in
            guard old, !new else { return nil }
            return state.feedback == nil ? .success : .error
        }
        .onChange(of: state.createdIssueId) { _, created in
            guard let created else { return }
            model.createdAcknowledged()
            environment.router.push(.issue(id: created), in: .work)
        }
        .sheet(isPresented: $showsCreate) { CreateIssueSheet(model: model) }
        .accessibilityIdentifier("work-root")
    }

    // MARK: - Header

    @ViewBuilder private var header: some View {
        FactorySwitcher(environment: environment, identifier: "work-switcher")
        if state.phase == .content {
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
            searchField
            chips
            countRow
        }
    }

    private var searchField: some View {
        TextField(WorkLabel.searchPlaceholder.text, text: $searchText)
            .font(Typography.body)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(.search)
            .onSubmit { model.search(searchText) }
            .onChange(of: searchText) { _, text in
                // Searching is sent when the person submits; clearing the box shows the filter again at once.
                if text.isEmpty, !state.search.isEmpty { model.search("") }
            }
            .padding(.leading, Spacing.minimumTarget)
            .padding(.trailing, Spacing.extraLarge)
            .frame(minHeight: Spacing.minimumTarget)
            .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.button))
            .overlay(alignment: .leading) {
                Image(systemName: "magnifyingglass").foregroundStyle(Palette.textSecondary.color)
                    .padding(.leading, Spacing.extraLarge).accessibilityHidden(true)
            }
            .overlay(RoundedRectangle(cornerRadius: Radius.button).strokeBorder(Palette.border.color))
            .accessibilityLabel(WorkLabel.searchLabel.text)
            .accessibilityIdentifier("work-search")
    }

    private var chips: some View {
        FlowLayout(spacing: Spacing.medium) {
            ForEach(IssueFilterOrder.chips, id: \.self) { filter in
                FilterChip(
                    text: L10n.text(filter), isSelected: state.filter == filter,
                    identifier: "work-filter-\(filter)"
                ) { model.select(filter) }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(WorkLabel.filtersLabel.text)
        .accessibilityIdentifier("work-filters")
    }

    private var countRow: some View {
        AdaptiveStack {
            VStack(alignment: .leading, spacing: Spacing.hairline) {
                Text(state.rows.count == 1 ? WorkLabel.countOne.text : WorkLabel.countLine.text(state.rows.count))
                    .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("work-count")
                UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
            }
            Spacer(minLength: Spacing.medium)
            if state.canCreate {
                Button {
                    showsCreate = true
                } label: {
                    Label(WorkLabel.create.text, systemImage: "plus")
                }
                .buttonStyle(.app(.primary))
                .fixedSize()
                .disabled(state.isCreating)
                .accessibilityIdentifier("work-create")
            }
        }
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        if state.rows.isEmpty {
            EmptyState(
                symbol: "checklist", title: WorkLabel.emptyTitle.text,
                message: state.search.isEmpty ? WorkLabel.emptyMessage.text : WorkLabel.emptySearchMessage.text
            ).frame(minHeight: 280)
        } else {
            VStack(spacing: Spacing.large) {
                ForEach(state.rows, id: \.id) { row in
                    WorkRowCard(row: row) { environment.router.push(.issue(id: row.id), in: .work) }
                }
            }
            if state.hasMore { loadMore }
        }
    }

    /// The end of the loaded rows: reaching it asks for the next page once; the holder ignores a second ask.
    private var loadMore: some View {
        HStack(spacing: Spacing.medium) {
            ProgressView()
            Text(WorkLabel.loadingMore.text).font(Typography.secondary)
                .foregroundStyle(Palette.textSecondary.color)
        }
        .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget)
        .onAppear { model.loadMore() }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("work-loading-more")
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }
}

/// One issue: its number, title, status, priority and assignee, and a way in.
private struct WorkRowCard: View {
    let row: IssueRowUi
    let onOpen: () -> Void

    var body: some View {
        let presentation = IssueRowPresentation(row: row)
        Button(action: onOpen) {
            HStack(alignment: .top, spacing: Spacing.extraLarge) {
                VStack(alignment: .leading, spacing: Spacing.small) {
                    Text(row.identifier).font(Typography.label).foregroundStyle(Palette.textSecondary.color)
                    Text(row.title).font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
                        .multilineTextAlignment(.leading)
                    FlowLayout(spacing: Spacing.medium) {
                        StatusPill(text: L10n.text(row.status), tone: pillTone(IssueTone.status(row.status)))
                        StatusPill(text: L10n.text(row.priority), tone: pillTone(IssueTone.priority(row.priority)))
                    }
                    Text(presentation.assignee).font(Typography.secondary)
                        .foregroundStyle(Palette.textSecondary.color)
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
        .accessibilityLabel(presentation.accessibility)
        .accessibilityHint(WorkLabel.openHint.text)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("work-row-\(row.id)")
    }
}

func pillTone(_ tone: StatusTone) -> StatusPill.Tone {
    switch tone {
    case .success: .success
    case .warn: .warn
    case .critical: .critical
    case .neutral: .neutral
    }
}

/// "New issue": a title and a priority. The shared holder checks the title and the role; the sheet only collects.
private struct CreateIssueSheet: View {
    let model: WorkListModel
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var priority = IssuePriorityUi.medium

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            Text(WorkLabel.createTitle.text).font(Typography.title).accessibilityAddTraits(.isHeader)
            FormField(
                title: WorkLabel.createField.text, placeholder: WorkLabel.createPlaceholder.text, text: $title,
                capitalization: .sentences, identifier: "work-create-title")
            VStack(alignment: .leading, spacing: Spacing.small) {
                Text(WorkLabel.createPriority.text).font(Typography.button)
                Picker(WorkLabel.createPriority.text, selection: $priority) {
                    ForEach(
                        [IssuePriorityUi.critical, .high, .medium, .low], id: \.self
                    ) { Text(L10n.text($0)).tag($0) }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("work-create-priority")
            }
            Button(WorkLabel.createSave.text) {
                model.create(title: title, priority: priority)
                dismiss()
            }
            .buttonStyle(.app(.primary))
            .accessibilityIdentifier("work-create-save")
            Button(WorkLabel.createCancel.text) { dismiss() }
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("work-create-cancel")
            Spacer(minLength: 0)
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("work-create-form")
    }
}
