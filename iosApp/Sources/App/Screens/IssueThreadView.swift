import PaperclipKit
import SwiftUI

/// The Thread tab: comments and the agent's request cards in time order, as the shared state lists them.
struct IssueThreadView: View {
    let model: IssueScreenModel
    let state: IssueState
    let environment: AppEnvironment
    @Binding var web: WebLink?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            if state.interactionsUnavailable {
                Text(IssueLabel.interactionsUnavailable.text).font(Typography.secondary)
                    .foregroundStyle(Palette.warn.color).accessibilityIdentifier("issue-interactions-unavailable")
            }
            if state.thread.isEmpty {
                Text(IssueLabel.noThread.text).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
                    .cardSurface().accessibilityIdentifier("issue-thread-empty")
            }
            ForEach(state.thread, id: \.id) { item in
                if let comment = item.comment {
                    CommentCard(comment: comment)
                } else if let card = item.interaction {
                    InteractionCardView(model: model, card: card, isBusy: state.isBusy, web: $web)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("issue-thread")
    }
}

private struct CommentCard: View {
    let comment: IssueCommentUi

    var body: some View {
        TickingReference(every: LiveClock.ageTick) { now in
            card(CommentPresentation.authorLine(comment, referenceDate: now))
        }
    }

    private func card(_ line: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.extraSmall) {
            Text(line).font(Typography.label).foregroundStyle(Palette.textSecondary.color)
            Text(MarkdownText.attributed(comment.body)).font(Typography.body)
                .foregroundStyle(Palette.textPrimary.color)
        }
        .cardSurface()
        // Combined, not replaced by a label: VoiceOver reads the words (never the marks) and a link in the comment
        // stays reachable through the Links rotor.
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("issue-comment-\(comment.id)")
    }
}

/// A text field whose text the view keeps while the person types; it only reports changes. The holder renders
/// asynchronously, so showing its state back would drop characters.
private struct DraftField: View {
    let placeholder: String
    let identifier: String
    let isDisabled: Bool
    let onChange: (String) -> Void
    @State private var text: String

    init(
        placeholder: String, initial: String, identifier: String, isDisabled: Bool,
        onChange: @escaping (String) -> Void
    ) {
        self.placeholder = placeholder
        self.identifier = identifier
        self.isDisabled = isDisabled
        self.onChange = onChange
        _text = State(initialValue: initial)
    }

    var body: some View {
        TextField(placeholder, text: $text, axis: .vertical)
            .font(Typography.body)
            .disabled(isDisabled)
            .padding(.horizontal, Spacing.extraLarge)
            .frame(minHeight: Spacing.minimumTarget)
            .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.button))
            .overlay(RoundedRectangle(cornerRadius: Radius.button).strokeBorder(Palette.border.color))
            .onChange(of: text) { _, new in onChange(new) }
            .accessibilityLabel(placeholder)
            .accessibilityIdentifier(identifier)
    }
}

/// One request from an agent (PRD FR-063). Which kind it is, whether it can be answered, why not, and whether the
/// answer is complete are the card's; the view draws the kind's controls and forwards each tap and edit.
struct InteractionCardView: View {
    let model: IssueScreenModel
    let card: InteractionUi
    let isBusy: Bool
    @Binding var web: WebLink?

    private var isOpen: Bool { card.canAnswer && !isBusy }
    private var isPending: Bool { card.status == .pending }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            Text(kindLine).font(Typography.label).foregroundStyle(Palette.accent.color)
                .accessibilityAddTraits(.isHeader).accessibilityIdentifier("interaction-kind-\(card.id)")
            if let title = card.title {
                Text(title).font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
            }
            if let prompt = card.prompt, prompt != card.title {
                Text(prompt).font(Typography.body).foregroundStyle(Palette.textPrimary.color)
            }
            if let details = card.detailsMarkdown, !details.isEmpty {
                Text(MarkdownText.attributed(details)).font(Typography.body)
                    .foregroundStyle(Palette.textSecondary.color)
            }
            controls
            footer
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.card)
                .strokeBorder((isPending ? Palette.accent : Palette.hairline).color, lineWidth: isPending ? 2 : 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("interaction-\(card.id)")
    }

    private var kindLine: String {
        var parts = [L10n.text(card.kind).uppercased()]
        if card.kind == .askUserQuestions { parts.append(IssueLabel.questionsMore.plural(card.questions.count)) }
        if !isPending { parts.append(L10n.text(card.status)) }
        return parts.joined(separator: " \u{00B7} ").uppercased()
    }

    // MARK: - Controls by kind

    @ViewBuilder private var controls: some View {
        if !card.tasks.isEmpty { choiceList(card.tasks, group: "") }
        if !card.options.isEmpty { choiceList(card.options, group: "") }
        ForEach(card.questions, id: \.id) { question in questionView(question) }
        ForEach(card.items, id: \.id) { item in verdictView(item) }
        if card.openInWebUrl != nil { openInWeb }
    }

    private func choiceList(_ choices: [ChoiceUi], group: String, radio: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: Spacing.small) {
            ForEach(choices, id: \.id) { choice in
                Button {
                    model.toggle(card.id, group: group, option: choice.id)
                } label: {
                    HStack(alignment: .top, spacing: Spacing.large) {
                        Image(systemName: symbol(selected: choice.selected, radio: radio))
                            .foregroundStyle((choice.selected ? Palette.accent : Palette.textSecondary).color)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: Spacing.hairline) {
                            Text(choice.label).font(Typography.body)
                                .foregroundStyle((isOpen ? Palette.textPrimary : Palette.textSecondary).color)
                                .multilineTextAlignment(.leading)
                            if let summary = choice.summary {
                                Text(summary).font(Typography.secondary)
                                    .foregroundStyle(Palette.textSecondary.color)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.cardSelect)
                .disabled(!isOpen)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(choice.label)
                .accessibilityAddTraits(choice.selected ? [.isButton, .isSelected] : .isButton)
                .accessibilityIdentifier("interaction-choice-\(group.isEmpty ? "" : group + "-")\(choice.id)")
            }
        }
    }

    private func symbol(selected: Bool, radio: Bool) -> String {
        switch (radio, selected) {
        case (true, true): "largecircle.fill.circle"
        case (true, false): "circle"
        case (false, true): "checkmark.square.fill"
        case (false, false): "square"
        }
    }

    private func questionView(_ question: QuestionUi) -> some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            Text(question.required ? question.prompt : "\(question.prompt) (\(IssueLabel.optionalQuestion.text))")
                .font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
                .accessibilityAddTraits(.isHeader)
            if let help = question.helpText {
                Text(help).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
            }
            if !question.options.isEmpty { choiceList(question.options, group: question.id, radio: !question.multi) }
            if question.allowOther || question.options.isEmpty {
                DraftField(
                    placeholder: question.options.isEmpty
                        ? IssueLabel.answerPlaceholder.text : IssueLabel.otherField.text,
                    initial: question.otherText, identifier: "interaction-text-\(question.id)",
                    isDisabled: !isOpen
                ) { model.text(card.id, target: question.id, $0) }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("interaction-question-\(question.id)")
    }

    private func verdictView(_ item: VerdictItemUi) -> some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            Text(item.label).font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
            if let summary = item.summary {
                Text(summary).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
            }
            HStack(spacing: Spacing.medium) {
                ForEach([VerdictUi.approve, .reject, .defer], id: \.self) { verdict in
                    let enabled = card.enabledVerdicts.contains(verdict)
                    Button(L10n.text(verdict)) { model.verdict(card.id, item: item.id, verdict) }
                        .buttonStyle(.app(item.verdict == verdict ? .primary : .secondary))
                        .disabled(!isOpen || !enabled)
                        .accessibilityAddTraits(item.verdict == verdict ? .isSelected : [])
                        .accessibilityIdentifier("interaction-verdict-\(item.id)-\(verdict)")
                }
            }
            DraftField(
                placeholder: IssueLabel.itemReason.text, initial: item.reason,
                identifier: "interaction-reason-\(item.id)", isDisabled: !isOpen
            ) { model.text(card.id, target: item.id, $0) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("interaction-item-\(item.id)")
    }

    private var openInWeb: some View {
        Button(IssueLabel.openInWeb.text) { web = webLink }
            .buttonStyle(.app(.secondary))
            .accessibilityIdentifier("interaction-open-web")
    }

    /// Only a web address is opened: a link the server sent is never handed to the system as anything else.
    private var webLink: WebLink? {
        WebAddress.url(card.openInWebUrl).map { WebLink(url: $0) }
    }

    // MARK: - Buttons and why not

    @ViewBuilder private var footer: some View {
        if isPending {
            if let reason = card.readOnlyReason {
                Text(L10n.text(reason)).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("interaction-read-only")
            } else {
                answerButtons
            }
        }
    }

    @ViewBuilder private var answerButtons: some View {
        if card.hasReject {
            DraftField(
                placeholder: card.rejectRequiresReason
                    ? IssueLabel.rejectReasonRequired.text : IssueLabel.rejectReason.text,
                initial: card.rejectReason, identifier: "interaction-reject-reason", isDisabled: !isOpen
            ) { model.text(card.id, target: "", $0) }
        }
        if let primary = card.primary {
            Button(L10n.text(primary)) { model.primary(card.id) }
                .buttonStyle(.app(.primary))
                .disabled(!isOpen || card.submitIssue != nil)
                .accessibilityIdentifier("interaction-primary")
            if let issue = card.submitIssue {
                Text(L10n.text(issue)).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("interaction-submit-issue")
            }
        }
        if card.hasReject {
            Button(L10n.text(VerdictUi.reject)) { model.reject(card.id) }
                .buttonStyle(.app(.destructive))
                .disabled(!isOpen || card.rejectIssue != nil)
                .accessibilityIdentifier("interaction-reject")
            if let issue = card.rejectIssue {
                Text(L10n.text(issue)).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("interaction-reject-issue")
            }
        }
    }
}
