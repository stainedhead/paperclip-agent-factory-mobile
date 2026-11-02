import PaperclipKit
import SwiftUI

/// Screen 3. It renders `ApprovalState` and forwards intents. Which actions are offered (by status, role and
/// connectivity), when a note is required and when Face ID is asked (inside the shared reject use case) are the
/// shared holder's; the view only lays them out.
struct ApprovalView: View {
    let environment: AppEnvironment
    let id: String

    var body: some View {
        ApprovalScreen(model: environment.approvalModel(id: id), environment: environment)
    }
}

private struct ApprovalScreen: View {
    let model: ApprovalModel
    let environment: AppEnvironment
    @State private var commentText = ""

    private var state: ApprovalState { model.state }

    var body: some View {
        VStack(spacing: 0) {
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
                .accessibilityIdentifier("approval-screen")
            }
            .scrollDismissesKeyboard(.interactively)
            actionBar
        }
        .background(Palette.background.color)
        .navigationTitle(ApprovalLabel.navTitle.text)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .sensoryFeedback(.success, trigger: state.isResolved)
        .task { await model.observe() }
        .refreshWhileVisible(graph: environment.graph, target: model.holder.refreshTarget)
        .sheet(isPresented: noteBinding) { noteSheet }
        .onChange(of: state.comments.count) { _, _ in commentText = "" }
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        header
        if let feedback = state.feedback, !(state.notePrompt != nil && feedback.kind != .reconciled) {
            FeedbackBanner(feedback: feedback) { model.dismissMessage() }
        }
        if let error = state.error {
            ErrorBanner(
                error: error, onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
        }
        if let status = state.status, state.isResolved {
            ResultCard(
                title: L10n.text(status), tone: status == .approved ? .success : .neutral, identifier: "approval-final"
            ) {
                Text(ApprovalPresentation.doneText(status))
                if let note = state.decisionNote, !note.isEmpty { Text(ApprovalLabel.decisionNote.text(note)) }
            }
        }
        ApprovalPayloadView(type: state.type, payload: state.payload)
        if !state.issues.isEmpty { linkedWork }
        discussion
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.small) {
            HStack(spacing: Spacing.medium) {
                Text(kindLine)
                    .font(Typography.label)
                    .foregroundStyle(Palette.warn.color)
                    .accessibilityIdentifier("approval-kind-line")
                if let status = state.status {
                    StatusPill(text: L10n.text(status), tone: ApprovalPresentation.tone(status))
                }
            }
            Text(ApprovalPresentation.title(payload: state.payload, type: state.type))
                .font(Typography.title)
                .multilineTextAlignment(.leading)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("approval-title")
            if let requester = state.requestedByAgentName {
                Text(DetailLabel.requestedBy.text(requester))
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("approval-requested-by")
            }
            UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
        }
    }

    private var kindLine: String {
        [ApprovalLabel.kindLine.text, state.type.map { L10n.text($0) }].compactMap { $0 }
            .joined(separator: " \u{00B7} ").uppercased()
    }

    private var linkedWork: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: ApprovalLabel.linkedWork.text)
            ForEach(state.issues, id: \.id) { issue in
                HStack(alignment: .firstTextBaseline) {
                    Text("\(issue.identifier ?? "") \(issue.title)")
                        .font(Typography.body)
                    Spacer(minLength: Spacing.medium)
                    if let status = issue.status {
                        Text(DecisionPresentation.prettify(status).capitalized)
                            .font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    }
                }
                .cardSurface()
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("approval-issue-\(issue.id)")
            }
        }
    }

    private var discussion: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            SectionHeader(title: ApprovalLabel.discussion.text)
            if state.comments.isEmpty {
                Text(ApprovalLabel.noDiscussion.text)
                    .font(Typography.body).foregroundStyle(Palette.textSecondary.color)
            }
            ForEach(state.comments, id: \.id) { comment in
                VStack(alignment: .leading, spacing: Spacing.extraSmall) {
                    Text(ApprovalPresentation.author(comment))
                        .font(Typography.label).foregroundStyle(Palette.textSecondary.color)
                    Text(comment.body).font(Typography.body)
                }
                .cardSurface()
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("approval-comment-\(comment.id)")
            }
            if state.actions.contains(.comment) { commentField }
        }
    }

    private var commentField: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            FormField(
                title: ApprovalLabel.commentLabel.text, placeholder: ApprovalLabel.commentPlaceholder.text,
                text: $commentText, capitalization: .sentences, identifier: "approval-comment-field")
            Button(ApprovalLabel.commentPost.text) { model.comment(commentText) }
                .buttonStyle(.app(.secondary))
                .disabled(commentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || state.isSubmitting)
                .accessibilityIdentifier("approval-comment-post")
        }
    }

    @ViewBuilder private var unavailable: some View {
        DetailUnavailableView(
            phase: state.phase, error: state.error, isOffline: state.isOffline,
            onRetry: { model.refresh() }, onSignInAgain: { environment.router.select(.factories) })
    }

    // MARK: - Actions

    private var hasActionBar: Bool {
        let main: [ApprovalActionUi] = [.approve, .reject, .requestRevision]
        return state.phase == .content
            && (state.actionsDisabledReason != nil || main.contains { state.actions.contains($0) })
    }

    @ViewBuilder private var actionBar: some View {
        if hasActionBar {
            VStack(spacing: Spacing.large) {
                if let reason = state.actionsDisabledReason { DisabledReasonView(reason: reason) }
                AdaptiveStack(spacing: Spacing.large) {
                    if state.actions.contains(.reject) {
                        Button {
                            model.requestReject()
                        } label: {
                            Label(L10n.text(ApprovalActionUi.reject), systemImage: "lock.fill")
                        }
                        .buttonStyle(.app(.destructive))
                        .accessibilityIdentifier("approval-reject")
                    }
                    if state.actions.contains(.approve) {
                        Button(L10n.text(ApprovalActionUi.approve)) { model.approve() }
                            .buttonStyle(.app(.primary))
                            .accessibilityIdentifier("approval-approve")
                    }
                }
                if state.actions.contains(.requestRevision) {
                    Button(L10n.text(ApprovalActionUi.requestRevision)) { model.requestRevision() }
                        .buttonStyle(.app(.muted))
                        .accessibilityIdentifier("approval-request-revision")
                }
            }
            .padding(.horizontal, Spacing.page)
            .padding(.top, Spacing.extraLarge)
            .padding(.bottom, Spacing.medium)
            .background(Palette.surface.color)
            .overlay(alignment: .top) { HairlineDivider() }
        }
    }

    private var noteBinding: Binding<Bool> {
        Binding(get: { state.notePrompt != nil }, set: { if !$0 { model.cancelNote() } })
    }

    @ViewBuilder private var noteSheet: some View {
        if let kind = state.notePrompt {
            ApprovalNoteSheet(
                kind: kind, needsDeviceAuth: kind == .reject && state.rejectNeedsDeviceAuth,
                isWorking: state.isSubmitting,
                issue: state.feedback?.deviceAuth, noteIssue: state.feedback?.invalid,
                onConfirm: { model.confirmNote($0) }, onCancel: { model.cancelNote() })
        }
    }
}
