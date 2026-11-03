import PaperclipKit
import SwiftUI

/// The top of the Issue screen, as in the mock: "PAP-1204 · project", the title, then status, priority and assignee
/// as pills. The assignee pill is a menu when this person may assign. Related issues (the parent, what blocks this)
/// are links.
struct IssueHeaderView: View {
    let model: IssueScreenModel
    let state: IssueState
    let header: IssueHeaderUi
    let environment: AppEnvironment

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            Text([header.identifier, header.projectName].compactMap { $0 }.joined(separator: " \u{00B7} "))
                .font(Typography.secondary.weight(.semibold)).foregroundStyle(Palette.textSecondary.color)
                .accessibilityIdentifier("issue-identifier")
            Text(header.title).font(Typography.title).accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("issue-title")
            FlowLayout(spacing: Spacing.medium) {
                StatusPill(text: L10n.text(header.status), tone: pillTone(IssueTone.status(header.status)))
                    .accessibilityIdentifier("issue-status")
                StatusPill(text: L10n.text(header.priority), tone: pillTone(IssueTone.priority(header.priority)))
                    .accessibilityIdentifier("issue-priority")
                assignee
            }
            if let summary = header.summary, !summary.isEmpty {
                Text(MarkdownText.attributed(summary)).font(Typography.body)
                    .foregroundStyle(Palette.textPrimary.color).accessibilityIdentifier("issue-summary")
            }
            if let parent = header.parent { link(IssueLabel.partOf.text, parent, id: "issue-parent") }
            ForEach(header.blockedBy, id: \.id) { link(IssueLabel.blockedBy.text, $0, id: "issue-blocker-\($0.id)") }
            UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
        }
    }

    private var assigneeName: String {
        header.assigneeIsMe
            ? IssueLabel.assigneeMe.text : (header.assigneeName ?? IssueLabel.assigneeUnassigned.text)
    }

    @ViewBuilder private var assignee: some View {
        if state.canAssign {
            Menu {
                ForEach(state.assigneeChoices, id: \.self) { choice in
                    Button(choiceName(choice)) { model.assign(choice.kind, id: choice.id) }
                        .accessibilityIdentifier("issue-assignee-\(choice.id ?? "none")")
                }
            } label: {
                chip(assigneeName, symbol: "chevron.down")
            }
            .disabled(state.isBusy)
            .accessibilityLabel("\(IssueLabel.assigneeMenu.text): \(assigneeName)")
            .accessibilityHint(IssueLabel.assigneeChange.text)
            .accessibilityIdentifier("issue-assignee-menu")
        } else {
            chip(assigneeName, symbol: nil)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(IssueLabel.assigneeMenu.text): \(assigneeName)")
                .accessibilityIdentifier("issue-assignee")
        }
    }

    private func choiceName(_ choice: AssigneeChoiceUi) -> String {
        switch choice.kind {
        case .none: IssueLabel.assigneeUnassigned.text
        case .me: IssueLabel.assigneeMe.text
        case .agent: choice.name ?? ""
        }
    }

    private func chip(_ text: String, symbol: String?) -> some View {
        HStack(spacing: Spacing.small) {
            Text(text)
            if let symbol { Image(systemName: symbol).font(Typography.micro).accessibilityHidden(true) }
        }
        .font(Typography.label)
        .foregroundStyle(Palette.textPrimary.color)
        .padding(.horizontal, Spacing.large)
        .frame(minHeight: symbol == nil ? 0 : Spacing.minimumTarget)
        .padding(.vertical, symbol == nil ? Spacing.extraSmall : 0)
        .background(Palette.surface.color, in: Capsule())
        .overlay(Capsule().strokeBorder(Palette.border.color))
    }

    private func link(_ title: String, _ ref: IssueRefUi, id: String) -> some View {
        Button {
            environment.router.push(.issue(id: ref.id), in: environment.router.selectedTab)
        } label: {
            Text("\(title): \(ref.identifier) \u{00B7} \(ref.title)").font(Typography.body)
                .foregroundStyle(Palette.accent.color).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title): \(ref.identifier). \(ref.title). \(L10n.text(ref.status))")
        .accessibilityIdentifier(id)
    }
}

/// Thread, Plan, Runs and Files as one segmented control, as in the mock. The tab that is open carries the selected
/// trait, not only the white fill.
struct IssueTabs: View {
    let state: IssueState
    let onSelect: (IssueTab) -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout =
            typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(spacing: Spacing.extraSmall)) : AnyLayout(HStackLayout(spacing: 0))
        layout {
            ForEach([IssueTab.thread, .plan, .runs, .files], id: \.self) { tab in
                Button {
                    onSelect(tab)
                } label: {
                    Text(IssueTabPresentation.title(tab, state: state))
                        .font(Typography.secondary.weight(state.tab == tab ? .semibold : .regular))
                        .foregroundStyle(
                            (state.tab == tab ? Palette.textPrimary : Palette.textSecondary).color
                        )
                        .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget)
                        .background(
                            state.tab == tab ? Palette.surface.color : Color.clear,
                            in: RoundedRectangle(cornerRadius: Radius.button - 2)
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(state.tab == tab ? .isSelected : [])
                .accessibilityIdentifier("issue-tab-\(tab)")
            }
        }
        .padding(3)
        .background(Palette.muted.color, in: RoundedRectangle(cornerRadius: Radius.button))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(IssueLabel.tabsLabel.text)
        .accessibilityIdentifier("issue-tabs")
    }
}

/// The fixed bar of the Issue screen: change status, the comment box, send. The reopen and interrupt switches show
/// only when the state says a comment may do that.
struct IssueCommentBar: View {
    let model: IssueScreenModel
    let state: IssueState
    @Binding var comment: String
    @Binding var reopen: Bool
    @Binding var interrupt: Bool
    let onSend: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            if let reason = state.actionsDisabledReason {
                DisabledReasonView(reason: reason)
            }
            if state.canComment && state.canReopen {
                Toggle(IssueLabel.reopenToggle.text, isOn: $reopen).font(Typography.body)
                    .accessibilityIdentifier("issue-reopen")
            }
            if state.canComment && state.canInterrupt {
                Toggle(IssueLabel.interruptToggle.text, isOn: $interrupt).font(Typography.body)
                    .accessibilityIdentifier("issue-interrupt")
            }
            HStack(spacing: Spacing.medium) {
                statusMenu
                TextField(IssueLabel.commentPlaceholder.text, text: $comment, axis: .vertical)
                    .font(Typography.body)
                    .lineLimit(1...4)
                    .padding(.horizontal, Spacing.extraLarge)
                    .frame(minHeight: Spacing.minimumTarget)
                    .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: 22))
                    .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(Palette.border.color))
                    .disabled(!state.canComment)
                    .accessibilityLabel(IssueLabel.commentField.text)
                    .accessibilityIdentifier("issue-comment-field")
                Button(action: onSend) {
                    Image(systemName: "arrow.right")
                        .frame(width: Spacing.minimumTarget, height: Spacing.minimumTarget)
                        .foregroundStyle((state.canComment ? Palette.onAccent : Palette.textSecondary).color)
                        .background((state.canComment ? Palette.accent : Palette.muted).color, in: Circle())
                }
                .disabled(!state.canComment)
                .accessibilityLabel(IssueLabel.commentSend.text)
                .accessibilityIdentifier("issue-comment-send")
            }
        }
        .padding(.horizontal, Spacing.extraLarge)
        .padding(.vertical, Spacing.large)
        .background(Palette.surface.color)
        .overlay(alignment: .top) { Rectangle().fill(Palette.hairline.color).frame(height: 1) }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("issue-comment-bar")
    }

    private var statusMenu: some View {
        Menu {
            ForEach(state.statusOptions, id: \.self) { status in
                Button(L10n.text(status)) { model.setStatus(status) }
                    .accessibilityIdentifier("issue-status-\(status)")
            }
        } label: {
            Image(systemName: "clock")
                .frame(width: Spacing.minimumTarget, height: Spacing.minimumTarget)
                .foregroundStyle((state.canChangeStatus ? Palette.textPrimary : Palette.textSecondary).color)
                .background(Palette.surface.color, in: Circle())
                .overlay(Circle().strokeBorder(Palette.border.color))
        }
        .disabled(!state.canChangeStatus)
        .accessibilityLabel(IssueLabel.statusMenu.text)
        .accessibilityIdentifier("issue-status-menu")
    }
}

/// "New subtask": a title. The shared holder checks it and the role.
struct SubtaskSheet: View {
    let model: IssueScreenModel
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            Text(IssueLabel.subtaskTitle.text).font(Typography.title).accessibilityAddTraits(.isHeader)
            FormField(
                title: IssueLabel.subtaskField.text, placeholder: IssueLabel.subtaskPlaceholder.text, text: $title,
                capitalization: .sentences, identifier: "issue-subtask-title")
            Button(IssueLabel.subtaskSave.text) {
                model.addSubtask(title)
                dismiss()
            }
            .buttonStyle(.app(.primary))
            .accessibilityIdentifier("issue-subtask-save")
            Button(IssueLabel.subtaskCancel.text) { dismiss() }
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("issue-subtask-cancel")
            Spacer(minLength: 0)
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("issue-subtask-form")
    }
}
