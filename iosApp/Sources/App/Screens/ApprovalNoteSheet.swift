import PaperclipKit
import SwiftUI

/// The note a reject or a revision request needs. The sheet is open while the state carries `notePrompt`; Confirm
/// sends the note and the shared use case does the rest: a reject asks the device owner (Face ID) before it calls the
/// server, a cancelled prompt comes back as `issue` and the sheet stays; a revision request asks nothing.
struct ApprovalNoteSheet: View {
    let kind: NotePromptKind
    let needsDeviceAuth: Bool
    let isWorking: Bool
    let issue: DeviceAuthIssue?
    let noteIssue: InvalidKey?
    let onConfirm: (String) -> Void
    let onCancel: () -> Void
    @State private var note = ""

    private var isReject: Bool { kind == .reject }

    var body: some View {
        VStack(spacing: Spacing.roomy) {
            if needsDeviceAuth {
                Image(systemName: "lock.fill")
                    .font(.largeTitle)
                    .foregroundStyle(Palette.destructive.color)
                    .accessibilityHidden(true)
            }
            Text((isReject ? ApprovalLabel.rejectTitle : ApprovalLabel.reviseTitle).text)
                .font(Typography.title).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            FormField(
                title: ApprovalLabel.noteLabel.text,
                placeholder: (isReject ? ApprovalLabel.rejectPlaceholder : ApprovalLabel.revisePlaceholder).text,
                text: $note, help: (isReject ? ApprovalLabel.rejectMessage : ApprovalLabel.reviseMessage).text,
                issue: noteIssue.map { L10n.text($0) }, capitalization: .sentences, identifier: "approval-note")
            if needsDeviceAuth {
                Label(
                    isWorking ? ShellLabel.confirmDestructiveWorking.text : ShellLabel.confirmDestructiveFaceID.text,
                    systemImage: "faceid"
                )
                .font(Typography.secondary)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityIdentifier("confirm-destructive-faceid")
            }
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
                if needsDeviceAuth {
                    Label(ApprovalLabel.rejectConfirm.text, systemImage: "lock.fill")
                } else {
                    Text(ApprovalLabel.reviseConfirm.text)
                }
            }
            .buttonStyle(.app(isReject ? .destructive : .primary))
            .disabled(isWorking)
            .accessibilityIdentifier("approval-note-confirm")
            Button(ShellLabel.commonCancel.text, action: onCancel)
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("approval-note-cancel")
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("approval-note-sheet")
    }
}
