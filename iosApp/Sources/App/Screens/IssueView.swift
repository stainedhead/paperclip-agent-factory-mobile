import PaperclipKit
import SwiftUI

/// Screen 4. It renders `IssueState` and forwards intents. Who may comment, change the status or the assignee, what a
/// comment may reopen or interrupt, which statuses and assignees are offered and every rule of the agent's request
/// cards are the shared holder's; the view lays them out.
struct IssueView: View {
    let environment: AppEnvironment
    let id: String

    var body: some View {
        IssueScreen(model: environment.issueModel(id: id), environment: environment)
    }
}

/// What was in the comment box when Send was tapped, kept so a comment that failed can come back into the box.
private struct CommentDraft {
    let body: String
    let reopen: Bool
    let interrupt: Bool
}

private struct IssueScreen: View {
    let model: IssueScreenModel
    let environment: AppEnvironment
    @State private var comment = ""
    @State private var reopen = false
    @State private var interrupt = false
    @State private var sentDraft: CommentDraft?
    @State private var showsSubtask = false
    @State private var web: WebLink?

    private var state: IssueState { model.state }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if state.isOffline && state.phase == .content {
                    OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                }
                if state.phase == .content, let header = state.header { content(header) } else { unavailable }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.vertical, Spacing.roomy)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("issue-screen")
        }
        .background(Palette.background.color)
        .navigationTitle(state.header?.identifier ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if state.phase == .content {
                IssueCommentBar(model: model, state: state, comment: $comment, reopen: $reopen, interrupt: $interrupt) {
                    sentDraft = CommentDraft(body: comment, reopen: reopen, interrupt: interrupt)
                    model.comment(comment, reopen: reopen, interrupt: interrupt)
                    (comment, reopen, interrupt) = ("", false, false)
                }
            }
        }
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
        .onChange(of: state.feedback) { _, feedback in
            // A comment that did not go through comes back into the box, so nothing typed is lost.
            guard feedback != nil, let sent = sentDraft else { return }
            if comment.isEmpty { (comment, reopen, interrupt) = (sent.body, sent.reopen, sent.interrupt) }
            sentDraft = nil
        }
        .sheet(isPresented: $showsSubtask) { SubtaskSheet(model: model) }
        .sheet(item: $web) { SafariView(url: $0.url).ignoresSafeArea() }
    }

    // MARK: - Content

    @ViewBuilder private func content(_ header: IssueHeaderUi) -> some View {
        IssueHeaderView(model: model, state: state, header: header, environment: environment)
        if let feedback = state.feedback { FeedbackBanner(feedback: feedback) { model.dismissMessage() } }
        if let error = state.error {
            ErrorBanner(
                error: error, onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
        }
        if !state.subtasks.isEmpty || state.canCreateSubtask { subtasks }
        IssueTabs(state: state) { model.select($0) }
        if let error = state.tabError {
            ErrorBanner(error: error, onRetry: { model.select(state.tab) })
        }
        switch state.tab {
        case .thread: IssueThreadView(model: model, state: state, environment: environment, web: $web)
        case .plan: planTab
        case .runs: runsTab
        case .files: filesTab
        }
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }

    // MARK: - Subtasks

    private var subtasks: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: IssueLabel.subtasks.text)
            ForEach(state.subtasks, id: \.id) { row in
                let presentation = IssueRowPresentation(row: row)
                Button {
                    environment.router.push(.issue(id: row.id), in: environment.router.selectedTab)
                } label: {
                    VStack(alignment: .leading, spacing: Spacing.hairline) {
                        Text("\(row.identifier) \u{00B7} \(row.title)").font(Typography.body)
                            .foregroundStyle(Palette.textPrimary.color).multilineTextAlignment(.leading)
                        Text(presentation.summary).font(Typography.secondary)
                            .foregroundStyle(Palette.textSecondary.color)
                    }
                    .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                    .cardSurface()
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(presentation.accessibility)
                .accessibilityAddTraits(.isButton)
                .accessibilityIdentifier("issue-subtask-\(row.id)")
            }
            if state.canCreateSubtask {
                Button(IssueLabel.addSubtask.text) { showsSubtask = true }
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("issue-add-subtask")
            }
        }
    }

    // MARK: - Plan, runs, files

    @ViewBuilder private var planTab: some View {
        if let plan = state.plan {
            VStack(alignment: .leading, spacing: Spacing.medium) {
                if let title = plan.title {
                    Text(title).font(Typography.cardTitle).accessibilityAddTraits(.isHeader)
                }
                Text(MarkdownText.attributed(plan.body)).font(Typography.body)
                    .foregroundStyle(Palette.textPrimary.color)
                    .accessibilityIdentifier("issue-plan-body")
                if let updated = plan.updatedAtEpochMillis?.int64Value {
                    UpdatedLabel(epochMillis: updated)
                }
            }
            .cardSurface()
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("issue-plan")
        } else {
            note(IssueLabel.planEmpty.text, id: "issue-plan-empty")
        }
    }

    @ViewBuilder private var runsTab: some View {
        if !state.runsLoaded {
            if state.tabError == nil { ProgressView().frame(maxWidth: .infinity, minHeight: 80) }
        } else if state.runs.isEmpty {
            note(IssueLabel.runsEmpty.text, id: "issue-runs-empty")
        } else {
            VStack(spacing: 0) {
                ForEach(Array(state.runs.enumerated()), id: \.element.runId) { index, run in
                    if index > 0 { HairlineDivider() }
                    RunListRow(
                        row: run, referenceDate: environment.referenceDate, identifier: "issue-run-\(run.runId)"
                    ) { environment.router.push(.run(id: run.runId), in: environment.router.selectedTab) }
                }
            }
            .padding(.horizontal, Spacing.card)
            .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("issue-runs")
        }
    }

    @ViewBuilder private var filesTab: some View {
        if !state.filesLoaded {
            if state.tabError == nil { ProgressView().frame(maxWidth: .infinity, minHeight: 80) }
        } else if state.files.isEmpty {
            note(IssueLabel.filesEmpty.text, id: "issue-files-empty")
        } else {
            VStack(alignment: .leading, spacing: Spacing.medium) {
                CardGroup {
                    ForEach(Array(state.files.enumerated()), id: \.element.id) { index, file in
                        if index > 0 { HairlineDivider() }
                        FileRow(file: file)
                    }
                }
                Text(IssueLabel.filesNote.text).font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("issue-files")
        }
    }

    private func note(_ text: String, id: String) -> some View {
        Text(text).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
            .cardSurface().accessibilityIdentifier(id)
    }
}

private struct FileRow: View {
    let file: FileUi

    var body: some View {
        let name = file.name ?? file.contentType
        let detail = IssueLabel.fileLine.text(file.contentType, FileSizeText.text(bytes: file.byteSize))
        VStack(alignment: .leading, spacing: Spacing.hairline) {
            Text(name).font(Typography.body).foregroundStyle(Palette.textPrimary.color)
            Text(detail).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
        }
        .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
        .padding(.vertical, Spacing.small)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(name). \(detail)")
        .accessibilityIdentifier("issue-file-\(file.id)")
    }
}

/// Text from the server that may carry markdown: inline styling is shown, the text is never executed or fetched.
enum MarkdownText {
    static func attributed(_ source: String) -> AttributedString {
        let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        return (try? AttributedString(markdown: source, options: options)) ?? AttributedString(source)
    }
}
