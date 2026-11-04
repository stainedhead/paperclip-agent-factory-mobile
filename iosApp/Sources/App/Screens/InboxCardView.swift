import PaperclipKit
import SwiftUI

/// One Inbox card: the shared `AttentionCard` plus the buttons the state offers. Buttons are disabled while the phone
/// is offline or another action is running (`isDisabled`); opening a card only reads, so it stays available.
struct InboxCardView: View {
    let item: InboxItemUi
    let isDisabled: Bool
    let onOpen: () -> Void
    let onApprove: () -> Void
    let onReject: () -> Void
    let onKeepPaused: () -> Void

    var body: some View {
        TickingReference(every: LiveClock.ageTick) { now in
            card(InboxCardPresentation(item: item, referenceDate: now))
        }
    }

    private func card(_ card: InboxCardPresentation) -> some View {
        let actions = InboxCardActions(item: item)
        return AttentionCard(
            severity: card.severity, kind: card.kindLine, age: card.age, ageSpoken: card.ageSpoken, title: card.title,
            subtitle: card.context,
            identifier: "inbox-card-\(item.id)", combinedLabel: card.accessibilityLabel,
            showsChevron: actions.link == .chevron && actions.inline.isEmpty, onOpen: onOpen
        ) {
            buttons(actions)
        }
    }

    @ViewBuilder private func buttons(_ actions: InboxCardActions) -> some View {
        switch actions.link {
        case .reviewOptions:
            Button(InboxLabel.reviewOptions.text, action: onOpen)
                .buttonStyle(.app(.primary))
                .accessibilityIdentifier("inbox-card-\(item.id)-review")
        case .details:
            AdaptiveStack(spacing: Spacing.medium) {
                if actions.offers(.reject) {
                    Button(L10n.text(InlineActionUi.reject), action: onReject)
                        .buttonStyle(.app(.destructive))
                        .disabled(isDisabled)
                        .accessibilityIdentifier("inbox-card-\(item.id)-reject")
                }
                Button(InboxLabel.details.text, action: onOpen)
                    .buttonStyle(.app(.muted))
                    .accessibilityIdentifier("inbox-card-\(item.id)-details")
                if actions.offers(.approve) {
                    Button(L10n.text(InlineActionUi.approve), action: onApprove)
                        .buttonStyle(.app(.primary))
                        .disabled(isDisabled)
                        .accessibilityIdentifier("inbox-card-\(item.id)-approve")
                }
            }
        case .openInWeb:
            Button(InboxLabel.openInWeb.text, action: onOpen)
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("inbox-card-\(item.id)-web")
        case .chevron:
            if !actions.inline.isEmpty {
                AdaptiveStack(spacing: Spacing.medium) {
                    if actions.offers(.keepPaused) {
                        Button(L10n.text(InlineActionUi.keepPaused), action: onKeepPaused)
                            .buttonStyle(.app(.secondary))
                            .disabled(isDisabled)
                            .accessibilityIdentifier("inbox-card-\(item.id)-keep-paused")
                    }
                    if actions.offers(.raiseAndResume) {
                        Button(L10n.text(InlineActionUi.raiseAndResume), action: onOpen)
                            .buttonStyle(.app(.primary))
                            .disabled(isDisabled)
                            .accessibilityIdentifier("inbox-card-\(item.id)-raise")
                    }
                }
            }
        }
    }
}

/// The filter chips: "All 9", "Decisions 3", ... The selected chip is filled dark and announced as selected.
struct InboxFilterChips: View {
    let total: Int
    let filters: [FilterChipUi]
    let selected: SourceKindUi?
    let onSelect: (SourceKindUi?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.medium) {
                chip(InboxLabel.allCount.text(total), isSelected: selected == nil, id: "inbox-filter-all") {
                    onSelect(nil)
                }
                ForEach(filters, id: \.kind) { filter in
                    chip(
                        InboxLabel.chip.text(filter.kind.chipLabel, filter.count), isSelected: selected == filter.kind,
                        id: "inbox-filter-\(filter.kind)"
                    ) { onSelect(filter.kind) }
                }
            }
            .padding(.horizontal, Spacing.page)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(InboxLabel.filtersLabel.text)
        .accessibilityIdentifier("inbox-filters")
    }

    private func chip(_ text: String, isSelected: Bool, id: String, action: @escaping () -> Void) -> some View {
        FilterChip(text: text, isSelected: isSelected, identifier: id, action: action)
    }
}

/// The five-second offer to take back a dismiss or snooze. It is on screen exactly while the state carries `undo`.
struct UndoSnackbar: View {
    let onUndo: () -> Void

    var body: some View {
        HStack(spacing: Spacing.extraLarge) {
            Text(InboxLabel.undoMessage.text)
                .font(Typography.body)
                .foregroundStyle(Palette.background.color)
                .accessibilityIdentifier("inbox-undo-message")
            Spacer(minLength: Spacing.medium)
            Button(InboxLabel.undoAction.text, action: onUndo)
                .font(Typography.button)
                .foregroundStyle(Palette.accentSoft.color)
                .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
                .accessibilityIdentifier("inbox-undo-button")
        }
        .padding(.horizontal, Spacing.roomy)
        .background(Palette.textPrimary.color, in: RoundedRectangle(cornerRadius: Radius.button))
        .padding(.horizontal, Spacing.page)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("inbox-undo")
    }
}

/// The note a reject needs, and the Face ID that follows. The sheet is open while the state carries `rejectPrompt`;
/// Confirm sends the note, the shared use case asks the device owner and calls the server only if they approve.
struct RejectNoteSheet: View {
    let title: String
    let isWorking: Bool
    let issue: DeviceAuthIssue?
    let onConfirm: (String) -> Void
    let onCancel: () -> Void
    @State private var note = ""

    var body: some View {
        VStack(spacing: Spacing.roomy) {
            Image(systemName: "lock.fill")
                .font(.largeTitle)
                .foregroundStyle(Palette.destructive.color)
                .accessibilityHidden(true)
            Text(InboxLabel.rejectTitle.text).font(Typography.title).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(title).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
                .multilineTextAlignment(.center)
            FormField(
                title: InboxLabel.noteLabel.text, placeholder: InboxLabel.notePlaceholder.text, text: $note,
                help: InboxLabel.rejectMessage.text, capitalization: .sentences, identifier: "reject-note")
            Label(
                isWorking ? ShellLabel.confirmDestructiveWorking.text : ShellLabel.confirmDestructiveFaceID.text,
                systemImage: "faceid"
            )
            .font(Typography.secondary)
            .foregroundStyle(Palette.textSecondary.color)
            .accessibilityIdentifier("confirm-destructive-faceid")
            if let issue {
                Text(L10n.text(issue))
                    .font(Typography.body)
                    .foregroundStyle(Palette.destructive.color)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("confirm-destructive-issue")
            }
            Button {
                onConfirm(note)
            } label: {
                Label(InboxLabel.rejectConfirm.text, systemImage: "lock.fill")
            }
            .buttonStyle(.app(.destructive))
            .disabled(isWorking)
            .accessibilityIdentifier("confirm-destructive-confirm")
            Button(ShellLabel.commonCancel.text, action: onCancel)
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("confirm-destructive-cancel")
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("reject-sheet")
    }
}
